package com.wf.wflib.core.api;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.ModLoader;
import net.neoforged.fml.ModLoadingIssue;
import net.neoforged.fml.i18n.FMLTranslations;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.Consumer;
import java.util.function.Predicate;

/**
 * Every mod's {@link SystemSet}, keyed by mod id. Ids are {@code <modid>:<sys>}; a bare id here means wflib's.
 * A mod calls {@link #define} in its constructor, after wflib's.
 */
public final class WFSystems {
    public static final String WFLIB = "wflib";
    private static final Logger LOGGER = LoggerFactory.getLogger(WFSystems.class);
    private static final Map<String, SystemSet> SETS = new LinkedHashMap<>();

    private WFSystems() {}

    /**
     * Creates {@code <modid>-systems.toml}, resolves {@code modId}'s systems and runs {@code register} of the on ones.
     * Requirements naming other mods' systems turn those on, with a loading warning.
     */
    public static SystemSet define(String modId, IEventBus modBus, ModContainer container,
                                   List<? extends WFSystem> systems) {
        return define(modId, modBus, container, systems, SystemsConfig.register(modId, container, systems),
                issue -> {
                    ModLoader.addLoadingIssue(issue);
                    LOGGER.warn(FMLTranslations.translateIssueEnglish(issue));
                });
    }

    static SystemSet define(String modId, IEventBus modBus, ModContainer container, List<? extends WFSystem> systems,
                            Predicate<String> configured, Consumer<ModLoadingIssue> issues) {
        SystemSet set = new SystemSet(modId, SystemsConfig.file(modId), systems, configured, issues);
        synchronized (SETS) {
            if (SETS.putIfAbsent(modId, set) != null) {
                throw new IllegalStateException("Systems of " + modId + " already defined");
            }
        }
        try {
            set.start(modBus, container);
        } catch (RuntimeException e) {
            synchronized (SETS) {
                SETS.remove(modId);
            }
            throw e;
        }
        return set;
    }

    /** Runs {@code registerClient} of {@code modId}'s on systems. */
    public static void startClient(String modId, IEventBus clientBus) {
        of(modId).startClient(clientBus);
    }

    public static SystemSet of(String modId) {
        synchronized (SETS) {
            SystemSet set = SETS.get(modId);
            if (set == null) {
                throw new IllegalStateException("Systems of " + modId + " not defined (wflib's are defined first)");
            }
            return set;
        }
    }

    /** Turns on systems {@code modId} needs, with their requirements; throws once construction is over. */
    public static void require(String modId, String... ids) {
        for (String id : ids) {
            Qualified q = Qualified.of(id);
            requireIn(q.mod, modId, q.id);
        }
    }

    static void requireIn(String mod, String requirer, String id) {
        of(mod).require(requirer, id);
    }

    /** For guarded optional use; throws for an unknown id. */
    public static boolean enabled(String id) {
        Qualified q = Qualified.of(id);
        return of(q.mod).isOn(q.id);
    }

    /** Every defined system, {@code <modid>:<sys>}. */
    public static Set<String> ids() {
        Set<String> out = new TreeSet<>();
        for (SystemSet set : snapshot()) {
            for (String id : set.ids()) {
                out.add(set.modId() + ":" + id);
            }
        }
        return Collections.unmodifiableSet(out);
    }

    /** Every on system, {@code <modid>:<sys>}. */
    public static Set<String> effective() {
        Set<String> out = new TreeSet<>();
        for (SystemSet set : snapshot()) {
            out.addAll(set.qualifiedEffective());
        }
        return Collections.unmodifiableSet(out);
    }

    /** Ends every mod's construction phase; called by wflib at the first {@code RegisterEvent}. */
    public static void close() {
        for (SystemSet set : snapshot()) {
            set.close();
        }
    }

    static void reset() {
        synchronized (SETS) {
            SETS.clear();
        }
    }

    /** Registry gate: unowned while wflib's set is undefined; an off owner throws naming the system. */
    static void checkOn(String system, String what) {
        SystemSet set;
        synchronized (SETS) {
            set = SETS.get(WFLIB);
        }
        if (set != null && !set.isOn(system)) {
            throw new IllegalStateException(what + " belongs to system '" + system
                    + "', which is off; WFSystems.require(modId, \"" + system + "\") in the mod constructor");
        }
    }

    private static List<SystemSet> snapshot() {
        synchronized (SETS) {
            return List.copyOf(SETS.values());
        }
    }

    private record Qualified(String mod, String id) {
        static Qualified of(String id) {
            int colon = id.indexOf(':');
            return colon < 0 ? new Qualified(WFLIB, id) : new Qualified(id.substring(0, colon), id.substring(colon + 1));
        }
    }
}
