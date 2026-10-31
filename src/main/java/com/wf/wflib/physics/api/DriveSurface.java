package com.wf.wflib.physics.api;

import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import net.minecraft.core.SectionPos;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Per-level cache of {@link SurfaceBake} sections. A section is rebaked when any of the 27 collision snapshots it read
 * changed (identity; rechecked only after {@link ChunkCollisionCache#epoch()} moves). Bake = one virtual thread per
 * section, one in flight per section; snapshots prepared on the caller, boxes collected + merged on the bake thread.
 * Sections within {@link #PREFETCH} of a view queued ahead; stale section served for the queue tick of its rebake; view
 * joins a section never baked. Tick / client thread only; {@link View} is what island tasks read.
 */
public final class DriveSurface {

    public static final double CLIMB = SurfaceBake.CLIMB;

    private static final class Entry {
        SurfaceSection section;
        SectionCollision[] inputs;
        long lastUse;
        CompletableFuture<SurfaceSection> pending;
        SectionCollision[] pendingInputs;
        /** Game tick the pending bake was queued. */
        long pendingTick;
        /** Cache epoch at which inputs last matched; equal => no recheck. */
        long checked = -1;

        Entry(SurfaceSection section, SectionCollision[] inputs) {
            this.section = section;
            this.inputs = inputs;
        }
    }

    /** Off the worldgen-shared background pool: a joined first bake queued behind chunk work. */
    private static final ExecutorService BAKERS = Executors.newVirtualThreadPerTaskExecutor();
    private final ChunkCollisionCache cache;
    /** Game ticks unused before eviction. */
    private static final int RETAIN_TICKS = 600;
    /** Border probe offset, blocks: a point on a section/column border asks both sides. */
    private static final double EDGE = 1.0e-3;
    /** Blocks around a view's bounds queued for baking. */
    private static final int PREFETCH = 8;
    private final Long2ObjectOpenHashMap<Entry> sections = new Long2ObjectOpenHashMap<>();
    private long lastSweep;

    public DriveSurface(ChunkCollisionCache cache) {
        this.cache = cache;
    }

    public static DriveSurface of(Level level) {
        return ChunkCollisionCache.of(level).surface;
    }

    /** Baked section, possibly stale while its rebake runs; joins a first bake. */
    public SurfaceSection section(Level level, long key) {
        Entry entry = refresh(level, key);
        if (entry.section == null) {
            entry.section = entry.pending.join();
            entry.inputs = entry.pendingInputs;
            entry.pending = null;
        }
        return entry.section;
    }

    /** Installs a finished bake; queues one when absent or stale and none matching is pending. */
    private Entry refresh(Level level, long key) {
        Entry entry = sections.get(key);
        if (entry == null) {
            entry = new Entry(null, null);
            sections.put(key, entry);
        }
        long now = level.getGameTime();
        entry.lastUse = now;
        // Installed on the first refresh after its queue tick, joined if unfinished. isDone() FORBIDDEN: thread timing
        // picked stale vs fresh => runs diverged.
        if (entry.pending != null && now > entry.pendingTick) {
            entry.section = entry.pending.join();
            entry.inputs = entry.pendingInputs;
            entry.pending = null;
        }
        // One bake in flight per section; still stale when it lands => next refresh resubmits. Queueing per change
        // FORBIDDEN: block churn queued one bake per tick (box copies), suite OOMed at 16 GB.
        long epoch = cache.epoch();
        if (entry.pending != null || entry.checked == epoch) {
            return entry;
        }
        int sx = SectionPos.x(key), sy = SectionPos.y(key), sz = SectionPos.z(key);
        if (entry.inputs != null && same(entry.inputs, inputs(sx, sy, sz))) {
            entry.checked = epoch;
            return entry;
        }
        int pad = SurfaceBake.PAD, padY = SurfaceBake.PAD_Y;
        AABB bounds = new AABB(sx * 16 - pad, sy * 16 - padY, sz * 16 - pad, sx * 16 + 16 + pad, sy * 16 + 16 + padY,
                sz * 16 + 16 + pad);
        cache.prepare(level, bounds);
        SectionCollision[] inputs = inputs(sx, sy, sz);
        if (allEmpty(inputs)) {
            entry.section = SurfaceSection.empty(sx, sy, sz);
            entry.inputs = inputs;
            entry.checked = epoch;
            return entry;
        }
        entry.pendingInputs = inputs;
        entry.pendingTick = now;
        entry.pending = CompletableFuture.supplyAsync(() -> {
            BoxBuffer boxes = new BoxBuffer();
            ChunkCollisionCache.collectBoxes(k -> inputs[((SectionPos.y(k) - sy + 1) * 3 + SectionPos.z(k) - sz + 1) * 3
                    + SectionPos.x(k) - sx + 1], bounds, boxes);
            return SurfaceBake.bake(sx, sy, sz, boxes);
        }, BAKERS);
        return entry;
    }

    private SectionCollision[] inputs(int sx, int sy, int sz) {
        SectionCollision[] inputs = new SectionCollision[27];
        int k = 0;
        for (int dy = -1; dy <= 1; dy++) {
            for (int dz = -1; dz <= 1; dz++) {
                for (int dx = -1; dx <= 1; dx++) {
                    inputs[k++] = cache.snapshotAt(SectionPos.asLong(sx + dx, sy + dy, sz + dz));
                }
            }
        }
        return inputs;
    }

    /** Bake reads boxes from these 27 only. */
    private static boolean allEmpty(SectionCollision[] inputs) {
        for (SectionCollision input : inputs) {
            if (input != null && !input.isEmpty()) {
                return false;
            }
        }
        return true;
    }

    /** Current null (unprepared or evicted) = unknown, not a change. */
    private static boolean same(SectionCollision[] baked, SectionCollision[] current) {
        for (int i = 0; i < baked.length; i++) {
            if (baked[i] != current[i] && current[i] != null) {
                return false;
            }
        }
        return true;
    }

    /** Sections meeting the bounds (one block of slack each way), baked now. */
    public View view(Level level, AABB bounds, CellTops tops) {
        long now = level.getGameTime();
        if (now - lastSweep > RETAIN_TICKS || now < lastSweep) {
            lastSweep = now;
            sections.values().removeIf(entry -> now - entry.lastUse > RETAIN_TICKS || now < entry.lastUse);
        }
        int x0 = Mth.floor(bounds.minX - 1) >> 4, x1 = Mth.floor(bounds.maxX + 1) >> 4;
        int y0 = Mth.floor(bounds.minY - 1) >> 4, y1 = Mth.floor(bounds.maxY + 1) >> 4;
        int z0 = Mth.floor(bounds.minZ - 1) >> 4, z1 = Mth.floor(bounds.maxZ + 1) >> 4;
        int pad = SurfaceBake.PAD, padY = SurfaceBake.PAD_Y;
        // Open air / water: no box within any viewed section's bake reach => every section empty; prefetch skipped.
        if (!cache.prepare(level, new AABB(x0 * 16 - pad, y0 * 16 - padY, z0 * 16 - pad, x1 * 16 + 16 + pad,
                y1 * 16 + 16 + padY, z1 * 16 + 16 + pad))) {
            return new View(tops, new Long2ObjectOpenHashMap<>());
        }
        for (int x = Mth.floor(bounds.minX - PREFETCH) >> 4; x <= Mth.floor(bounds.maxX + PREFETCH) >> 4; x++) {
            for (int y = Mth.floor(bounds.minY - 1) >> 4; y <= Mth.floor(bounds.maxY + 1) >> 4; y++) {
                for (int z = Mth.floor(bounds.minZ - PREFETCH) >> 4; z <= Mth.floor(bounds.maxZ + PREFETCH) >> 4; z++) {
                    refresh(level, SectionPos.asLong(x, y, z));
                }
            }
        }
        Long2ObjectOpenHashMap<SurfaceSection> map = new Long2ObjectOpenHashMap<>();
        for (int x = x0; x <= x1; x++) {
            for (int y = y0; y <= y1; y++) {
                for (int z = z0; z <= z1; z++) {
                    long key = SectionPos.asLong(x, y, z);
                    map.put(key, section(level, key));
                }
            }
        }
        return new View(tops, map);
    }

    @FunctionalInterface
    public interface TriangleSink {
        void accept(SurfaceSection section, int triangle);
    }

    /** Baked sections + cell tops; immutable, worker-safe when tops is. */
    public static final class View implements CellTops {

        private final CellTops tops;
        private final Long2ObjectOpenHashMap<SurfaceSection> sections;

        View(CellTops tops, Long2ObjectOpenHashMap<SurfaceSection> sections) {
            this.tops = tops;
            this.sections = sections;
        }

        @Override
        public double top(int x, int y, int z) {
            return tops.top(x, y, z);
        }

        /** Floor top replaced by the hill over (px, pz): the hill height nearest it within [-CLIMB, CLIMB]. */
        @Override
        public double surface(int x, int y, int z, double px, double pz) {
            double base = CellTops.super.surface(x, y, z, px, pz);
            if (Double.isNaN(base)) {
                return base;
            }
            double best = base, gap = Double.MAX_VALUE;
            for (int sy = Mth.floor(base - CLIMB) >> 4; sy <= Mth.floor(base + CLIMB) >> 4; sy++) {
                SurfaceSection section = sections.get(SectionPos.asLong(x >> 4, sy, z >> 4));
                if (section == null) {
                    continue;
                }
                for (int t = section.first(x, z), end = section.end(x, z); t < end; t++) {
                    double h = section.height(t, px, pz);
                    if (Math.abs(h - base) <= CLIMB && Math.abs(h - base) < gap) {
                        best = h;
                        gap = Math.abs(h - base);
                    }
                }
            }
            return best;
        }

        /** Point in a hill triangle's cover band ({@link SurfaceSection#covers}); sections on a border both asked. */
        public boolean covers(double x, double y, double z, double hullY) {
            for (int sx = Mth.floor(x - EDGE) >> 4; sx <= Mth.floor(x + EDGE) >> 4; sx++) {
                for (int sy = Mth.floor(y - CLIMB - 1) >> 4; sy <= Mth.floor(y + CLIMB + 1) >> 4; sy++) {
                    for (int sz = Mth.floor(z - EDGE) >> 4; sz <= Mth.floor(z + EDGE) >> 4; sz++) {
                        SurfaceSection section = sections.get(SectionPos.asLong(sx, sy, sz));
                        if (section != null && (section.covers(x, y, z, hullY) || section.covers(x - EDGE, y, z - EDGE, hullY)
                                || section.covers(x + EDGE, y, z + EDGE, hullY))) {
                            return true;
                        }
                    }
                }
            }
            return false;
        }

        /** Triangles of sections meeting the bounds over columns meeting its xz. */
        public void triangles(AABB bounds, TriangleSink sink) {
            for (SurfaceSection section : sections.values()) {
                int x0 = Math.max(section.sx * 16, Mth.floor(bounds.minX)), x1 = Math.min(section.sx * 16 + 15, Mth.floor(bounds.maxX));
                int z0 = Math.max(section.sz * 16, Mth.floor(bounds.minZ)), z1 = Math.min(section.sz * 16 + 15, Mth.floor(bounds.maxZ));
                if (x0 > x1 || z0 > z1 || section.triangleCount() == 0
                        || section.sy * 16 > bounds.maxY + 1 || section.sy * 16 + 16 < bounds.minY - 1) {
                    continue;
                }
                for (int x = x0; x <= x1; x++) {
                    for (int z = z0; z <= z1; z++) {
                        for (int t = section.first(x, z), end = section.end(x, z); t < end; t++) {
                            sink.accept(section, t);
                        }
                    }
                }
            }
        }
    }
}
