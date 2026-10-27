package com.wf.wflib.radiation;

import com.wf.wflib.util.ForcedChunks;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

import java.util.Arrays;
import java.util.function.Predicate;

/**
 * Gametest lane far off the arena grid: a radiation section is 16 blocks wide and diffuses into its
 * neighbours, so an arena-relative seed charges the next arena. World + sidecar persist across runs =>
 * the field around the lane is zeroed before the body and on release.
 */
final class RadiationLane {

    private static final int ORIGIN_Z = 12288;
    private static final int SPACING = 256;
    /** Forced 3x3 + the FULL-loaded border ring. */
    private static final int CLEAR_RADIUS = 2;

    final GameTestHelper helper;
    final ServerLevel level;
    final BlockPos origin;
    private final int cx;
    private final int cz;
    private boolean held = true;

    RadiationLane(GameTestHelper helper, int index) {
        this.helper = helper;
        this.level = helper.getLevel();
        this.origin = new BlockPos(0, level.getMinBuildHeight() + 4, ORIGIN_Z + index * SPACING);
        this.cx = origin.getX() >> 4;
        this.cz = origin.getZ() >> 4;
        force(true);
    }

    BlockPos pos(BlockPos rel) {
        return origin.offset(rel);
    }

    BlockPos pos(int x, int y, int z) {
        return origin.offset(x, y, z);
    }

    /** Forced chunks load async; a body run before they tick reads an unloaded chunk as 0. */
    void run(Runnable body) {
        helper.startSequence()
                .thenWaitUntil(() -> helper.assertTrue(level.isPositionEntityTicking(origin), "lane loading"))
                .thenExecute(() -> {
                    clearField();
                    body.run();
                });
    }

    void clearField() {
        RadiationSystemNT.awaitSimulation();
        WorldRadiationData data = RadiationSystemNT.getWorldRadData(level);
        int n = data.sectionsPerChunk;
        for (int dx = -CLEAR_RADIUS; dx <= CLEAR_RADIUS; dx++) {
            for (int dz = -CLEAR_RADIUS; dz <= CLEAR_RADIUS; dz++) {
                int id = data.getId(ChunkPos.asLong(cx + dx, cz + dz));
                if (id < 0) continue;
                int base = id * n;
                Arrays.fill(data.uniformRads, base, base + n, 0.0D);
                for (int slot = 0; slot < n; slot++) {
                    if (data.complexSecs[base + slot] instanceof WorldRadiationData.MultiSectionRef m) {
                        for (int p = 0, count = m.pocketCount & 0xFFFF; p < count; p++) m.data[p << 1] = 0.0D;
                    }
                }
                if (data.pending[id] != null) data.pending[id].clearAll();
                data.setChunkDirty(id);
            }
        }
    }

    void release() {
        if (!held) return;
        held = false;
        clearField();
        force(false);
    }

    /** No public inverse of {@link RadiationSystemNT#markRadResistant}; sweep joined first, it reads the set. */
    static void unmarkRadResistant(Block block) {
        RadiationSystemNT.awaitSimulation();
        boolean changed = false;
        for (BlockState s : block.getStateDefinition().getPossibleStates()) {
            changed |= RadiationSystemNT.RAD_RESISTANT_STATES.remove(s);
        }
        if (changed) RadiationSystemNT.resistantStateVersion++;
    }

    static void markRadResistant(Block block, Predicate<BlockState> filter) {
        RadiationSystemNT.awaitSimulation();
        RadiationSystemNT.markRadResistant(block, filter);
    }

    static boolean markRadResistant(BlockState state) {
        RadiationSystemNT.awaitSimulation();
        boolean added = RadiationSystemNT.RAD_RESISTANT_STATES.add(state);
        if (added) RadiationSystemNT.resistantStateVersion++;
        return added;
    }

    static void unmarkRadResistant(BlockState state, boolean added) {
        if (!added) return;
        RadiationSystemNT.awaitSimulation();
        if (RadiationSystemNT.RAD_RESISTANT_STATES.remove(state)) RadiationSystemNT.resistantStateVersion++;
    }

    static void markRadSource(Block block, double emission, double saturation) {
        RadiationSystemNT.awaitSimulation();
        RadiationSystemNT.markRadSource(block, emission, saturation);
    }

    static void unmarkRadSource(Block block) {
        RadiationSystemNT.awaitSimulation();
        boolean changed = false;
        for (BlockState s : block.getStateDefinition().getPossibleStates()) {
            changed |= RadiationSystemNT.RAD_SOURCE_STATES.remove(s) != null;
        }
        if (changed) RadiationSystemNT.radSourceStateVersion++;
    }

    private void force(boolean forced) {
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) ForcedChunks.set(level, cx + dx, cz + dz, forced);
        }
    }
}
