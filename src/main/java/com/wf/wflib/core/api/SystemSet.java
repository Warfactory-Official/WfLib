package com.wf.wflib.core.api;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.ModLoadingIssue;

import java.util.ArrayDeque;
import java.util.Collections;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Predicate;

/**
 * One mod's systems and which of them are on. On = mandatory, configured on, or required (transitively) by an
 * on system or by {@link #require}. Only on systems get {@code register}/{@code registerClient}.
 */
public final class SystemSet {
    private static final String BY_REQUIRE =
            "{0}: system ''{1}'' is off in {2} but {3} needs it; it was turned on.";

    private final String modId;
    private final String configFile;
    private final Map<String, WFSystem> byId = new LinkedHashMap<>();
    private final Predicate<String> configured;
    private final Consumer<ModLoadingIssue> issues;
    private final Set<String> on = new LinkedHashSet<>();
    private final Set<String> clientStarted = new LinkedHashSet<>();
    private volatile Set<String> effective = Set.of();
    private IEventBus modBus;
    private ModContainer container;
    private IEventBus clientBus;
    private boolean started;
    private boolean closed;

    /**
     * @param configured whether the config file lists the system on
     * @param issues     receives one warning per system turned on against its config
     */
    public SystemSet(String modId, String configFile, List<? extends WFSystem> systems,
                     Predicate<String> configured, Consumer<ModLoadingIssue> issues) {
        this.modId = modId;
        this.configFile = configFile;
        this.configured = configured;
        this.issues = issues;
        for (WFSystem s : systems) {
            if (byId.put(s.id(), s) != null) {
                throw new IllegalArgumentException("Duplicate system id " + s.id());
            }
        }
    }

    public String modId() {
        return modId;
    }

    /** Ids as {@code <modid>:<sys>}. */
    public Set<String> qualifiedEffective() {
        Set<String> out = new java.util.TreeSet<>();
        for (String id : effective) {
            out.add(modId + ":" + id);
        }
        return Collections.unmodifiableSet(out);
    }

    public Set<String> ids() {
        return Collections.unmodifiableSet(byId.keySet());
    }

    public Set<String> effective() {
        return effective;
    }

    public boolean isOn(String id) {
        if (id.startsWith(modId + ":")) {
            id = id.substring(modId.length() + 1);
        }
        known(id);
        return effective.contains(id);
    }

    /** Resolves, then runs {@code register} of every on system in declaration order. */
    public synchronized void start(IEventBus modBus, ModContainer container) {
        if (started) {
            throw new IllegalStateException(modId + " systems already started");
        }
        started = true;
        this.modBus = modBus;
        this.container = container;
        Set<String> fresh = new LinkedHashSet<>();
        for (WFSystem s : byId.values()) {
            if (s.mandatory()) {
                fresh.add(s.id());
            } else if (configured.test(s.id())) {
                fresh.add(s.id());
            }
        }
        for (String id : List.copyOf(fresh)) {
            closure(id, fresh);
        }
        activate(fresh);
    }

    /** Runs {@code registerClient} of every on system, and of each system turned on later. */
    public synchronized void startClient(IEventBus clientBus) {
        started("startClient");
        this.clientBus = clientBus;
        for (String id : on) {
            if (clientStarted.add(id)) {
                byId.get(id).registerClient(clientBus);
            }
        }
    }

    /** Turns on, with their requirements, systems a dependent needs; legal until {@link #close}. */
    public synchronized void require(String requirer, String... ids) {
        started("require");
        if (closed) {
            throw new IllegalStateException(requirer + " requires " + List.of(ids) + " after the construction phase");
        }
        Set<String> fresh = new LinkedHashSet<>();
        for (String id : ids) {
            if (id.startsWith(modId + ":")) {
                id = id.substring(modId.length() + 1);
            }
            known(id);
            if (!on.contains(id) && fresh.add(id)) {
                issues.accept(ModLoadingIssue.warning(BY_REQUIRE, modId, id, configFile, requirer));
            }
            closure(id, fresh);
        }
        activate(fresh);
    }

    /** Ends the construction phase; {@link #require} throws afterwards. */
    public synchronized void close() {
        closed = true;
    }

    private void closure(String root, Set<String> fresh) {
        Deque<String> work = new ArrayDeque<>();
        work.add(root);
        while (!work.isEmpty()) {
            String from = work.poll();
            for (String need : byId.get(from).requires()) {
                if (foreign(need, from)) {
                    continue;
                }
                known(need);
                if (!on.contains(need) && fresh.add(need)) {
                    issues.accept(ModLoadingIssue.warning(BY_REQUIRE, modId, need, configFile, from));
                    work.add(need);
                }
            }
        }
    }

    private void activate(Set<String> fresh) {
        Set<String> all = new LinkedHashSet<>(on);
        all.addAll(fresh);
        Set<String> ordered = new LinkedHashSet<>();
        for (String id : byId.keySet()) {
            if (all.contains(id)) {
                ordered.add(id);
            }
        }
        on.clear();
        on.addAll(ordered);
        effective = Collections.unmodifiableSet(new LinkedHashSet<>(ordered));
        for (String id : byId.keySet()) {
            if (fresh.contains(id)) {
                byId.get(id).register(modBus, container);
                if (clientBus != null && clientStarted.add(id)) {
                    byId.get(id).registerClient(clientBus);
                }
            }
        }
    }

    /** A requirement naming another mod's system, or a wflib shorthand this mod has no system for: enabled there. */
    private boolean foreign(String need, String from) {
        String mod;
        String id;
        int colon = need.indexOf(':');
        if (colon >= 0) {
            mod = need.substring(0, colon);
            id = need.substring(colon + 1);
        } else if (!byId.containsKey(need)) {
            mod = "wflib";
            id = need;
        } else {
            return false;
        }
        if (mod.equals(modId)) {
            known(id);
            return false;
        }
        WFSystems.requireIn(mod, modId + ":" + from, id);
        return true;
    }

    private void known(String id) {
        if (!byId.containsKey(id)) {
            throw new IllegalArgumentException(modId + " has no system '" + id + "'; known: " + byId.keySet());
        }
    }

    private void started(String what) {
        if (!started) {
            throw new IllegalStateException(modId + " systems not started: " + what);
        }
    }
}
