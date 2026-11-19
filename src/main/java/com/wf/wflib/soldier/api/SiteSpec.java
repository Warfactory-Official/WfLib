package com.wf.wflib.soldier.api;

import java.util.List;

/**
 * What stands at an operation site: squads of soldiers raised from saved variants (the soldier editor's
 * loadout presets, by id) inside a disc of {@code radius} blocks.
 */
public record SiteSpec(int radius, List<Squad> squads) {
    public SiteSpec {
        if (squads.isEmpty()) throw new IllegalArgumentException("A site needs at least one squad");
        squads = List.copyOf(squads);
    }

    /**
     * @param units variant ids drawn in turn (empty: every saved variant)
     * @param count soldiers in the squad
     * @param mode guard (hold the ground) or patrol it
     */
    public record Squad(List<String> units, int count, SoldierOrder.Kind mode) {
        public Squad {
            units = List.copyOf(units);
            if (count < 1) throw new IllegalArgumentException("A squad has at least one soldier");
        }
    }
}
