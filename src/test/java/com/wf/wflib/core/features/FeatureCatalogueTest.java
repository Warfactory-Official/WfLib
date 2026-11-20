package com.wf.wflib.core.features;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.wf.wflib.core.api.Features;
import com.wf.wflib.core.features.FeatureCatalogue.Feature;
import com.wf.wflib.core.features.FeatureCatalogue.Group;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class FeatureCatalogueTest {
    /** The systems, read from the sources (the classes cannot be loaded without a game): id, mandatory, requires. */
    private record Sys(String id, boolean mandatory, Set<String> requires) {
    }

    private static List<Sys> systems() throws IOException {
        List<Sys> out = new ArrayList<>();
        Pattern id = Pattern.compile("public String id\\(\\)\\s*\\{\\s*return \"(\\w+)\";");
        Pattern mandatory = Pattern.compile("mandatory\\(\\)\\s*\\{\\s*return true;");
        Pattern requires = Pattern.compile("requires\\(\\)\\s*\\{\\s*return Set\\.of\\(([^)]*)\\);");
        try (var files = Files.walk(Path.of("src/main/java/com/wf/wflib"))) {
            for (Path f : (Iterable<Path>) files.filter(p -> p.toString().endsWith("System.java"))::iterator) {
                String text = Files.readString(f);
                if (!text.contains("implements WFSystem")) continue;
                Matcher m = id.matcher(text);
                if (!m.find()) continue;
                Set<String> req = new HashSet<>();
                Matcher r = requires.matcher(text);
                if (r.find()) for (String q : r.group(1).split(",")) if (!q.isBlank()) req.add(q.trim().replace("\"", ""));
                out.add(new Sys(m.group(1), mandatory.matcher(text).find(), req));
            }
        }
        return out;
    }

    @AfterEach
    void reset() {
        Features.install(Map.of());
    }

    @Test
    void idsAreUnique() {
        Set<String> seen = new HashSet<>();
        for (Group g : FeatureCatalogue.GROUPS) {
            assertTrue(seen.add(g.id()), "group " + g.id());
            assertFalse(g.features().isEmpty(), g.id() + " is empty");
        }
        for (Feature f : FeatureCatalogue.all()) assertTrue(seen.add(f.id()), "feature " + f.id());
    }

    @Test
    void everySwitchableSystemHasOneFeatureAndNoFeatureNamesAMissingOne() throws IOException {
        Set<String> switchable = new HashSet<>();
        Set<String> all = new HashSet<>();
        for (Sys s : systems()) {
            all.add(s.id());
            if (!s.mandatory()) switchable.add(s.id());
        }
        assertTrue(all.size() > 50, "found the systems: " + all.size());
        Set<String> named = new HashSet<>();
        for (Feature f : FeatureCatalogue.all()) {
            if (f.system() != null) {
                assertTrue(switchable.contains(f.system()), f.id() + " names '" + f.system() + "', which is not a switchable system");
                assertTrue(named.add(f.system()), f.system() + " has two features");
                assertEquals(f.system(), f.id(), "a system feature's id is its system id");
            }
            if (f.needsSystem() != null) assertTrue(all.contains(f.needsSystem()), f.id() + " needs unknown system " + f.needsSystem());
            for (String app : f.apps()) assertNotNull(ResourceLocation.tryParse(app), app);
        }
        assertEquals(switchable, named, "switchable systems with no feature: " + diff(switchable, named));
    }

    private static Set<String> diff(Set<String> a, Set<String> b) {
        Set<String> d = new HashSet<>(a);
        d.removeAll(b);
        return d;
    }

    @Test
    void anAppBelongsToOneFeature() {
        Set<String> apps = new HashSet<>();
        for (Feature f : FeatureCatalogue.all()) for (String a : f.apps()) assertTrue(apps.add(a), a + " is claimed twice");
    }

    @Test
    void theGraphFollowsSystemRequirementsAndFlags() throws IOException {
        Map<String, Set<String>> requires = new HashMap<>();
        for (Sys s : systems()) requires.put(s.id(), s.requires());
        FeatureGraph g = new FeatureGraph(requires);
        assertTrue(g.needs("contract").contains("soldier"));
        assertTrue(g.needs("contract_heat").contains("contract"));
        assertTrue(g.needs("contract_heat").contains("soldier"), "through contract");
        assertTrue(g.dependents("soldier").containsAll(Set.of("contract", "contract_heat", "contract_blacksite")));
        assertTrue(g.needs("radar_app").isEmpty());
        assertFalse(g.needs("contract").contains("contract"));
    }

    @Test
    void aGroupSwitchTurnsEverythingInItOffAndHidesItsApps() {
        Map<String, Boolean> v = new HashMap<>();
        v.put("military.enabled", false);
        Features.install(v);
        assertFalse(Features.systemConfigured("soldier", true));
        assertFalse(Features.systemConfigured("contract", true));
        assertFalse(Features.appShown(ResourceLocation.parse("wflib:contracts")));
        assertTrue(Features.systemConfigured("magazine", true));
        assertTrue(Features.appShown(ResourceLocation.parse("wflib:radar")));
    }

    @Test
    void aSingleBoxHidesOnlyItsApp() {
        Map<String, Boolean> v = new HashMap<>();
        v.put("radar_app", false);
        Features.install(v);
        assertFalse(Features.appShown(ResourceLocation.parse("wflib:radar")));
        assertTrue(Features.appShown(ResourceLocation.parse("wflib:telemetry")));
    }

    @Test
    void defaultsAreTheCataloguesAndUnsetIsOn() {
        Features.install(Map.of("military.enabled", true));
        assertFalse(Features.systemConfigured("anomaly", true), "anomalies default off");
        assertTrue(Features.systemConfigured("soldier", false));
        assertTrue(Features.systemConfigured("ammo", true), "a system no feature names is not gated");
    }
}
