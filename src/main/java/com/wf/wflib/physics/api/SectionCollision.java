package com.wf.wflib.physics.api;

import it.unimi.dsi.fastutil.objects.Reference2IntMap;
import it.unimi.dsi.fastutil.objects.Reference2IntOpenHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.level.EmptyBlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import com.wf.wflib.physics.collision.DynamicTree;

/**
 * Collision snapshot of one 16x16x16 section, copied on the tick thread; immutable, read by physics threads.
 * Occupancy = collision shape, not solid-block: vehicles rest at actual wheel height.
 */
public abstract sealed class SectionCollision {

    public static final int SECTION_SIZE = 4096;

    /** No collision anywhere in this section. Shared; carries no per-section state. */
    public static final SectionCollision EMPTY = new Empty();

    /** Null: empty cell. localIndex = (y << 8) | (z << 4) | x. */
    @Nullable
    public abstract BlockState collisionAt(int localIndex);

    /** Null: empty cell. Packed cell-relative; FULL_CUBE for a whole block. */
    @Nullable
    abstract long[] boxesAt(int localIndex);

    public boolean isEmpty() {
        return this == EMPTY;
    }

    private boolean occupied(int localIndex) {
        long[] boxes = boxesAt(localIndex);
        return boxes != null && boxes.length > 0;
    }

    /** Blocks above floor, e.g. 1.0 whole block, 0.5 bottom slab, 1.5 fence; 0 when empty. */
    public double collisionTop(int localIndex) {
        long[] boxes = boxesAt(localIndex);
        if (boxes == null || boxes.length == 0) {
            return 0;
        }
        int top = 0;
        for (long box : boxes) {
            top = Math.max(top, rawMin(box, 1) + rawSize(box, 1));
        }
        return top / 16.0;
    }

    /** How this section was stored, for debugging memory use. */
    public Kind kind() {
        return this instanceof Uniform ? Kind.UNIFORM
                : this instanceof ByteIndexed ? Kind.BYTE_INDEXED
                : this instanceof ShortIndexed ? Kind.SHORT_INDEXED
                : Kind.EMPTY;
    }

    public enum Kind {
        EMPTY, UNIFORM, BYTE_INDEXED, SHORT_INDEXED
    }

    /** Approximate retained size in bytes, for the cache's memory budget. */
    public abstract int footprint();

    int mergedFootprint() {
        long[] boxes = merged;
        return boxes == null ? 0 : boxes.length * 8;
    }

    /** Packed: min bits 0-29, size bits 30-59, 10 bits/axis, sixteenths of a block. */
    private static final int COORD_BITS = 10;
    private static final long COORD_MASK = (1L << COORD_BITS) - 1;
    private static final int MAX_COORD = (int) COORD_MASK;
    private static final int SIZE_SHIFT = 3 * COORD_BITS;

    /** Cell holds nothing that collides. */
    static final long[] NO_COLLISION = new long[0];

    /** An ordinary whole block. Shared, so the merge can identity-compare the common case. */
    static final long[] FULL_CUBE = {packBox(0, 0, 0, 16, 16, 16)};

    private static long packBox(int minX, int minY, int minZ, int sizeX, int sizeY, int sizeZ) {
        return (long) minX
                | ((long) minY << COORD_BITS)
                | ((long) minZ << (2 * COORD_BITS))
                | ((long) sizeX << SIZE_SHIFT)
                | ((long) sizeY << (SIZE_SHIFT + COORD_BITS))
                | ((long) sizeZ << (SIZE_SHIFT + 2 * COORD_BITS));
    }

    private static int rawMin(long packed, int axis) {
        return (int) ((packed >>> (axis * COORD_BITS)) & COORD_MASK);
    }

    private static int rawSize(long packed, int axis) {
        return (int) ((packed >>> (SIZE_SHIFT + axis * COORD_BITS)) & COORD_MASK);
    }

