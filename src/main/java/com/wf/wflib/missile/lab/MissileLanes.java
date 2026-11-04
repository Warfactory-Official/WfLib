package com.wf.wflib.missile.lab;

import com.wf.wflib.labs.api.LabContents;
import com.wf.wflib.labs.api.LabContext;
import com.wf.wflib.labs.api.LabTicks;
import com.wf.wflib.missile.api.MissileEntity;
import com.wf.wflib.missile.api.MissilePreset;
import com.wf.wflib.missile.api.MissilePresets;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

/** Scenario lanes on real ticks: far from spawn, one batch at a time, chunks held, seeded, cleared after. */
final class MissileLanes {
    private static final int ORIGIN_X = 200_000;
    private static final int SPACING = 4096;
    /** Game-time phase every batch starts on (radar sweeps, claims, listener wakeups). */
    private static final int ALIGN = 1200;
    private static final int RESET_RADIUS = 4;
    private static final int RESET_HEIGHT = 64;

    private MissileLanes() {
    }

    interface Lane {
        /** Chunks to hold, lane origin {@code (x, *, z)}. */
        List<ChunkPos> chunks(BlockPos origin);

        /** Origin y = ground surface. */
        void start(ServerLevel level, BlockPos origin);

        /** After each tick; true = done. */
        boolean step(ServerLevel level, int tick);

        /** Batch end (done or {@code maxTicks}); entities in the lane are removed afterwards. */
        void finish(ServerLevel level, boolean timedOut);
    }

    static void run(ServerLevel level, List<? extends Lane> lanes, int width, int maxTicks, long seed) {
        for (int b = 0; b * width < lanes.size(); b++) {
            List<? extends Lane> batch = lanes.subList(b * width, Math.min(lanes.size(), (b + 1) * width));
            List<BlockPos> origins = new ArrayList<>();
            Set<ChunkPos> held = new LinkedHashSet<>();
            for (int i = 0; i < batch.size(); i++) {
                BlockPos o = new BlockPos(ORIGIN_X, 0, i * SPACING);
                origins.add(o);
                held.addAll(batch.get(i).chunks(o));
            }
            LabTicks.hold(level, held);
            try {
                LabTicks.align(level, ALIGN);
                level.random.setSeed(seed ^ (b * 0x9E3779B97F4A7C15L));
                boolean[] done = new boolean[batch.size()];
                for (int i = 0; i < batch.size(); i++) {
                    BlockPos o = origins.get(i);
                    origins.set(i, o = reset(level, o));
                    batch.get(i).start(level, o);
                }
                int left = batch.size();
                for (int t = 1; t <= maxTicks && left > 0; t++) {
                    LabTicks.tick(level.getServer());
                    for (int i = 0; i < batch.size(); i++) {
                        if (!done[i] && batch.get(i).step(level, t)) {
                            done[i] = true;
                            left--;
                        }
                    }
                }
                for (int i = 0; i < batch.size(); i++) {
                    batch.get(i).finish(level, !done[i]);
                }
            } finally {
                for (BlockPos o : origins) {
                    clear(level, o);
                }
                LabTicks.tick(level.getServer());
                LabTicks.release(level, held);
            }
        }
    }

    /** Generator surface at the lane origin; anything built above it there (radius {@link #RESET_RADIUS}) is cleared. */
    private static BlockPos reset(ServerLevel level, BlockPos o) {
        int y = level.getChunkSource().getGenerator().getBaseHeight(o.getX(), o.getZ(),
                Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, level, level.getChunkSource().randomState());
        for (BlockPos p : BlockPos.betweenClosed(o.getX() - RESET_RADIUS, y, o.getZ() - RESET_RADIUS,
                o.getX() + RESET_RADIUS, y + RESET_HEIGHT, o.getZ() + RESET_RADIUS)) {
            if (!level.getBlockState(p).isAir()) {
                level.removeBlock(p, false);
            }
        }
        return new BlockPos(o.getX(), y, o.getZ());
    }

    private static void clear(ServerLevel level, BlockPos origin) {
        AABB box = new AABB(origin).inflate(SPACING / 2.0 - 1.0, 4096.0, SPACING / 2.0 - 1.0);
        for (MissileEntity m : level.getEntitiesOfClass(MissileEntity.class, box)) {
            m.discard();
        }
    }

    /** Chunks within {@code radius} chunks of the segment {@code a -> b}. */
    static List<ChunkPos> around(Vec3 a, Vec3 b, int radius) {
        Set<ChunkPos> out = new LinkedHashSet<>();
        int steps = (int) Math.ceil(a.distanceTo(b) / 8.0);
        for (int i = 0; i <= steps; i++) {
            Vec3 p = steps == 0 ? a : a.lerp(b, i / (double) steps);
            int cx = ((int) Math.floor(p.x)) >> 4;
            int cz = ((int) Math.floor(p.z)) >> 4;
            for (int dx = -radius; dx <= radius; dx++) {
                for (int dz = -radius; dz <= radius; dz++) {
                    out.add(new ChunkPos(cx + dx, cz + dz));
                }
            }
        }
        return new ArrayList<>(out);
    }

    /** Threat presets: air medium, not interceptors, {@code missiles=} globs, dev content per {@code dev=}. */
    static List<MissilePreset> threats(LabContext ctx) {
        String globs = ctx.param("missiles", null);
        List<Pattern> want = new ArrayList<>();
        if (globs != null) {
            for (String g : globs.split(",")) {
                want.add(Pattern.compile(Pattern.quote(g).replace("*", "\\E.*\\Q")));
            }
        }
        return MissilePresets.all().stream()
                .filter(p -> p.medium() == MissileEntity.Medium.AIR && !p.isInterceptor())
                .filter(p -> want.isEmpty() || want.stream().anyMatch(w -> w.matcher(p.id().toString()).matches()
                        || w.matcher(p.id().getPath()).matches()))
                .filter(p -> ctx.includeDev() || !LabContents.DEV_ORIGIN.equals(LabContents.origin(p.id().toString())))
                .sorted(Comparator.comparing(p -> p.id().toString()))
                .toList();
    }

    static String origin(MissilePreset p) {
        return LabContents.origin(p.id().toString());
    }
}
