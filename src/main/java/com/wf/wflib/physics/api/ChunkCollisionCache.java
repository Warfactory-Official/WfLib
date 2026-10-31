package com.wf.wflib.physics.api;

import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.util.Mth;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.phys.AABB;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.StampedLock;
import com.wf.wflib.physics.collision.DynamicTree;

/**
 * Per-level cache of SectionCollision snapshots; physics asks geometry without touching a live
 * PalettedContainer. prepare(): tick-thread only, builds. collectBoxes()/anyBoxIn()/Cursor:
 * worker-safe, read finished snapshots only. invalidate(): LevelChunk.setBlockState mixin + chunk
 * load/unload events. Pin captures refs into PinnedSections (tick thread) for a deterministic
 * off-thread solve.
 */
public final class ChunkCollisionCache implements SectionSource {

    /**
     * Frozen view over the sections one vehicle's physics may touch this tick. Filled by
     * prepareAndPin() on the tick thread; immutable refs, so free to hold. Per-vehicle, never shared.
     */
    public static final class PinnedSections implements SectionSource {

        private final Long2ObjectOpenHashMap<SectionCollision> pinned =
                new Long2ObjectOpenHashMap<>();

        @Override
        @Nullable
        public SectionCollision section(long key) {
            return pinned.get(key);
        }

        public void clear() {
            pinned.clear();
        }

        void pin(long key, SectionCollision snapshot) {
            pinned.put(key, snapshot);
        }

    }

    /**
     * Soft cap on retained snapshots; roughly 16MB per level assuming mixed sections cost 8KB each.
     */
    private static final int MAX_SECTIONS = 2048;

    /** Columns untouched for this many ticks are dropped; also the sweep period. */
    private static final int RETAIN_TICKS = 200;

    private static final Map<Level, ChunkCollisionCache> CACHES = new ConcurrentHashMap<>();

    /**
     * One chunk's snapshots, array per section. Keyed by chunk, not section: carrier bounds cover
     * ~1500 sections but ~200 columns, so a sweep costs one hash probe/column + array index/section,
     * not a probe/section. Null slot: not yet prepared.
     */
    private static final class Column {

        private final SectionCollision[] bySection;
        /** Slot's snapshot predates a block change; still served until the next prepare rebuilds it. */
        private final boolean[] stale;
        /** Bit per slot: this section has a current snapshot. */
        private long presentMask;
        /** Bit per slot: that snapshot holds geometry. */
        private long solidMask;
        /** When the sweep last wanted this column. Per column, so the sweep stamps once, not per section. */
        private int lastUseTick;

        private Column(int count) {
            this.bySection = new SectionCollision[count];
            this.stale = new boolean[count];
        }

        private int solidCount() {
            int count = 0;
            for (SectionCollision snapshot : bySection) {
                if (snapshot != null && !snapshot.isEmpty()) {
                    count++;
                }
            }
            return count;
        }

    }

    /** Slots fromSlot..toSlot as a mask. */
    private static long range(int fromSlot, int toSlot) {
        return (toSlot - fromSlot + 1 >= 64 ? -1L : ((1L << (toSlot - fromSlot + 1)) - 1)) << fromSlot;
    }

    private final Long2ObjectOpenHashMap<Column> columns = new Long2ObjectOpenHashMap<>();
    /** Drive surface baked from this cache; same lifetime. */
    public final DriveSurface surface = new DriveSurface(this);
    private volatile long epoch;
    private final StampedLock sectionsLock = new StampedLock();
    private volatile int tick;

    /** Section y of the bottom of the level, and how many there are: the array's bounds. */
    private final int minSection;
    private final int sectionCount;
    /** Whether a column's slots fit one bitmask, which is what makes the sweep O(columns). */
    private final boolean masked;

    /** Per-section trees are shared across vehicles; scratch can't live on them, so per-thread instead. */
    private static final ThreadLocal<DynamicTree.QueryScratch> QUERY_SCRATCH =
            ThreadLocal.withInitial(DynamicTree.QueryScratch::new);

    private ChunkCollisionCache(Level level) {
        this.minSection = level.getMinSection();
        this.sectionCount = level.getSectionsCount();
        this.masked = this.sectionCount <= Long.SIZE;
    }

    public static ChunkCollisionCache of(Level level) {
        return CACHES.computeIfAbsent(level, ChunkCollisionCache::new);
    }