    public static double boxMinX(long packed) { return rawMin(packed, 0) / 16.0; }
    public static double boxMinY(long packed) { return rawMin(packed, 1) / 16.0; }
    public static double boxMinZ(long packed) { return rawMin(packed, 2) / 16.0; }
    public static double boxSizeX(long packed) { return rawSize(packed, 0) / 16.0; }
    public static double boxSizeY(long packed) { return rawSize(packed, 1) / 16.0; }
    public static double boxSizeZ(long packed) { return rawSize(packed, 2) / 16.0; }

    /** Cached forever: BlockState is canonical, safe as a permanent key. */
    private static final Map<BlockState, long[]> SHAPE_BOXES = new ConcurrentHashMap<>();

    static long[] boxesOf(BlockState state) {
        return SHAPE_BOXES.computeIfAbsent(state, SectionCollision::computeBoxes);
    }

    private static long[] computeBoxes(BlockState state) {
        if (state.isAir()) {
            return NO_COLLISION;
        }
        // Dynamic shape (e.g. moving piston) needs the world to resolve; treated as full block (matches old isSolid).
        if (state.getBlock().hasDynamicShape()) {
            return FULL_CUBE;
        }
        VoxelShape shape = state.getCollisionShape(EmptyBlockGetter.INSTANCE, BlockPos.ZERO);
        if (shape.isEmpty()) {
            return NO_COLLISION;
        }
        if (Block.isShapeFullBlock(shape)) {
            return FULL_CUBE;
        }
        List<AABB> parts = shape.toAabbs();
        if (parts.isEmpty()) {
            return NO_COLLISION;
        }
        long[] boxes = new long[parts.size()];
        for (int i = 0; i < boxes.length; i++) {
            AABB part = parts.get(i);
            int minX = floorSixteenths(part.minX);
            int minY = floorSixteenths(part.minY);
            int minZ = floorSixteenths(part.minZ);
            boxes[i] = packBox(minX, minY, minZ,
                    Math.max(1, ceilSixteenths(part.maxX) - minX),
                    Math.max(1, ceilSixteenths(part.maxY) - minY),
                    Math.max(1, ceilSixteenths(part.maxZ) - minZ));
        }
        return boxes;
    }

    private static int floorSixteenths(double blocks) {
        return Mth.clamp(Mth.floor(blocks * 16), 0, MAX_COORD);
    }

    private static int ceilSixteenths(double blocks) {
        return Mth.clamp(Mth.ceil(blocks * 16), 0, MAX_COORD);
    }

    /** Greedy-merged, not minimal. Cached after first use; lazy-init races are benign. */
    public long[] collisionBoxes() {
        long[] boxes = merged;
        if (boxes == null) {
            boxes = isEmpty() ? NO_COLLISION : merge();
            merged = boxes;
        }
        return boxes;
    }

    private volatile long[] merged;

    /** Below this, a linear scan beats a tree walk. */
    private static final int TREE_THRESHOLD = 64;

    private volatile DynamicTree tree;

    /** Null below TREE_THRESHOLD. Built once per snapshot, shared by every vehicle over this section. */
    @Nullable
    DynamicTree collisionTree() {
        long[] boxes = collisionBoxes();
        if (boxes.length < TREE_THRESHOLD) {
            return null;
        }
        DynamicTree built = tree;
        if (built == null) {
            built = new DynamicTree(boxes.length);
            built.build(boxes);
            tree = built;
        }
        return built;
    }

