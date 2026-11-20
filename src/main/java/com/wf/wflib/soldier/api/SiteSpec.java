package com.wf.wflib.soldier.api;

import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

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
     * @param at where the squad holds, when it is not the site's centre (a gate, a tower, an outpost)
     * @param radius how far from {@code at} it ranges; 0 = the site's radius
     * @param route points it walks in turn when patrolling (heights are the walking level); empty = wander its radius
     */
    public record Squad(List<String> units, int count, SoldierOrder.Kind mode, @Nullable Vec3 at, int radius, List<Vec3> route) {
        public Squad {
            units = List.copyOf(units);
            route = List.copyOf(route);
            if (count < 1) throw new IllegalArgumentException("A squad has at least one soldier");
            if (radius < 0) throw new IllegalArgumentException("Negative radius");
        }

        /** A squad at the site's centre ranging over its whole radius. */
        public Squad(List<String> units, int count, SoldierOrder.Kind mode) {
            this(units, count, mode, null, 0, List.of());
        }
    }
}