    /** The slot a section y occupies in a column, or -1 when it is outside the level. */
    private int slot(int sectionY) {
        int index = sectionY - minSection;
        return index < 0 || index >= sectionCount ? -1 : index;
    }

    /** Drops a level's cache entirely. Called when the level unloads. */
    public static void forget(Level level) {
        CACHES.remove(level);
    }

    /** The live view of this cache, for queries that can and should see current data. */
    @Override
    @Nullable
    public SectionCollision section(long key) {
        return read(key);
    }

    private SectionCollision read(long key) {
        int slot = slot(SectionPos.y(key));
        if (slot < 0) {
            return null;
        }
        return readSlot(SectionPos.asLong(SectionPos.x(key), 0, SectionPos.z(key)), slot);
    }

    /** One snapshot, by its column key and slot. Safe from a worker thread. */
    private SectionCollision readSlot(long columnKey, int slot) {
        long stamp = sectionsLock.tryOptimisticRead();
        SectionCollision snapshot;
        try {
            Column column = columns.get(columnKey);
            snapshot = column == null ? null : column.bySection[slot];
        } catch (RuntimeException raced) {
            // Lock-free probe mid-rehash can trip a bounds check on torn mask/key array; transient.
            snapshot = null;
            stamp = 0;
        }
        if (!sectionsLock.validate(stamp)) {
            stamp = sectionsLock.readLock();
            try {
                Column column = columns.get(columnKey);
                snapshot = column == null ? null : column.bySection[slot];
            } finally {
                sectionsLock.unlockRead(stamp);
            }
        }
        return snapshot;
    }

    /** Tick-thread only: column array writes happen only there, so the sweep may index it directly. Worker threads use readSlot. */
    private Column readColumn(long columnKey) {
        long stamp = sectionsLock.tryOptimisticRead();
        Column column;
        try {
            column = columns.get(columnKey);
        } catch (RuntimeException raced) {
            column = null;
            stamp = 0;
        }
        if (!sectionsLock.validate(stamp)) {
            stamp = sectionsLock.readLock();
            try {
                column = columns.get(columnKey);
            } finally {
                sectionsLock.unlockRead(stamp);
            }
        }
        return column;
    }

    private void write(long columnKey, int slot, SectionCollision snapshot) {
        long stamp = sectionsLock.writeLock();
        try {
            Column column = columns.get(columnKey);
            if (column == null) {
                column = new Column(sectionCount);
                columns.put(columnKey, column);
            }
            SectionCollision previous = column.bySection[slot];
            column.bySection[slot] = snapshot;
            epoch++;
            column.stale[slot] = false;
            column.presentMask |= 1L << slot;
            if (snapshot.isEmpty()) {
                column.solidMask &= ~(1L << slot);
            } else {
                column.solidMask |= 1L << slot;
            }
            if (previous != null && !previous.isEmpty()) {
                retained--;
            }
            if (!snapshot.isEmpty()) {
                retained++;
            }
        } finally {
            sectionsLock.unlockWrite(stamp);
        }
    }

    /** Cheap enough per block update. Stale snapshot stays readable: skipped-prepare reader sees old geometry, never air. */
    public static void invalidate(Level level, BlockPos pos) {
        ChunkCollisionCache cache = CACHES.get(level);
        if (cache == null) {
            return;
        }
        int slot = cache.slot(SectionPos.blockToSectionCoord(pos.getY()));
        if (slot < 0) {
            return;
        }
        long stamp = cache.sectionsLock.writeLock();
        try {
            Column column = cache.columns.get(SectionPos.asLong(
                    SectionPos.blockToSectionCoord(pos.getX()), 0, SectionPos.blockToSectionCoord(pos.getZ())));
            if (column != null && column.bySection[slot] != null) {
                column.stale[slot] = true;
                column.presentMask &= ~(1L << slot);
                cache.epoch++;
            }
        } finally {
            cache.sectionsLock.unlockWrite(stamp);
        }
    }

    /** Drops every snapshot belonging to a chunk. Used on chunk load and unload. */
    public static void invalidateChunk(Level level, ChunkPos chunkPos) {
        ChunkCollisionCache cache = CACHES.get(level);
        if (cache == null) {
            return;
        }
        long stamp = cache.sectionsLock.writeLock();
        try {
            Column removed = cache.columns.remove(SectionPos.asLong(chunkPos.x, 0, chunkPos.z));
            if (removed != null) {
                cache.epoch++;
                cache.retained -= removed.solidCount();
            }
        } finally {
            cache.sectionsLock.unlockWrite(stamp);
        }
    }