    /** Extend X, widen Z, raise Y; each occupied cell covered exactly once; merges only identical shapes. */
    private long[] merge() {
        boolean[] used = new boolean[SECTION_SIZE];
        long[] out = new long[128];
        int count = 0;

        for (int y = 0; y < 16; y++) {
            for (int z = 0; z < 16; z++) {
                for (int x = 0; x < 16; x++) {
                    int start = cellIndex(x, y, z);
                    if (used[start] || !occupied(start)) {
                        continue;
                    }
                    long[] shape = boxesAt(start);

                    int width = 1;
                    if (spans(shape, 0)) {
                        while (x + width < 16 && matches(used, cellIndex(x + width, y, z), shape)) {
                            width++;
                        }
                    }
                    int depth = 1;
                    if (spans(shape, 2)) {
                        while (z + depth < 16 && rowMatches(used, x, y, z + depth, width, shape)) {
                            depth++;
                        }
                    }
                    int height = 1;
                    if (spans(shape, 1)) {
                        while (y + height < 16 && slabMatches(used, x, y + height, z, width, depth, shape)) {
                            height++;
                        }
                    }
                    for (int dy = 0; dy < height; dy++) {
                        for (int dz = 0; dz < depth; dz++) {
                            for (int dx = 0; dx < width; dx++) {
                                used[cellIndex(x + dx, y + dy, z + dz)] = true;
                            }
                        }
                    }

                    for (long box : shape) {
                        if (count == out.length) {
                            out = Arrays.copyOf(out, count * 2);
                        }
                        // width/height/depth > 1 only on axes where spans() held => extension exact.
                        out[count++] = packBox(
                                x * 16 + rawMin(box, 0),
                                y * 16 + rawMin(box, 1),
                                z * 16 + rawMin(box, 2),
                                rawSize(box, 0) + (width - 1) * 16,
                                rawSize(box, 1) + (height - 1) * 16,
                                rawSize(box, 2) + (depth - 1) * 16);
                    }
                }
            }
        }
        return count == out.length ? out : Arrays.copyOf(out, count);
    }

    /** True when every box in a shape runs the full width of its cell along the axis. */
    private static boolean spans(long[] shape, int axis) {
        for (long box : shape) {
            if (rawMin(box, axis) != 0 || rawSize(box, axis) != 16) {
                return false;
            }
        }
        return true;
    }

    private boolean matches(boolean[] used, int localIndex, long[] shape) {
        if (used[localIndex] || !occupied(localIndex)) {
            return false;
        }
        return sameShape(boxesAt(localIndex), shape);
    }

    /** Identity check first: hits for palette entries and FULL_CUBE. */
    private static boolean sameShape(long[] a, long[] b) {
        return a == b || Arrays.equals(a, b);
    }

    private boolean rowMatches(boolean[] used, int x, int y, int z, int width, long[] shape) {
        for (int dx = 0; dx < width; dx++) {
            if (!matches(used, cellIndex(x + dx, y, z), shape)) {
                return false;
            }
        }
        return true;
    }

    private boolean slabMatches(boolean[] used, int x, int y, int z, int width, int depth, long[] shape) {
        for (int dz = 0; dz < depth; dz++) {
            if (!rowMatches(used, x, y, z + dz, width, shape)) {
                return false;
            }
        }
        return true;
    }

    public static int cellIndex(int x, int y, int z) {
        return (y << 8) | (z << 4) | x;
    }

