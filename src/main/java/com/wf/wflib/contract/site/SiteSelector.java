package com.wf.wflib.contract.site;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.phys.Vec3;

import java.util.Optional;

/**
 * Chooses where a base goes: candidates on a ring round the player, each cheaply screened and then surveyed ({@link SiteSurvey})
 * for the whole footprint, the best kept. A bigger base asks more of the ground, so when nothing fits the next smaller archetype
 * is tried. Server thread; generates chunks while it looks, so it takes a moment far from anywhere explored.
 */
public final class SiteSelector {
    private SiteSelector() {
    }

    /** @param padY the level the pad will be graded to */
    public record Found(BaseKind kind, int x, int z, int padY, double score) {
    }

    public static Optional<Found> find(ServerLevel level, Vec3 origin, int minDistance, int maxDistance, BaseKind wanted, int playerClearance, RandomSource rng) {
        Terrain terrain = new LevelTerrain(level, true);
        for (BaseKind kind = wanted; kind != null; kind = smaller(kind)) {
            Found best = null;
            int tries = kind == BaseKind.FORTRESS ? 12 : 16, surveyed = 0, maxSurveys = kind == BaseKind.FORTRESS ? 4 : 6;
            for (int i = 0; i < tries && surveyed < maxSurveys; i++) {
                double angle = rng.nextDouble() * Math.PI * 2;
                double dist = minDistance + rng.nextDouble() * (maxDistance - minDistance);
                int x = (int) Math.floor(origin.x + Math.cos(angle) * dist), z = (int) Math.floor(origin.z + Math.sin(angle) * dist);
                if (!screen(terrain, x, z, kind.radius) || nearPlayer(level, x, z, kind.radius + playerClearance)) continue;
                surveyed++;
                var r = SiteSurvey.assess(terrain, x, z, kind.radius, kind.maxRange());
                if (!r.ok()) continue;
                if (best == null || r.score() > best.score()) best = new Found(kind, x, z, r.padY(), r.score());
                if (r.score() >= 95) break;
            }
            if (best != null) return Optional.of(best);
        }
        return Optional.empty();
    }

    private static BaseKind smaller(BaseKind k) {
        return k == BaseKind.FORTRESS ? BaseKind.COMPOUND : k == BaseKind.COMPOUND ? BaseKind.OUTPOST : null;
    }

    /** The centre and four points on the footprint's edge: dry, solid, not a coast. Five chunks, not a whole footprint's worth. */
    private static boolean screen(Terrain t, int x, int z, int radius) {
        int[][] probe = {{0, 0}, {radius, 0}, {-radius, 0}, {0, radius}, {0, -radius}};
        for (int[] p : probe) {
            int px = x + p[0], pz = z + p[1];
            if (!t.available(px, pz) || t.wet(px, pz) || t.surface(px, pz) != Terrain.Surface.SOLID) return false;
        }
        return true;
    }

    private static boolean nearPlayer(ServerLevel level, int x, int z, int distance) {
        for (ServerPlayer p : level.players()) {
            if (p instanceof com.wf.wflib.soldier.api.SoldierPlayer) continue;
            double dx = p.getX() - x, dz = p.getZ() - z;
            if (dx * dx + dz * dz < (double) distance * distance) return true;
        }
        return false;
    }
}