    /** Tick-thread only. @return false is exact: no overlapping section holds collision shapes. */
    public boolean prepare(Level level, AABB bounds) {
        return prepareAndPin(level, bounds, null);
    }

    /** Like prepare(), also pins into pin (replaced each call, never accumulates). EMPTY not pinned: absent == EMPTY to readers. */
    public boolean prepareAndPin(Level level, AABB bounds, @Nullable PinnedSections pin) {
        if (pin != null) {
            pin.clear();
        }
        if (level.isDebug()) {
            // Debug world synthesizes block states, not stored; caller falls back to live reads.
            return true;
        }
        this.tick = (int) level.getGameTime();

        int minSectionX = SectionPos.blockToSectionCoord(Mth.floor(bounds.minX));
        int minSectionY = SectionPos.blockToSectionCoord(Mth.floor(bounds.minY));
        int minSectionZ = SectionPos.blockToSectionCoord(Mth.floor(bounds.minZ));
        int maxSectionX = SectionPos.blockToSectionCoord(Mth.floor(bounds.maxX));
        int maxSectionY = SectionPos.blockToSectionCoord(Mth.floor(bounds.maxY));
        int maxSectionZ = SectionPos.blockToSectionCoord(Mth.floor(bounds.maxZ));

        if (maxSectionY < minSection || minSectionY > minSection + sectionCount - 1) {
            return false;
        }
        // tick is volatile; hoist to avoid a re-read per section in the loop below.
        int stamp = this.tick;
        int fromSlot = Mth.clamp(minSectionY - minSection, 0, sectionCount - 1);
        int toSlot = Mth.clamp(maxSectionY - minSection, 0, sectionCount - 1);
        boolean anySolid = false;
        long wanted = masked ? range(fromSlot, toSlot) : 0L;
        for (int sectionX = minSectionX; sectionX <= maxSectionX; sectionX++) {
            for (int sectionZ = minSectionZ; sectionZ <= maxSectionZ; sectionZ++) {
                long columnKey = SectionPos.asLong(sectionX, 0, sectionZ);
                Column column = readColumn(columnKey);
                if (column == null || !masked || (column.presentMask & wanted) != wanted) {
                    // Chunk fetched only if a snapshot is actually missing (settled hull: none are);
                    // unconditional getChunkNow cost more than every lookup.
                    ChunkAccess chunk = null;
                    boolean looked = false;
                    for (int slot = fromSlot; slot <= toSlot; slot++) {
                        if (column != null && column.bySection[slot] != null && !column.stale[slot]) {
                            continue;
                        }
                        if (!looked) {
                            chunk = level.getChunkSource().getChunkNow(sectionX, sectionZ);
                            looked = true;
                        }
                        write(columnKey, slot, build(chunk, slot + minSection));
                        column = readColumn(columnKey);
                    }
                }
                // Was a volatile write per section: 1500 barriers on a carrier. One stamp/column now.
                column.lastUseTick = stamp;
                if (!anySolid) {
                    anySolid = masked ? (column.solidMask & wanted) != 0
                            : anySolidIn(column, fromSlot, toSlot);
                }
                if (pin != null && masked) {
                    for (long bits = column.solidMask & wanted; bits != 0; bits &= bits - 1) {
                        int slot = Long.numberOfTrailingZeros(bits);
                        pin.pin(SectionPos.asLong(sectionX, slot + minSection, sectionZ), column.bySection[slot]);
                    }
                } else if (pin != null) {
                    for (int slot = fromSlot; slot <= toSlot; slot++) {
                        SectionCollision snapshot = column.bySection[slot];
                        if (snapshot != null && !snapshot.isEmpty()) {
                            pin.pin(SectionPos.asLong(sectionX, slot + minSection, sectionZ), snapshot);
                        }
                    }
                }
            }
        }

        if (retained > evictAbove || stamp - lastSweep >= RETAIN_TICKS) {
            evict(stamp);
        }
        return anySolid;
    }

    /**
     * Solid-only count. EMPTY is a shared singleton, free to keep; excluded from budget since a
     * large hull over open water touches thousands of them (counting them cleared a rotated
     * carrier's whole cache every tick).
     */
    private int retained;