    /** Tick thread only. */
    public static SectionCollision snapshot(@Nullable LevelChunkSection section) {
        if (section == null || section.hasOnlyAir() || !section.maybeHas(SectionCollision::mayCollide)) {
            return EMPTY;
        }

        // narrow (byte) indexes up to 255 palette states; upgraded to wide (short) past that.
        byte[] narrow = new byte[SECTION_SIZE];
        short[] wide = null;
        List<BlockState> palette = new ArrayList<>();
        List<long[]> paletteBoxes = new ArrayList<>();
        Reference2IntMap<BlockState> ids = new Reference2IntOpenHashMap<>();
        // Reference identity valid: BlockState canonical. Negative id memoizes non-colliding states.
        ids.defaultReturnValue(0);
        int cells = 0;

        for (int i = 0; i < SECTION_SIZE; i++) {
            BlockState state = section.getBlockState(i & 15, i >> 8, (i >> 4) & 15);
            if (state.isAir()) {
                continue;
            }
            int id = ids.getInt(state);
            if (id < 0) {
                continue;
            }
            if (id == 0) {
                long[] boxes = boxesOf(state);
                if (boxes.length == 0) {
                    ids.put(state, -1);
                    continue;
                }
                palette.add(state);
                paletteBoxes.add(boxes);
                id = palette.size();
                ids.put(state, id);
                if (id > 255 && wide == null) {
                    wide = new short[SECTION_SIZE];
                    for (int j = 0; j < SECTION_SIZE; j++) {
                        wide[j] = (short) (narrow[j] & 0xFF);
                    }
                }
            }
            cells++;
            if (wide != null) {
                wide[i] = (short) id;
            } else {
                narrow[i] = (byte) id;
            }
        }

        if (cells == 0) {
            // maybeHas() is conservative: a pass finding nothing is reachable.
            return EMPTY;
        }
        long[][] boxTable = paletteBoxes.toArray(new long[0][]);
        if (cells == SECTION_SIZE && palette.size() == 1) {
            return new Uniform(palette.get(0), boxTable[0]);
        }
        BlockState[] states = palette.toArray(new BlockState[0]);
        if (wide != null) {
            return new ShortIndexed(states, boxTable, wide);
        }
        return new ByteIndexed(states, boxTable, narrow);
    }

    private static boolean mayCollide(BlockState state) {
        return boxesOf(state).length > 0;
    }

    private static final class Empty extends SectionCollision {

        @Override
        public BlockState collisionAt(int localIndex) {
            return null;
        }

        @Override
        long[] boxesAt(int localIndex) {
            return null;
        }

        @Override
        public int footprint() {
            return 0;
        }

    }

    /** Every cell is the same state, like deep stone or a filled section of one material. */
    private static final class Uniform extends SectionCollision {

        private final BlockState state;
        private final long[] boxes;

        private Uniform(BlockState state, long[] boxes) {
            this.state = state;
            this.boxes = boxes;
        }

        @Override
        public BlockState collisionAt(int localIndex) {
            return state;
        }

        @Override
        long[] boxesAt(int localIndex) {
            return boxes;
        }

        @Override
        public int footprint() {
            return 16 + mergedFootprint();
        }

    }

    /** The common case: fewer than 256 distinct colliding states, one byte per cell. */
    private static final class ByteIndexed extends SectionCollision {

        private final BlockState[] palette;
        private final long[][] paletteBoxes;
        private final byte[] index;

        private ByteIndexed(BlockState[] palette, long[][] paletteBoxes, byte[] index) {
            this.palette = palette;
            this.paletteBoxes = paletteBoxes;
            this.index = index;
        }

        @Override
        public BlockState collisionAt(int localIndex) {
            int id = index[localIndex] & 0xFF;
            return id == 0 ? null : palette[id - 1];
        }

        @Override
        long[] boxesAt(int localIndex) {
            int id = index[localIndex] & 0xFF;
            return id == 0 ? null : paletteBoxes[id - 1];
        }

        @Override
        public int footprint() {
            return SECTION_SIZE + palette.length * 16 + mergedFootprint() + 32;
        }

    }

    /** Fallback for a section with 256+ distinct colliding states. */
    private static final class ShortIndexed extends SectionCollision {

        private final BlockState[] palette;
        private final long[][] paletteBoxes;
        private final short[] index;

        private ShortIndexed(BlockState[] palette, long[][] paletteBoxes, short[] index) {
            this.palette = palette;
            this.paletteBoxes = paletteBoxes;
            this.index = index;
        }

        @Override
        public BlockState collisionAt(int localIndex) {
            int id = index[localIndex] & 0xFFFF;
            return id == 0 ? null : palette[id - 1];
        }

        @Override
        long[] boxesAt(int localIndex) {
            int id = index[localIndex] & 0xFFFF;
            return id == 0 ? null : paletteBoxes[id - 1];
        }

        @Override
        public int footprint() {
            return SECTION_SIZE * 2 + palette.length * 16 + mergedFootprint() + 32;
        }

    }

}
