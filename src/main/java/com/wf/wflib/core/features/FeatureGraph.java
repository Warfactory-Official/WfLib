package com.wf.wflib.core.features;

import com.wf.wflib.core.api.WFSystem;
import com.wf.wflib.core.features.FeatureCatalogue.Feature;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Which features need which: a system feature needs the features of the systems it {@code requires}, a flag needs the feature of
 * its system. The screen uses it so that ticking a box ticks what it needs and unticking one unticks what needs it, rather than
 * leaving the config to be corrected with a warning at launch.
 */
public final class FeatureGraph {
    private final Map<String, Set<String>> direct = new HashMap<>();

    public static FeatureGraph of(List<? extends WFSystem> systems) {
        Map<String, Set<String>> requires = new HashMap<>();
        for (WFSystem s : systems) requires.put(s.id(), s.requires());
        return new FeatureGraph(requires);
    }

    /** @param requires each wflib system id to the ids it requires */
    public FeatureGraph(Map<String, Set<String>> requires) {
        for (Feature f : FeatureCatalogue.all()) {
            Set<String> needs = new LinkedHashSet<>();
            if (f.system() != null && requires.containsKey(f.system())) {
                for (String r : requires.get(f.system())) {
                    Feature other = FeatureCatalogue.ofSystem(r.startsWith("wflib:") ? r.substring(6) : r);
                    if (other != null) needs.add(other.id());
                }
            }
            if (f.needsSystem() != null) {
                Feature other = FeatureCatalogue.ofSystem(f.needsSystem());
                if (other != null) needs.add(other.id());
            }
            direct.put(f.id(), needs);
        }
    }

    /** What {@code id} names itself as needing, one step. */
    public Set<String> direct(String id) {
        return Set.copyOf(direct.getOrDefault(id, Set.of()));
    }

    /** Everything {@code id} needs, through any number of steps, not including itself. */
    public Set<String> needs(String id) {
        return walk(id, true);
    }

    /** Everything that needs {@code id}, through any number of steps, not including itself. */
    public Set<String> dependents(String id) {
        return walk(id, false);
    }

    private Set<String> walk(String root, boolean forward) {
        Set<String> out = new LinkedHashSet<>();
        Deque<String> work = new ArrayDeque<>();
        work.add(root);
        while (!work.isEmpty()) {
            String at = work.poll();
            for (Map.Entry<String, Set<String>> e : direct.entrySet()) {
                if (forward ? at.equals(e.getKey()) : e.getValue().contains(at)) {
                    for (String next : forward ? e.getValue() : Set.of(e.getKey())) {
                        if (!next.equals(root) && out.add(next)) work.add(next);
                    }
                }
            }
        }
        return out;
    }
}