    /** Unloaded chunk => EMPTY, never force-loaded: worldgen from physics is a hitch, can't run off-thread. */
    private static SectionCollision build(@Nullable ChunkAccess chunk, int sectionY) {
        if (chunk == null) {
            return SectionCollision.EMPTY;
        }
        int index = chunk.getSectionIndexFromSectionY(sectionY);
        LevelChunkSection[] chunkSections = chunk.getSections();
        if (index < 0 || index >= chunkSections.length) {
            return SectionCollision.EMPTY;
        }
        return SectionCollision.snapshot(chunkSections[index]);
    }

    /** Retained count that triggers the next eviction; raised while the working set exceeds budget. */
    private int evictAbove = MAX_SECTIONS;
    private int lastSweep;

    /**
     * Drops columns idle past RETAIN_TICKS (incl. empty-only: a never-loaded chunk gets no unload
     * event), then LRU down to budget. Never evicts this tick's columns: a prepare-skipping reader
     * via covers() would read them as air.
     */
    private void evict(int now) {
        lastSweep = now;
        int cutoff = now - RETAIN_TICKS;
        long stamp = sectionsLock.writeLock();
        try {
            columns.values().removeIf(column -> column.lastUseTick < cutoff);
            retained = countRetained();
            if (retained > MAX_SECTIONS) {
                Column[] idle = columns.values().stream()
                        .filter(column -> column.lastUseTick < now)
                        .sorted(java.util.Comparator.comparingInt(column -> column.lastUseTick))
                        .toArray(Column[]::new);
                for (int i = 0; i < idle.length && retained > MAX_SECTIONS; i++) {
                    columns.values().remove(idle[i]);
                    retained -= idle[i].solidCount();
                }
            }
            // Working set over budget => hold it; re-evict only after 25% growth.
            evictAbove = Math.max(MAX_SECTIONS, retained + MAX_SECTIONS / 4);
        } finally {
            sectionsLock.unlockWrite(stamp);
        }
    }

    /** Fallback for a level too tall for one mask. */
    private static boolean anySolidIn(Column column, int fromSlot, int toSlot) {
        for (int slot = fromSlot; slot <= toSlot; slot++) {
            SectionCollision snapshot = column.bySection[slot];
            if (snapshot != null && !snapshot.isEmpty()) {
                return true;
            }
        }
        return false;
    }

    private int countRetained() {
        int count = 0;
        for (Column column : columns.values()) {
            count += column.solidCount();
        }
        return count;
    }

    /** Snapshots holding geometry, for diagnostics. */
    public int retainedSections() {
        return retained;
    }

    /** Every snapshot, empty ones included, for diagnostics. */
    public int cachedSections() {
        return countCached();
    }

    private int countCached() {
        int count = 0;
        for (Column column : columns.values()) {
            for (SectionCollision snapshot : column.bySection) {
                if (snapshot != null) {
                    count++;
                }
            }
        }
        return count;
    }

    /** Memoizes last section (consecutive samples usually share one): map lookup -> long compare. Not thread-safe. */
    public static final class Cursor {

        private final SectionSource source;
        private long lastKey = Long.MIN_VALUE;
        private SectionCollision lastSection = SectionCollision.EMPTY;

        Cursor(SectionSource source) {
            this.source = source;
        }

        private SectionCollision resolve(int x, int y, int z) {
            long key = SectionPos.asLong(x >> 4, y >> 4, z >> 4);
            if (key != lastKey) {
                SectionCollision snapshot = source.section(key);
                lastSection = snapshot == null ? SectionCollision.EMPTY : snapshot;
                lastKey = key;
            }
            return lastSection;
        }

        /** Null: no collision shape. Safe off-thread; unprepared section reads as empty. */
        @Nullable
        public BlockState collisionAt(int x, int y, int z) {
            return resolve(x, y, z).collisionAt(SectionCollision.cellIndex(x & 15, y & 15, z & 15));
        }

        /** World-space; equals y when cell is empty. */
        public double collisionTop(int x, int y, int z) {
            return y + resolve(x, y, z).collisionTop(SectionCollision.cellIndex(x & 15, y & 15, z & 15));
        }

        /** Forgets the memoised section. Call after anything may have invalidated the cache. */
        public void reset() {
            lastKey = Long.MIN_VALUE;
            lastSection = SectionCollision.EMPTY;
        }

    }

    public Cursor cursor() {
        return new Cursor(this);
    }

    /** A cursor over an arbitrary source, such as a PinnedSections view for an off-thread solve. */
    public static Cursor cursorOver(SectionSource source) {
        return new Cursor(source);
    }

