package com.wf.wflib.core.api;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.ModLoadingIssue;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class SystemSetTest {
    private static final class Sys implements WFSystem {
        private final String id;
        private final boolean mandatory;
        private final Set<String> requires;
        final List<String> log;

        Sys(List<String> log, String id, boolean mandatory, String... requires) {
            this.log = log;
            this.id = id;
            this.mandatory = mandatory;
            this.requires = Set.of(requires);
        }

        @Override
        public String id() {
            return id;
        }

        @Override
        public boolean mandatory() {
            return mandatory;
        }

        @Override
        public Set<String> requires() {
            return requires;
        }

        @Override
        public void register(IEventBus modBus, ModContainer container) {
            log.add(id);
        }
    }

    private final List<String> registered = new ArrayList<>();
    private final List<ModLoadingIssue> issues = new ArrayList<>();

    private SystemSet set(Set<String> configuredOn) {
        return new SystemSet("t", "t-systems.toml", List.of(
                new Sys(registered, "base", true),
                new Sys(registered, "a", false, "b"),
                new Sys(registered, "b", false, "c"),
                new Sys(registered, "c", false),
                new Sys(registered, "leaf", false)), configuredOn::contains, issues::add);
    }

    @Test
    void transitiveAutoEnableWarnsOncePerSystem() {
        SystemSet s = set(Set.of("base", "a"));
        s.start(null, null);
        assertEquals(Set.of("base", "a", "b", "c"), s.effective());
        assertEquals(List.of("base", "a", "b", "c"), registered);
        assertEquals(2, issues.size());
        assertTrue(issues.stream().anyMatch(i -> i.translationArgs().contains("b") && i.translationArgs().contains("a")));
        assertTrue(issues.stream().anyMatch(i -> i.translationArgs().contains("c") && i.translationArgs().contains("b")));
        assertFalse(s.isOn("leaf"));
    }

    @Test
    void mandatoryStaysOnWithoutWarning() {
        SystemSet s = set(Set.of());
        s.start(null, null);
        assertEquals(Set.of("base"), s.effective());
        assertEquals(0, issues.size());
    }

    @Test
    void noWarningWhenConfiguredOnOrMandatoryOn() {
        SystemSet s = set(Set.of("base", "a", "b", "c", "leaf"));
        s.start(null, null);
        assertEquals(0, issues.size());
        assertEquals(5, s.effective().size());
    }

    @Test
    void requireEnablesLateWithWarningAndRegisters() {
        SystemSet s = set(Set.of("base"));
        s.start(null, null);
        s.require("dep", "a");
        assertEquals(List.of("base", "a", "b", "c").stream().sorted().toList(), registered.stream().sorted().toList());
        assertEquals(3, issues.size());
        assertEquals(Set.of("base", "a", "b", "c"), s.effective());
        s.require("dep2", "a");
        assertEquals(3, issues.size());
    }

    @Test
    void requireAfterCloseThrows() {
        SystemSet s = set(Set.of("base"));
        s.start(null, null);
        s.close();
        assertThrows(IllegalStateException.class, () -> s.require("dep", "leaf"));
        assertFalse(s.isOn("leaf"));
    }

    @Test
    void unknownIdThrows() {
        SystemSet s = set(Set.of("base"));
        s.start(null, null);
        assertThrows(IllegalArgumentException.class, () -> s.require("dep", "nope"));
        assertThrows(IllegalArgumentException.class, () -> s.isOn("nope"));
    }

    @Test
    void unknownRequirementThrowsAtStart() {
        try {
            defineWflib(Set.of("base"));
            SystemSet s = new SystemSet("t", "f", List.of(new Sys(registered, "x", false, "ghost")), id -> true, issues::add);
            assertThrows(IllegalArgumentException.class, () -> s.start(null, null));
        } finally {
            WFSystems.reset();
        }
    }

    private SystemSet defineWflib(Set<String> on) {
        return WFSystems.define("wflib", null, null, List.of(
                new Sys(registered, "base", true),
                new Sys(registered, "a", false, "b"),
                new Sys(registered, "b", false),
                new Sys(registered, "leaf", false)), on::contains, issues::add);
    }

    @Test
    void ownedRegistryThrowsWhileOffAndNamesSystem() {
        try {
            defineWflib(Set.of("base"));
            WFRegistry<String> off = WFRegistry.create("leaf", "t");
            IllegalStateException e = assertThrows(IllegalStateException.class,
                    () -> off.register(net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("t", "x"), "v"));
            assertTrue(e.getMessage().contains("leaf"));
            OrderedRegistry<String> on = OrderedRegistry.create("base", "t");
            on.register(net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("t", "x"), 0, "v");
        } finally {
            WFSystems.reset();
        }
    }

    @Test
    void qualifiedIdsAndBareShorthandForWflib() {
        try {
            defineWflib(Set.of("base", "a"));
            assertTrue(WFSystems.enabled("wflib:a"));
            assertTrue(WFSystems.enabled("b"));
            assertFalse(WFSystems.enabled("wflib:leaf"));
            assertEquals(Set.of("wflib:base", "wflib:a", "wflib:b"), WFSystems.effective());
            assertEquals(Set.of("wflib:base", "wflib:a", "wflib:b", "wflib:leaf"), WFSystems.ids());
            assertThrows(IllegalArgumentException.class, () -> WFSystems.enabled("wflib:nope"));
            assertThrows(IllegalStateException.class, () -> WFSystems.enabled("other:x"));
        } finally {
            WFSystems.reset();
        }
    }

    @Test
    void dependentSystemsRequireWflibSystemsAcrossMods() {
        try {
            defineWflib(Set.of("base"));
            List<String> depLog = new ArrayList<>();
            SystemSet dep = WFSystems.define("dep", null, null, List.of(
                    new Sys(depLog, "gun", false, "wflib:a", "aux"),
                    new Sys(depLog, "aux", false),
                    new Sys(depLog, "idle", false)), Set.of("gun")::contains, issues::add);
            assertEquals(List.of("gun", "aux"), depLog);
            assertEquals(Set.of("wflib:base", "wflib:a", "wflib:b", "dep:gun", "dep:aux"), WFSystems.effective());
            assertTrue(WFSystems.enabled("dep:gun"));
            assertFalse(WFSystems.enabled("dep:idle"));
            assertEquals(Set.of("dep:aux", "dep:gun"), dep.qualifiedEffective());
            assertTrue(registered.contains("a") && registered.contains("b"));
            assertTrue(issues.stream().anyMatch(i -> i.translationArgs().contains("a")
                    && i.translationArgs().contains("dep:gun") && i.translationArgs().contains("wflib")));
            assertTrue(issues.stream().anyMatch(i -> i.translationArgs().contains("aux")));
        } finally {
            WFSystems.reset();
        }
    }

    @Test
    void crossModRequireAfterCloseThrows() {
        try {
            defineWflib(Set.of("base"));
            WFSystems.close();
            assertThrows(IllegalStateException.class, () -> WFSystems.define("dep", null, null,
                    List.of(new Sys(new ArrayList<>(), "gun", false, "wflib:leaf")), id -> true, issues::add));
        } finally {
            WFSystems.reset();
        }
    }

    @Test
    void definingTwiceOrBeforeWflibFails() {
        try {
            assertThrows(IllegalStateException.class, () -> WFSystems.define("dep", null, null,
                    List.of(new Sys(new ArrayList<>(), "gun", false, "wflib:a")), id -> true, issues::add));
            WFSystems.reset();
            defineWflib(Set.of("base"));
            assertThrows(IllegalStateException.class, () -> defineWflib(Set.of("base")));
        } finally {
            WFSystems.reset();
        }
    }
}
