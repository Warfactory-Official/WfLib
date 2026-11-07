package com.wf.wflib.soldier;

import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SnapshotTerrainTest {
    /** A flat world: floor at y = -1 (a full block), air above, with blocks set by the test. */
    private final Map<Long, Integer> blocks = new HashMap<>();

    private void set(int x, int y, int z, int cell) {
        blocks.put(BlockPos.asLong(x, y, z), cell);
    }

    private SnapshotTerrain world() {
        return new SnapshotTerrain((x, y, z) -> {
            Integer set = blocks.get(BlockPos.asLong(x, y, z));
            if (set != null) return set;
            return y <= -1 ? 16 : 0;
        });
    }

    @Test
    void standingNeedsAFloorAndHeadRoom() {
        var t = world();
        assertTrue(t.walkable(new BlockPos(0, 0, 0)));
        assertFalse(t.walkable(new BlockPos(0, 3, 0)), "nothing to stand on");
        set(2, 1, 0, 16);
        assertFalse(t.walkable(new BlockPos(2, 0, 0)), "a block at head height");
        set(3, 0, 0, 16);
        assertFalse(t.walkable(new BlockPos(3, 0, 0)), "inside a wall");
    }

    @Test
    void lowBlocksAreSteppedOntoAndTallOnesAreNot() {
        var t = world();
        set(1, 0, 0, 8);                                  // a bottom slab (half a block)
        assertTrue(t.walkable(new BlockPos(1, 0, 0)), "feet in the slab's cell");
        set(2, 0, 0, 12);                                 // three quarters of a block
        assertFalse(t.walkable(new BlockPos(2, 0, 0)));
        set(4, -1, 0, 8);                                 // floor made of a slab
        assertTrue(t.walkable(new BlockPos(4, 0, 0)));
        set(5, -1, 0, 2);                                 // a carpet is no floor
        assertFalse(t.walkable(new BlockPos(5, 0, 0)));
    }

    @Test
    void sightIsBlockedByWallsButNotByPanesOrOverLowCover() {
        var t = world();
        Vec3 from = new Vec3(0.5, 1.6, 0.5), to = new Vec3(8.5, 1.6, 0.5);
        assertTrue(t.sees(from, to));
        set(4, 0, 0, 16);
        set(4, 1, 0, 16);
        assertFalse(t.sees(from, to), "a two-high wall in between");
        set(4, 0, 0, 16 | SoldierSections.THIN);
        set(4, 1, 0, 16 | SoldierSections.THIN);
        assertTrue(t.sees(from, to), "panes block walking, not sight");
        set(4, 0, 0, 16);
        set(4, 1, 0, 0);
        assertTrue(t.sees(from, to), "over a one-high wall at eye height");
        assertFalse(t.sees(new Vec3(0.5, 0.5, 0.5), new Vec3(8.5, 0.5, 0.5)), "but not at the height of the wall");
    }

    @Test
    void unseenSectionsReadAsWalls() {
        var t = new SnapshotTerrain((x, y, z) -> x > 5 ? -1 : y <= -1 ? 16 : 0);
        assertFalse(t.walkable(new BlockPos(6, 0, 0)));
        assertTrue(t.incomplete);
        assertTrue(t.blocked(new Vec3(0.5, 1.6, 0.5), new Vec3(9.5, 1.6, 0.5)));
    }

    @Test
    void theRouteSearchWalksRoundAWallOnACopy() {
        for (int z = -3; z <= 3; z++) {                  // a wall across x = 3 with a gap at z = 3
            if (z != 3) for (int y = 0; y <= 2; y++) set(3, y, z, 16);
        }
        List<Vec3> route = BotTactics.routeAvoiding(world(), new BlockPos(0, 0, 0), new BlockPos(6, 0, 0), List.of(), List.of());
        assertFalse(route.isEmpty());
        assertTrue(route.stream().anyMatch(v -> v.z > 2.5), "goes through the gap");
        assertEquals(6.5, route.get(route.size() - 1).x, 1e-9);
    }
}