    /** First-found short-circuit; faster than collectBoxes for open-sky/level-ground cases. */
    public boolean anyBoxIn(AABB bounds) {
        return anyBoxIn(this, bounds);
    }

    /** Like anyBoxIn(AABB), resolving sections through source. */
    public static boolean anyBoxIn(SectionSource source, AABB bounds) {
        int minSectionX = SectionPos.blockToSectionCoord(Mth.floor(bounds.minX));
        int minSectionY = SectionPos.blockToSectionCoord(Mth.floor(bounds.minY));
        int minSectionZ = SectionPos.blockToSectionCoord(Mth.floor(bounds.minZ));
        int maxSectionX = SectionPos.blockToSectionCoord(Mth.floor(bounds.maxX));
        int maxSectionY = SectionPos.blockToSectionCoord(Mth.floor(bounds.maxY));
        int maxSectionZ = SectionPos.blockToSectionCoord(Mth.floor(bounds.maxZ));

        for (int sectionX = minSectionX; sectionX <= maxSectionX; sectionX++) {
            for (int sectionY = minSectionY; sectionY <= maxSectionY; sectionY++) {
                for (int sectionZ = minSectionZ; sectionZ <= maxSectionZ; sectionZ++) {
                    SectionCollision snapshot = source.section(SectionPos.asLong(sectionX, sectionY, sectionZ));
                    if (snapshot == null || snapshot.isEmpty()) {
                        continue;
                    }
                    int originX = SectionPos.sectionToBlockCoord(sectionX);
                    int originY = SectionPos.sectionToBlockCoord(sectionY);
                    int originZ = SectionPos.sectionToBlockCoord(sectionZ);
                    for (long packed : snapshot.collisionBoxes()) {
                        double x0 = originX + SectionCollision.boxMinX(packed);
                        double y0 = originY + SectionCollision.boxMinY(packed);
                        double z0 = originZ + SectionCollision.boxMinZ(packed);
                        if (x0 + SectionCollision.boxSizeX(packed) > bounds.minX && x0 < bounds.maxX
                                && y0 + SectionCollision.boxSizeY(packed) > bounds.minY && y0 < bounds.maxY
                                && z0 + SectionCollision.boxSizeZ(packed) > bounds.minZ && z0 < bounds.maxZ) {
                            return true;
                        }
                    }
                }
            }
        }
        return false;
    }

    /** World coords. Safe off-thread; unprepared/empty sections contribute nothing. Prefer over the List&lt;AABB&gt; overload (allocates per box). */
    public void collectBoxes(AABB bounds, BoxBuffer out) {
        forEachBox(this, bounds.minX, bounds.minY, bounds.minZ,
                bounds.maxX, bounds.maxY, bounds.maxZ, out::add);
    }

    /** Like above but with primitive bounds; per-substep query allocates nothing. */
    public void collectBoxes(double minX, double minY, double minZ,
                             double maxX, double maxY, double maxZ, BoxBuffer out) {
        forEachBox(this, minX, minY, minZ, maxX, maxY, maxZ, out::add);
    }

    public void collectBoxes(AABB bounds, List<AABB> out) {
        forEachBox(this, bounds.minX, bounds.minY, bounds.minZ,
                bounds.maxX, bounds.maxY, bounds.maxZ,
                (x0, y0, z0, x1, y1, z1) -> out.add(new AABB(x0, y0, z0, x1, y1, z1)));
    }

    /** Like collectBoxes(AABB, BoxBuffer), resolving sections through source. */
    public static void collectBoxes(SectionSource source, AABB bounds, BoxBuffer out) {
        forEachBox(source, bounds.minX, bounds.minY, bounds.minZ,
                bounds.maxX, bounds.maxY, bounds.maxZ, out::add);
    }

    /** Primitive-bounds form over an arbitrary source; for the substep broadphase. */
    public static void collectBoxes(SectionSource source, double minX, double minY, double minZ,
                                    double maxX, double maxY, double maxZ, BoxBuffer out) {
        forEachBox(source, minX, minY, minZ, maxX, maxY, maxZ, out::add);
    }

    /** Receives each merged box as primitives. */
    private interface BoxSink {
        void accept(double minX, double minY, double minZ, double maxX, double maxY, double maxZ);
    }

    private static void forEachBox(SectionSource source,
                                   double boundsMinX, double boundsMinY, double boundsMinZ,
                                   double boundsMaxX, double boundsMaxY, double boundsMaxZ, BoxSink out) {
        int minSectionX = SectionPos.blockToSectionCoord(Mth.floor(boundsMinX));
        int minSectionY = SectionPos.blockToSectionCoord(Mth.floor(boundsMinY));
        int minSectionZ = SectionPos.blockToSectionCoord(Mth.floor(boundsMinZ));
        int maxSectionX = SectionPos.blockToSectionCoord(Mth.floor(boundsMaxX));
        int maxSectionY = SectionPos.blockToSectionCoord(Mth.floor(boundsMaxY));
        int maxSectionZ = SectionPos.blockToSectionCoord(Mth.floor(boundsMaxZ));

        DynamicTree.QueryScratch scratch = null;
        for (int sectionX = minSectionX; sectionX <= maxSectionX; sectionX++) {
            for (int sectionY = minSectionY; sectionY <= maxSectionY; sectionY++) {
                for (int sectionZ = minSectionZ; sectionZ <= maxSectionZ; sectionZ++) {
                    SectionCollision snapshot = source.section(SectionPos.asLong(sectionX, sectionY, sectionZ));
                    if (snapshot == null || snapshot.isEmpty()) {
                        continue;
                    }
                    int originX = SectionPos.sectionToBlockCoord(sectionX);
                    int originY = SectionPos.sectionToBlockCoord(sectionY);
                    int originZ = SectionPos.sectionToBlockCoord(sectionZ);
                    long[] boxes = snapshot.collisionBoxes();
                    DynamicTree tree = snapshot.collisionTree();
                    if (tree != null) {
                        if (scratch == null) {
                            scratch = QUERY_SCRATCH.get();
                        }
                        int count = tree.query(scratch,
                                boundsMinX - originX, boundsMinY - originY, boundsMinZ - originZ,
                                boundsMaxX - originX, boundsMaxY - originY, boundsMaxZ - originZ);
                        for (int i = 0; i < count; i++) {
                            emitBox(boundsMinX, boundsMinY, boundsMinZ,
                                    boundsMaxX, boundsMaxY, boundsMaxZ,
                                    out, boxes[scratch.result(i)], originX, originY, originZ);
                        }
                    } else {
                        for (long packed : boxes) {
                            emitBox(boundsMinX, boundsMinY, boundsMinZ,
                                    boundsMaxX, boundsMaxY, boundsMaxZ,
                                    out, packed, originX, originY, originZ);
                        }
                    }
                }
            }
        }
    }

    /** Exact test kept even behind a tree query: fattened proxies may return boxes outside bounds. */
    private static void emitBox(double boundsMinX, double boundsMinY, double boundsMinZ,
                                double boundsMaxX, double boundsMaxY, double boundsMaxZ,
                                BoxSink out, long packed,
                                int originX, int originY, int originZ) {
        double x0 = originX + SectionCollision.boxMinX(packed);
        double y0 = originY + SectionCollision.boxMinY(packed);
        double z0 = originZ + SectionCollision.boxMinZ(packed);
        double x1 = x0 + SectionCollision.boxSizeX(packed);
        double y1 = y0 + SectionCollision.boxSizeY(packed);
        double z1 = z0 + SectionCollision.boxSizeZ(packed);
        if (x1 <= boundsMinX || x0 >= boundsMaxX
                || y1 <= boundsMinY || y0 >= boundsMaxY
                || z1 <= boundsMinZ || z0 >= boundsMaxZ) {
            return;
        }
        out.accept(x0, y0, z0, x1, y1, z1);
    }

    /** Debug overlay only; physics should go through a Cursor. */
    @Nullable
    /** Bumped under the write lock by every snapshot write, invalidation, column drop. */
    public long epoch() {
        return epoch;
    }

    public SectionCollision snapshotAt(long sectionKey) {
        return read(sectionKey);
    }

    /** Every snapshot, empty included; debug overlay readout (same count as cachedSections()). */
    public int cachedSectionCount() {
        return countCached();
    }

    /** Approximate retained bytes across all snapshots; for debug overlay readout. */
    public long footprint() {
        long stamp = sectionsLock.readLock();
        try {
            long total = 0;
            for (Column column : columns.values()) {
                for (SectionCollision snapshot : column.bySection) {
                    if (snapshot != null) {
                        total += snapshot.footprint();
                    }
                }
            }
            return total;
        } finally {
            sectionsLock.unlockRead(stamp);
        }
    }

}
