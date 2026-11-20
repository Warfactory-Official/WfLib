package com.wf.wflib.contract.site;

import com.wf.wflib.contract.site.BasePlan.Piece;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.FenceBlock;
import net.minecraft.world.level.block.IronBarsBlock;
import net.minecraft.world.level.block.LadderBlock;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.WallBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BedPart;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.block.state.properties.SlabType;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.level.levelgen.Heightmap;

import java.util.ArrayList;
import java.util.List;

/**
 * Puts a {@link BasePlan} into the world: grades the pad level (cutting hills, filling hollows, blending an apron into the land
 * around it), then raises the wall, towers, buildings, nests, trenches and the rest of what the plan lists. The look is modern
 * military (concrete, steel, ballistic glass, hesco, sandbags, containers; see {@link Palette}) and every piece has a job: beds
 * to sleep in, a workbench, a furnace to cook on, a map table, chests to stock, lights that are lamps. No decoration for its own
 * sake. Each piece reads its {@code style} to pick among several looks. The caller loads the chunks and keeps them. Server
 * thread; one call can place tens of thousands of blocks.
 */
public final class BaseBuilder {
    private static final int FLAGS = 2 | 16;
    private static final int APRON = 6, CLEAR = 16;
    private static final Direction[] DIRS = {Direction.NORTH, Direction.EAST, Direction.SOUTH, Direction.WEST};

    private final ServerLevel level;
    private final Palette pal;
    private final RandomSource rng;
    private final BlockPos.MutableBlockPos at = new BlockPos.MutableBlockPos();
    /** Blocks whose shape depends on their neighbours (bars, fences, stairs): re-shaped once everything stands. */
    private final List<BlockPos> shaped = new ArrayList<>();

    private BaseBuilder(ServerLevel level, Palette pal, RandomSource rng) {
        this.level = level;
        this.pal = pal;
        this.rng = rng;
    }

    public static void build(ServerLevel level, BasePlan plan, Palette pal, long seed) {
        BaseBuilder b = new BaseBuilder(level, pal, RandomSource.create(seed));
        b.load(plan);
        b.grade(plan);
        if (plan.wall() != null) b.wall(plan);
        for (Piece p : plan.pieces()) b.piece(p, plan);
        b.reshape();
    }

    // ---------------------------------------------------------------- terrain

    private void load(BasePlan plan) {
        int r = plan.kind().radius + APRON + 2;
        for (int x = plan.cx() - r; x <= plan.cx() + r; x += 16) {
            for (int z = plan.cz() - r; z <= plan.cz() + r; z += 16) level.getChunk(x >> 4, z >> 4, ChunkStatus.FULL, true);
        }
        level.getChunk((plan.cx() + r) >> 4, (plan.cz() + r) >> 4, ChunkStatus.FULL, true);
    }

    /** The pad is level at {@code padY}; past it the ground eases back to what it was over {@link #APRON} blocks. */
    private void grade(BasePlan plan) {
        int r = plan.kind().radius;
        for (int dx = -r - APRON; dx <= r + APRON; dx++) {
            for (int dz = -r - APRON; dz <= r + APRON; dz++) {
                double d = Math.sqrt(dx * dx + dz * dz);
                if (d > r + APRON) continue;
                int x = plan.cx() + dx, z = plan.cz() + dz;
                int natural = level.getHeight(Heightmap.Types.OCEAN_FLOOR_WG, x, z);       // first free block over the real ground
                int target = d <= r ? plan.padY() : plan.padY() + (int) Math.round((natural - plan.padY()) * ((d - r) / APRON));
                setColumn(x, z, natural, target);
            }
        }
    }

    /** Makes the ground in a column end at {@code target} (the first free block): fills up or cuts down, then clears what stood above. */
    private void setColumn(int x, int z, int natural, int target) {
        for (int y = Math.min(natural, target); y < target; y++) set(x, y, z, y == target - 1 ? pal.ground().defaultBlockState() : Blocks.DIRT.defaultBlockState());
        if (target - 1 >= natural && target - 1 > level.getMinBuildHeight()) set(x, target - 1, z, pal.ground().defaultBlockState());
        int top = Math.max(natural, target) + CLEAR;
        for (int y = target; y < top; y++) {
            if (!level.getBlockState(at.set(x, y, z)).isAir()) set(x, y, z, Blocks.AIR.defaultBlockState());
        }
    }

    // ---------------------------------------------------------------- helpers

    private void set(int x, int y, int z, BlockState s) {
        s = pal.weathered(s, rng);
        level.setBlock(at.set(x, y, z), s, FLAGS);
        Block b = s.getBlock();
        if (b instanceof IronBarsBlock || b instanceof FenceBlock || b instanceof WallBlock || b instanceof StairBlock) shaped.add(new BlockPos(x, y, z));
    }

    private void set(int x, int y, int z, Block b) {
        set(x, y, z, b.defaultBlockState());
    }

    /** {@code b} turned to {@code d} when it has a horizontal facing (chests, furnaces, lecterns, anvils, stairs). */
    private void set(int x, int y, int z, Block b, Direction d) {
        BlockState s = b.defaultBlockState();
        if (s.hasProperty(BlockStateProperties.HORIZONTAL_FACING)) s = s.setValue(BlockStateProperties.HORIZONTAL_FACING, d);
        set(x, y, z, s);
    }

    private void fill(int x0, int y0, int z0, int x1, int y1, int z1, Block b) {
        BlockState s = b.defaultBlockState();
        for (int x = Math.min(x0, x1); x <= Math.max(x0, x1); x++) {
            for (int y = Math.min(y0, y1); y <= Math.max(y0, y1); y++) {
                for (int z = Math.min(z0, z1); z <= Math.max(z0, z1); z++) set(x, y, z, s);
            }
        }
    }

    /** A box with an air inside: walls, and the floor and roof layers left to the caller. */
    private void shell(int x0, int y0, int z0, int x1, int y1, int z1, Block wall) {
        fill(x0, y0, z0, x1, y1, z1, wall);
        fill(x0 + 1, y0, z0 + 1, x1 - 1, y1, z1 - 1, Blocks.AIR);
    }

    /** Only the outline of a rectangle at one height. */
    private void ring(int x0, int y, int z0, int x1, int z1, Block b) {
        for (int x = x0; x <= x1; x++) {
            set(x, y, z0, b);
            set(x, y, z1, b);
        }
        for (int z = z0; z <= z1; z++) {
            set(x0, y, z, b);
            set(x1, y, z, b);
        }
    }

    private BlockState slab(Block b, SlabType type) {
        return b.defaultBlockState().setValue(SlabBlock.TYPE, type);
    }

    private static Direction dir(int facing) {
        return DIRS[facing & 3];
    }

    private void bed(int x, int y, int z, Direction headTo, Block bed) {
        BlockState foot = bed.defaultBlockState().setValue(BedBlock.FACING, headTo).setValue(BedBlock.PART, BedPart.FOOT);
        set(x, y, z, foot);
        set(x + headTo.getStepX(), y, z + headTo.getStepZ(), foot.setValue(BedBlock.PART, BedPart.HEAD));
    }

    private void ironDoor(int x, int y, int z, Direction facing) {
        BlockState lo = Blocks.IRON_DOOR.defaultBlockState().setValue(DoorBlock.FACING, facing).setValue(DoorBlock.OPEN, true).setValue(DoorBlock.HALF, DoubleBlockHalf.LOWER);
        set(x, y, z, lo);
        set(x, y + 1, z, lo.setValue(DoorBlock.HALF, DoubleBlockHalf.UPPER));
    }

    /** Bars, fences and stairs were placed without neighbour updates (fast); join them up now. */
    private void reshape() {
        for (BlockPos p : shaped) {
            BlockState s = level.getBlockState(p);
            BlockState n = Block.updateFromNeighbourShapes(s, level, p);
            if (n != s) level.setBlock(p, n, 2 | 16);
        }
    }

    private Block accent(int style) {
        return pal.containers().get(Math.floorMod(style, pal.containers().size()));
    }

    private int x0(Piece p) {
        return p.x() - p.w() / 2;
    }

    private int z0(Piece p) {
        return p.z() - p.d() / 2;
    }

    // ---------------------------------------------------------------- the wall

    /** The block of one wall cell at {@code h} above the pad, for the wall's style and the stretch it is in. */
    private boolean hescoStretch(BasePlan plan, int i) {
        return switch (plan.wall().style()) {
            case BasePlan.Wall.HESCO -> true;
            case BasePlan.Wall.MIXED -> Math.floorMod((i + plan.wall().half()) / 7, 2) == 1;
            default -> false;
        };
    }

    private void wall(BasePlan plan) {
        var w = plan.wall();
        int h = w.height(), half = w.half(), cx = plan.cx(), cz = plan.cz(), y = plan.padY();
        boolean thick = plan.kind() == BaseKind.FORTRESS;
        int[][] inward = {{0, 1}, {-1, 0}, {0, -1}, {1, 0}};
        for (int side = 0; side < 4; side++) {
            for (int i = -half; i <= half; i++) {
                int x = side == 1 ? cx + half : side == 3 ? cx - half : cx + i;
                int z = side == 0 ? cz - half : side == 2 ? cz + half : cz + i;
                boolean hesco = hescoStretch(plan, i);
                wallCell(x, z, y, h, i, hesco, false);
                if (thick) wallCell(x + inward[side][0], z + inward[side][1], y, h - 1, i, hesco, true);
            }
        }
        for (int side = -1; side <= 1; side += 2) gate(plan, side, thick);
    }

    /** One column of the wall: reinforced concrete with a pilaster, glass slit, floodlight and a parapet, or hesco topped with bags. */
    private void wallCell(int x, int z, int y, int h, int i, boolean hesco, boolean inner) {
        if (hesco) {
            fill(x, y, z, x, y + h - 2, z, pal.hesco());
            set(x, y + h - 1, z, pal.bag());
            if (!inner && Math.floorMod(i, 2) == 0) set(x, y + h, z, pal.bag());
            return;
        }
        fill(x, y, z, x, y + h - 1, z, pal.heavy());
        if (inner) return;
        if (Math.floorMod(i, 6) == 0) fill(x, y, z, x, y + h, z, pal.trim());            // pilaster
        else if (Math.floorMod(i, 5) == 2) set(x, y + h - 2, z, pal.glass());           // firing slit
        else if (Math.floorMod(i, 8) == 4) set(x, y + h - 1, z, pal.light());           // floodlight in the coping
        else set(x, y + h, z, Math.floorMod(i, 2) == 0 ? pal.heavy().defaultBlockState() : pal.bars().defaultBlockState());
    }

    /** A five-wide vehicle gate in the middle of a side: steel header, red-and-white boom posts, floodlights. */
    private void gate(BasePlan plan, int side, boolean thick) {
        var w = plan.wall();
        int h = w.height(), half = w.half(), cx = plan.cx(), cz = plan.cz(), y = plan.padY();
        boolean ns = w.axis() == 0;
        int gx = ns ? 0 : side * half, gz = ns ? side * half : 0;
        int in = -side;                                           // inward step along the gate axis
        for (int k = -2; k <= 2; k++) {
            for (int d = 0; d <= (thick ? 1 : 0); d++) {
                int x = cx + gx + (ns ? k : in * d), z = cz + gz + (ns ? in * d : k);
                fill(x, y, z, x, y + h + 1, z, Blocks.AIR);
                set(x, y + h - 1, z, pal.steel());
                set(x, y - 1, z, pal.floor());                                        // paved lane
            }
        }
        for (int d = -8; d <= 10; d++) {                                                                  // the road through the gate, edged in yellow
            for (int k = -3; k <= 3; k++) {
                int x = cx + gx + (ns ? k : in * d), z = cz + gz + (ns ? in * d : k);
                if (Math.abs(k) == 3 && d > (thick ? 1 : 0) || Math.abs(k) == 3 && d < 0) set(x, y - 1, z, Blocks.YELLOW_CONCRETE);
                else if (Math.abs(k) < 3 && (d > (thick ? 1 : 0) || d < 0)) set(x, y - 1, z, d % 4 == 0 && k == 0 ? Blocks.WHITE_CONCRETE : pal.floor());
            }
        }
        for (int k = -3; k <= 3; k += 6) {
            int x = cx + gx + (ns ? k : 0), z = cz + gz + (ns ? 0 : k);
            for (int yy = y; yy <= y + h; yy++) set(x, yy, z, (yy - y) % 2 == 0 ? Blocks.RED_CONCRETE : Blocks.WHITE_CONCRETE);
            set(x, y + h + 1, z, pal.light());
        }
    }

    // ---------------------------------------------------------------- pieces

    private void piece(Piece p, BasePlan plan) {
        switch (p.type()) {
            case COMMAND -> command(p);
            case BARRACKS -> barracks(p);
            case CABIN -> cabin(p);
            case MESS -> mess(p);
            case MEDICAL -> medical(p);
            case ARMORY -> armory(p);
            case GENERATOR -> generator(p);
            case CONTAINERS -> containers(p);
            case YARD -> yard(p);
            case HELIPAD -> helipad(p);
            case CHECKPOINT -> checkpoint(p);
            case BARRIER -> barrier(p);
            case BUNKER -> bunker(p);
            case DEPOT -> depot(p);
            case TOWER -> tower(p);
            case NEST -> nest(p);
            case MAST -> mast(p);
            case BAY -> bay(p);
            case LIGHT -> light(p);
            case TRENCH -> trench(p);
        }
    }

    // ---- buildings

    /** The doorway cells (in the wall row) and the step inward, for the wall {@code facing} points at (0 north, 1 east, 2 south, 3 west). */
    private int[] doorway(Piece p, int xa, int za, int xb, int zb, int width) {
        int dx = p.x(), dz = p.z();
        return switch (p.facing() & 3) {
            case 0 -> new int[]{dx, za, width == 2 ? dx + 1 : dx, za, 0, 1};
            case 1 -> new int[]{xb, dz, xb, width == 2 ? dz + 1 : dz, -1, 0};
            case 2 -> new int[]{dx, zb, width == 2 ? dx + 1 : dx, zb, 0, -1};
            default -> new int[]{xa, dz, xa, width == 2 ? dz + 1 : dz, 1, 0};
        };
    }

    private boolean nearDoor(Piece p, int x, int z) {
        int xa = x0(p), za = z0(p), xb = xa + p.w() - 1, zb = za + p.d() - 1;
        int[] d = doorway(p, xa, za, xb, zb, 2);
        for (int k = 0; k < 2; k++) {
            int ax = d[k * 2] + d[4], az = d[k * 2 + 1] + d[5];
            if (Math.abs(x - ax) <= 1 && Math.abs(z - az) <= 1) return true;
        }
        return false;
    }

    /**
     * A flat-roofed building: paved apron, walls on a dark plinth with posts at the corners and a band in the container colour,
     * ribbon windows, a doorway with an open steel door under a canopy, a roof with a parapet, flush lamps in the ceiling, and
     * a kit of roof gear (solar panels, air handling, an antenna, a water tank) that the {@code style} picks.
     */
    private void building(Piece p, Block wallBlock, boolean windows) {
        int xa = x0(p), za = z0(p), xb = xa + p.w() - 1, zb = za + p.d() - 1, y = p.y(), h = p.height(), st = p.style();
        fill(xa - 1, y - 1, za - 1, xb + 1, y - 1, zb + 1, pal.floor());
        shell(xa, y, za, xb, y + h - 1, zb, wallBlock);
        ring(xa, y, za, xb, zb, pal.trim());                                                           // plinth
        if (((st >> 4) & 1) == 0) ring(xa, y + h - 1, za, xb, zb, accent(st));                         // colour band
        for (int[] c : new int[][]{{xa, za}, {xb, za}, {xa, zb}, {xb, zb}}) fill(c[0], y, c[1], c[0], y + h, c[1], pal.trim());
        fill(xa - 1, y + h, za - 1, xb + 1, y + h, zb + 1, pal.trim());                                // roof
        ring(xa - 1, y + h + 1, za - 1, xb + 1, zb + 1, Blocks.AIR);
        for (int x = xa - 1; x <= xb + 1; x++) {
            set(x, y + h + 1, za - 1, slab(pal.slab(), SlabType.BOTTOM));
            set(x, y + h + 1, zb + 1, slab(pal.slab(), SlabType.BOTTOM));
        }
        for (int z = za; z <= zb; z++) {
            set(xa - 1, y + h + 1, z, slab(pal.slab(), SlabType.BOTTOM));
            set(xb + 1, y + h + 1, z, slab(pal.slab(), SlabType.BOTTOM));
        }
        if (windows) {
            for (int x = xa + 2; x <= xb - 2; x += 4) {
                for (int dx = 0; dx < 2 && x + dx < xb; dx++) {
                    for (int yy = y + 1; yy <= y + 2 && yy < y + h - 1; yy++) {
                        set(x + dx, yy, za, pal.glass());
                        set(x + dx, yy, zb, pal.glass());
                    }
                }
            }
            for (int z = za + 2; z <= zb - 2; z += 4) {
                for (int yy = y + 1; yy <= y + 2 && yy < y + h - 1; yy++) {
                    set(xa, yy, z, pal.glass());
                    set(xb, yy, z, pal.glass());
                }
            }
        }
        // ceiling lamps, flush
        for (int x = xa + 2; x <= xb - 2; x += 3) {
            for (int z = za + 2; z <= zb - 2; z += 3) set(x, y + h, z, pal.light());
        }
        if (p.w() <= 3) set(p.x(), y + h, p.z(), pal.light());
        roofGear(p, xa, za, xb, zb, y + h + 1);
        door(p, xa, za, xb, zb, y);
    }

    private void roofGear(Piece p, int xa, int za, int xb, int zb, int top) {
        int kind = (p.style() >> 6) & 3;
        if (p.w() <= 3) return;
        switch (kind) {
            case 0 -> {                                                                              // a solar array
                for (int x = xa + 1; x < xb; x += 2) for (int z = za + 1; z < zb; z += 2) set(x, top, z, Blocks.DAYLIGHT_DETECTOR);
            }
            case 1 -> {                                                                              // air handling and an antenna
                set(xa + 1, top, za + 1, pal.steel());
                set(xa + 1, top + 1, za + 1, Blocks.IRON_TRAPDOOR);
                set(xb - 1, top, zb - 1, pal.steel());
                set(xb - 1, top + 1, zb - 1, Blocks.IRON_TRAPDOOR);
                set(p.x(), top, p.z(), Blocks.IRON_BARS);
                fill(p.x(), top + 1, p.z(), p.x(), top + 3, p.z(), Blocks.IRON_BARS);
                set(p.x(), top + 4, p.z(), Blocks.LIGHTNING_ROD);
            }
            case 2 -> {                                                                              // a water tank on a stand
                set(xb - 1, top, za + 1, pal.steel());
                set(xb - 1, top + 1, za + 1, Blocks.BARREL);
                set(xb - 2, top, za + 1, pal.steel());
                set(xb - 2, top + 1, za + 1, Blocks.BARREL);
                set(xa + 1, top, zb - 1, Blocks.IRON_TRAPDOOR);
            }
            default -> {
                set(p.x(), top, p.z(), Blocks.LIGHTNING_ROD);
            }
        }
    }

    /** A doorway one or two wide and two high, an open steel door in it, and a slab canopy on two posts out front. */
    private void door(Piece p, int xa, int za, int xb, int zb, int y) {
        int len = (p.facing() & 1) == 0 ? p.w() : p.d();
        int[] d = doorway(p, xa, za, xb, zb, len >= 5 ? 2 : 1);
        Direction out = dir(p.facing());
        for (int k = 0; k < 2; k++) {
            int x = d[k * 2], z = d[k * 2 + 1];
            fill(x, y, z, x, y + 1, z, Blocks.AIR);
            if (k == 0) ironDoor(x, y, z, out);
            if (d[0] == d[2] && d[1] == d[3]) break;
        }
        // canopy
        int ox = out.getStepX(), oz = out.getStepZ();
        for (int k = 0; k < 2; k++) {
            int x = d[k * 2], z = d[k * 2 + 1];
            for (int step = 1; step <= 2; step++) {
                if (y + 3 < y + p.height()) {
                    set(x + ox * step, y + 3, z + oz * step, slab(pal.slab(), SlabType.BOTTOM));
                }
            }
            if (d[0] == d[2] && d[1] == d[3]) break;
        }
        int sx = d[2] - d[0] != 0 ? 1 : 0, sz = d[3] - d[1] != 0 ? 1 : 0;
        if (p.height() >= 4) {
            for (int side = 0; side < 2; side++) {
                int x = (side == 0 ? d[0] - sx : d[2] + sx) + ox * 2 + (sx == 0 ? (side == 0 ? -1 : 1) : 0);
                int z = (side == 0 ? d[1] - sz : d[3] + sz) + oz * 2 + (sz == 0 ? (side == 0 ? -1 : 1) : 0);
                fill(x, y, z, x, y + 2, z, Blocks.IRON_BARS);
                set(x, y + 3, z, slab(pal.slab(), SlabType.BOTTOM));
            }
        }
    }

    /** The cells just inside {@code side} of a building: where furniture stands against that wall, corners left free. */
    private List<int[]> edge(Piece p, Direction side) {
        int xa = x0(p), za = z0(p), xb = xa + p.w() - 1, zb = za + p.d() - 1;
        List<int[]> out = new ArrayList<>();
        switch (side) {
            case NORTH -> {
                for (int x = xa + 2; x <= xb - 2; x++) out.add(new int[]{x, za + 1});
            }
            case SOUTH -> {
                for (int x = xa + 2; x <= xb - 2; x++) out.add(new int[]{x, zb - 1});
            }
            case WEST -> {
                for (int z = za + 2; z <= zb - 2; z++) out.add(new int[]{xa + 1, z});
            }
            default -> {
                for (int z = za + 2; z <= zb - 2; z++) out.add(new int[]{xb - 1, z});
            }
        }
        out.removeIf(c -> nearDoor(p, c[0], c[1]));
        return out;
    }

    /** The wall opposite the door, whose cells hold the furniture that wants a quiet place. */
    private Direction back(Piece p) {
        return dir(p.facing() + 2);
    }

    /** Looks across the room from {@code side}. */
    private Direction into(Direction side) {
        return side.getOpposite();
    }

    private Block bodyFor(Piece p) {
        return switch (Math.floorMod(p.style(), 3)) {
            case 1 -> accent(p.style() >> 2);
            case 2 -> pal.heavy();
            default -> pal.body();
        };
    }

    /** The operations room: a map table, a briefing lectern, a radio, a workbench and stock chests; the contract's cache goes in at {@code (xa+2, za+2)}. */
    private void command(Piece p) {
        building(p, pal.body(), true);
        int xa = x0(p), za = z0(p), xb = xa + p.w() - 1, zb = za + p.d() - 1, y = p.y();
        Direction back = back(p);
        List<int[]> row = edge(p, back);
        Direction face = into(back);
        int n = row.size();
        for (int i = 0; i < n; i++) {
            int[] c = row.get(i);
            if (c[0] == xa + 2 && c[1] == za + 2) continue;                                           // the cache
            Block b = switch (i % 5) {
                case 0 -> Blocks.CARTOGRAPHY_TABLE;
                case 1 -> Blocks.LECTERN;
                case 2 -> Blocks.JUKEBOX;                                                              // the radio
                case 3 -> Blocks.CRAFTING_TABLE;
                default -> Blocks.CHEST;
            };
            set(c[0], y, c[1], b, face);
        }
        for (Direction side : new Direction[]{back.getClockWise(), back.getCounterClockWise()}) {
            List<int[]> col = edge(p, side);
            for (int i = 0; i < col.size(); i++) {
                int[] c = col.get(i);
                if (c[0] == xa + 2 && c[1] == za + 2) continue;
                set(c[0], y, c[1], i % 3 == 0 ? Blocks.CHEST : i % 3 == 1 ? Blocks.BARREL : Blocks.SMOOTH_STONE_SLAB, into(side));
            }
        }
        if (p.w() >= 9) {                                                                              // a conference table: slabs with benches
            int tx = p.x(), tz = p.z();
            for (int dx = -1; dx <= 1; dx++) {
                if (tx + dx == xa + 2 && tz == za + 2) continue;
                set(tx + dx, y, tz, slab(Blocks.SMOOTH_STONE_SLAB, SlabType.BOTTOM));
                set(tx + dx, y, tz - 1, Blocks.POLISHED_ANDESITE_STAIRS, Direction.SOUTH);
                set(tx + dx, y, tz + 1, Blocks.POLISHED_ANDESITE_STAIRS, Direction.NORTH);
            }
        }
        // the cache cell and the strip beside it stay clear for the contract's barrel
    }

    /** Bunks: a row of beds along each long wall, a locker between each pair; a steel door and a bench. */
    private void barracks(Piece p) {
        building(p, bodyFor(p), true);
        int xa = x0(p), za = z0(p), xb = xa + p.w() - 1, zb = za + p.d() - 1, y = p.y();
        boolean alongX = p.w() >= p.d();
        if (alongX) {
            for (int x = xa + 1; x <= xb - 3; x += 3) {
                for (int side = 0; side < 2; side++) {
                    int z = side == 0 ? za + 1 : zb - 1;
                    if (nearDoor(p, x, z) || nearDoor(p, x + 1, z)) continue;
                    bed(x, y, z, Direction.EAST, pal.bed());
                    set(x + 2, y, z, Blocks.BARREL);
                }
            }
            set(xb - 1, y, p.z(), Blocks.CRAFTING_TABLE);
        } else {
            for (int z = za + 1; z <= zb - 3; z += 3) {
                for (int side = 0; side < 2; side++) {
                    int x = side == 0 ? xa + 1 : xb - 1;
                    if (nearDoor(p, x, z) || nearDoor(p, x, z + 1)) continue;
                    bed(x, y, z, Direction.SOUTH, pal.bed());
                    set(x, y, z + 2, Blocks.BARREL);
                }
            }
        }
    }

    /** A prefab cabin: a one-room container module with a bunk, a workbench and stores. */
    private void cabin(Piece p) {
        Piece small = new Piece(p.type(), p.x(), p.y(), p.z(), p.w(), p.d(), p.facing(), 3, p.style());
        building(small, accent(p.style()), false);
        int xa = x0(p), za = z0(p), xb = xa + p.w() - 1, zb = za + p.d() - 1, y = p.y();
        for (int x = xa + 1; x < xb - 1; x++) if (!nearDoor(small, x, zb - 1)) { bed(x, y, zb - 1, Direction.EAST, pal.bed()); break; }
        set(xa + 1, y, za + 1, Blocks.CRAFTING_TABLE);
        set(xb - 1, y, za + 1, Blocks.BARREL);
        set(xb - 1, y, zb - 1, Blocks.CHEST, Direction.WEST);
    }

    /** Smokers and a furnace on a counter, a sink, a cold store; long tables with benches. */
    private void mess(Piece p) {
        building(p, bodyFor(p), true);
        int y = p.y();
        Direction back = back(p);
        List<int[]> row = edge(p, back);
        for (int i = 0; i < row.size(); i++) {
            int[] c = row.get(i);
            Block b = switch (i % 5) {
                case 0 -> Blocks.SMOKER;
                case 1 -> Blocks.FURNACE;
                case 2 -> Blocks.CAULDRON;
                case 3 -> Blocks.BARREL;
                default -> Blocks.BLAST_FURNACE;
            };
            set(c[0], y, c[1], b, into(back));
        }
        boolean alongX = p.w() >= p.d();
        int cx = p.x(), cz = p.z();
        for (int k = -2; k <= 2 && Math.min(p.w(), p.d()) >= 8; k++) {
            int x = alongX ? cx + k : cx, z = alongX ? cz : cz + k;
            if (nearDoor(p, x, z)) continue;
            set(x, y, z, slab(Blocks.SMOOTH_STONE_SLAB, SlabType.BOTTOM));
            Direction a = alongX ? Direction.SOUTH : Direction.EAST, b2 = a.getOpposite();
            set(x + (alongX ? 0 : -1), y, z + (alongX ? -1 : 0), Blocks.POLISHED_ANDESITE_STAIRS, a);
            set(x + (alongX ? 0 : 1), y, z + (alongX ? 1 : 0), Blocks.POLISHED_ANDESITE_STAIRS, b2);
        }
    }

    /** White concrete with a red cross on the side wall: beds, a brewing stand and a sink, supply chests. */
    private void medical(Piece p) {
        Piece white = new Piece(p.type(), p.x(), p.y(), p.z(), p.w(), p.d(), p.facing(), p.height(), p.style() | 0x10);
        building(white, Blocks.WHITE_CONCRETE, true);
        int xa = x0(p), za = z0(p), xb = xa + p.w() - 1, zb = za + p.d() - 1, y = p.y();
        // the cross, on the wall to the left of the door
        Direction left = dir(p.facing()).getCounterClockWise();
        int wx = left == Direction.WEST ? xa : left == Direction.EAST ? xb : p.x();
        int wz = left == Direction.NORTH ? za : left == Direction.SOUTH ? zb : p.z();
        int ax = left.getAxis() == Direction.Axis.X ? 0 : 1, az = left.getAxis() == Direction.Axis.X ? 1 : 0;
        for (int k = -1; k <= 1; k++) {
            set(wx + ax * k, y + 2, wz + az * k, Blocks.RED_CONCRETE);
            if (k != 0) set(wx, y + 2 + k, wz, Blocks.RED_CONCRETE);
        }
        Direction back = back(p);
        List<int[]> row = edge(p, back);
        for (int i = 0; i < row.size(); i++) {
            int[] c = row.get(i);
            Block b = switch (i % 4) {
                case 0 -> Blocks.BREWING_STAND;
                case 1 -> Blocks.CAULDRON;
                case 2 -> Blocks.CHEST;
                default -> Blocks.BARREL;
            };
            set(c[0], y, c[1], b, into(back));
        }
        boolean alongX = p.w() >= p.d();
        for (int k = 0; k < 3; k++) {
            int x = alongX ? xa + 1 + k * 3 : xa + 1, z = alongX ? zb - 1 : za + 1 + k * 3;
            if (nearDoor(p, x, z)) continue;
            bed(x, y, z, alongX ? Direction.EAST : Direction.SOUTH, Blocks.WHITE_BED);
        }
    }

    /** Heavy walls, no windows, a steel door: weapon stores, a grinder, an anvil, a repair bench. */
    private void armory(Piece p) {
        building(p, pal.heavy(), false);
        int y = p.y();
        Direction back = back(p);
        List<int[]> row = edge(p, back);
        for (int i = 0; i < row.size(); i++) {
            int[] c = row.get(i);
            Block b = switch (i % 5) {
                case 0 -> Blocks.CHEST;
                case 1 -> Blocks.GRINDSTONE;
                case 2 -> Blocks.ANVIL;
                case 3 -> Blocks.SMITHING_TABLE;
                default -> Blocks.CHEST;
            };
            set(c[0], y, c[1], b, into(back));
        }
        for (Direction side : new Direction[]{back.getClockWise(), back.getCounterClockWise()}) {
            for (int[] c : edge(p, side)) set(c[0], y, c[1], Blocks.BARREL);
        }
    }

    /** An open steel-framed shed round two generator sets, with a transformer, exhaust, a fuel point and a solar row. */
    private void generator(Piece p) {
        int xa = x0(p), za = z0(p), xb = xa + p.w() - 1, zb = za + p.d() - 1, y = p.y();
        fill(xa - 1, y - 1, za - 1, xb + 1, y - 1, zb + 1, pal.floor());
        for (int[] c : new int[][]{{xa, za}, {xb, za}, {xa, zb}, {xb, zb}}) fill(c[0], y, c[1], c[0], y + 2, c[1], pal.steel());
        fill(xa, y + 3, za, xb, y + 3, zb, pal.trim());
        for (int x = xa + 1; x < xb; x++) {                                                            // mesh sides at the back and ends
            fill(x, y, za, x, y + 2, za, Blocks.IRON_BARS);
        }
        for (int z = za + 1; z < zb; z++) {
            fill(xa, y, z, xa, y + 2, z, Blocks.IRON_BARS);
            fill(xb, y, z, xb, y + 2, z, Blocks.IRON_BARS);
        }
        Direction out = Direction.SOUTH;
        set(xa + 1, y, za + 1, Blocks.BLAST_FURNACE, out);
        set(xa + 2, y, za + 1, Blocks.BLAST_FURNACE, out);
        set(xa + 1, y + 1, za + 1, Blocks.HOPPER);
        set(xb - 1, y, za + 1, Blocks.COPPER_BLOCK);                                                   // transformer
        set(xb - 1, y + 1, za + 1, Blocks.LIGHTNING_ROD);
        set(xb - 1, y, zb - 1, Blocks.BARREL);
        set(xb - 2, y, zb - 1, Blocks.BARREL);
        set(p.x(), y + 4, za + 1, Blocks.IRON_BARS);                                                   // exhaust stack
        fill(p.x(), y + 4, za + 1, p.x(), y + 6, za + 1, Blocks.IRON_BARS);
        set(p.x(), y + 3, p.z(), pal.light());
        for (int x = xa + 1; x < xb; x++) set(x, y + 4, p.z(), Blocks.DAYLIGHT_DETECTOR);
    }

    /** Shipping containers, some stacked, some open at one end with stores inside. */
    private void containers(Piece p) {
        int xa = x0(p), za = z0(p), y = p.y(), st = p.style();
        fill(xa - 1, y - 1, za - 1, xa + p.w(), y - 1, za + p.d(), pal.floor());
        int len = Math.min(7, p.w());
        container(xa, y, za, len, accent(st), true);
        container(xa + (p.w() - len), y, za + 4, len, accent(st + 1), false);
        if (((st >> 3) & 1) == 0) container(xa + 1, y + 3, za, len - 1, accent(st + 2), false);        // a stack
        else container(xa + (p.w() - len) + 1, y + 3, za + 4, len - 1, accent(st + 2), true);
        set(xa + len + 1, y, za + 1, Blocks.BARREL);
    }

    /** A container: 3 wide, 3 high, hollow, open at one end. Frame in dark steel-grey, body in {@code col}. */
    private void container(int x0, int y0, int z0, int len, Block col, boolean openWest) {
        fill(x0, y0, z0, x0 + len - 1, y0 + 2, z0 + 2, col);
        fill(x0 + 1, y0, z0 + 1, x0 + len - 2, y0 + 1, z0 + 1, Blocks.AIR);
        for (int[] c : new int[][]{{x0, z0}, {x0 + len - 1, z0}, {x0, z0 + 2}, {x0 + len - 1, z0 + 2}}) fill(c[0], y0, c[1], c[0], y0 + 2, c[1], pal.trim());
        ring(x0, y0 + 2, z0, x0 + len - 1, z0 + 2, pal.trim());
        int ex = openWest ? x0 : x0 + len - 1;
        fill(ex, y0, z0 + 1, ex, y0 + 1, z0 + 1, Blocks.AIR);
        int sx = openWest ? x0 + 1 : x0 + len - 2;
        set(sx, y0, z0 + 1, Blocks.CHEST, openWest ? Direction.EAST : Direction.WEST);
        if (len > 4) set(openWest ? sx + 1 : sx - 1, y0, z0 + 1, Blocks.BARREL);
    }

    /** A motor pool and workshop: a paved pad with hazard edging, a workbench row, drums and a tool store. Scales down to a 3x3 field yard. */
    private void yard(Piece p) {
        int xa = x0(p), za = z0(p), xb = xa + p.w() - 1, zb = za + p.d() - 1, y = p.y();
        fill(xa, y - 1, za, xb, y - 1, zb, pal.floor());
        if (p.w() >= 5) ring(xa, y - 1, za, xb, zb, Blocks.YELLOW_CONCRETE);
        if (p.w() < 5) {                                                                                 // a field yard: a bench, an anvil, a drum, a lamp post
            set(xa, y, za, Blocks.CRAFTING_TABLE);
            set(xb, y, za, Blocks.ANVIL, Direction.EAST);
            set(xb, y, zb, Blocks.BARREL);
            set(p.x(), y, p.z(), Blocks.IRON_BARS);
            fill(p.x(), y + 1, p.z(), p.x(), y + 2, p.z(), Blocks.IRON_BARS);
            set(p.x(), y + 3, p.z(), pal.light());
            return;
        }
        set(xa + 1, y, za + 1, Blocks.CRAFTING_TABLE);
        set(xb - 1, y, za + 1, Blocks.ANVIL, Direction.EAST);
        set(xb - 1, y, zb - 1, Blocks.BARREL);
        if (p.w() >= 5) {
            set(xa + 2, y, za + 1, Blocks.GRINDSTONE, Direction.SOUTH);
            set(xa + 1, y, zb - 1, Blocks.CHEST, Direction.EAST);
            set(xb - 2, y, zb - 1, Blocks.BARREL);
            set(xb - 1, y, zb - 2, Blocks.BARREL);
            if (p.w() >= 7) {
                fill(p.x(), y, p.z(), p.x(), y + 2, p.z(), pal.steel());                               // a work light
                set(p.x(), y + 3, p.z(), pal.light());
            }
        }
    }

    /** A landing pad: light concrete, yellow ring, white H, corner lamps. No walls; helicopters want the sky. */
    private void helipad(Piece p) {
        int y = p.y() - 1, r = p.w() / 2;
        for (int dx = -r; dx <= r; dx++) {
            for (int dz = -r; dz <= r; dz++) {
                boolean edge = Math.max(Math.abs(dx), Math.abs(dz)) == r;
                boolean h = (Math.abs(dx) == 2 && Math.abs(dz) <= 2) || (dz == 0 && Math.abs(dx) <= 2);
                boolean ring = Math.abs(Math.hypot(dx, dz) - (r - 1)) < 0.6;
                Block b = h ? Blocks.WHITE_CONCRETE : ring ? Blocks.YELLOW_CONCRETE : edge ? Blocks.GRAY_CONCRETE : Blocks.LIGHT_GRAY_CONCRETE;
                set(p.x() + dx, y, p.z() + dz, b);
            }
        }
        for (int[] c : new int[][]{{-r, -r}, {r, -r}, {-r, r}, {r, r}}) set(p.x() + c[0], y, p.z() + c[1], pal.light());
    }

    /** A guard booth at a gate: glass on three sides, an open steel door onto the lane, a desk inside, a flush lamp. */
    private void checkpoint(Piece p) {
        building(new Piece(p.type(), p.x(), p.y(), p.z(), 3, 3, p.facing(), 4, p.style() | 0x10), pal.body(), true);
        for (Direction d : DIRS) {
            if (d == dir(p.facing())) continue;
            int gx = p.x() + d.getStepX(), gz = p.z() + d.getStepZ();
            set(gx, p.y() + 1, gz, pal.glass());
            set(gx, p.y() + 2, gz, pal.glass());
        }
        set(p.x(), p.y(), p.z(), Blocks.LECTERN, dir(p.facing()));
    }

    /** A wing of hesco or jersey barriers running out from a gate, so that an approach is a funnel. */
    private void barrier(Piece p) {
        boolean alongZ = p.facing() == 0;
        for (int i = -3; i <= 3; i++) {
            int x = p.x() + (alongZ ? 0 : i), z = p.z() + (alongZ ? i : 0);
            set(x, p.y() - 1, z, pal.ground());
            if ((p.style() & 1) == 0) {
                fill(x, p.y(), z, x, p.y() + 1, z, pal.hesco());
            } else {
                set(x, p.y(), z, pal.heavy());
                set(x, p.y() + 1, z, i % 2 == 0 ? Blocks.RED_CONCRETE : Blocks.WHITE_CONCRETE);
            }
        }
    }

    /** Heavy walls and roof, firing slits, a steel door. */
    private void bunker(Piece p) {
        int xa = x0(p), za = z0(p), xb = xa + p.w() - 1, zb = za + p.d() - 1, y = p.y();
        fill(xa - 1, y - 1, za - 1, xb + 1, y - 1, zb + 1, pal.floor());
        fill(xa - 1, y, za - 1, xb + 1, y + 2, zb + 1, pal.heavy());
        fill(xa + 1, y, za + 1, xb - 1, y + 1, zb - 1, Blocks.AIR);
        fill(xa - 1, y + 3, za - 1, xb + 1, y + 3, zb + 1, pal.heavy());
        for (int x = xa + 1; x < xb; x += 2) {
            set(x, y + 1, za - 1, Blocks.AIR);                                                          // firing slits
            set(x, y + 1, zb + 1, Blocks.AIR);
        }
        Piece big = new Piece(p.type(), p.x(), p.y(), p.z(), p.w() + 2, p.d() + 2, p.facing(), p.height(), p.style());
        int[] d = doorway(big, xa - 1, za - 1, xb + 1, zb + 1, 1);
        fill(d[0], y, d[1], d[0], y + 1, d[1], Blocks.AIR);
        ironDoor(d[0], y, d[1], dir(p.facing()));
        set(p.x(), y + 1, p.z(), pal.light());
        set(xa + 1, y, za + 1, Blocks.CHEST, Direction.EAST);
        set(xb - 1, y, zb - 1, Blocks.BARREL);
        set(xa + 1, y, zb - 1, Blocks.CRAFTING_TABLE);
    }

    /** A depot: an open steel-roofed warehouse on pallets, a pair of containers, or a hesco-walled ammunition yard. */
    private void depot(Piece p) {
        int xa = x0(p), za = z0(p), xb = xa + p.w() - 1, zb = za + p.d() - 1, y = p.y();
        fill(xa - 1, y - 1, za - 1, xb + 1, y - 1, zb + 1, pal.floor());
        switch (Math.floorMod(p.style(), 3)) {
            case 0 -> {
                for (int[] c : new int[][]{{xa, za}, {xb, za}, {xa, zb}, {xb, zb}}) fill(c[0], y, c[1], c[0], y + 2, c[1], pal.steel());
                fill(xa, y + 3, za, xb, y + 3, zb, pal.trim());
                for (int x = xa + 1; x < xb; x++) for (int z = za + 1; z < zb; z++) stock(x, y, z, 7);
                set(p.x(), y + 3, p.z(), pal.light());
            }
            case 1 -> {
                container(xa, y, za, Math.min(7, p.w()), accent(p.style()), true);
                for (int x = xa; x <= xb; x += 2) stock(x, y, zb, 4);
                set(xb, y, zb - 1, Blocks.CHEST, Direction.WEST);
            }
            default -> {
                ring(xa, y, za, xb, zb, pal.hesco());
                ring(xa, y + 1, za, xb, zb, pal.hesco());
                int[] d = doorway(new Piece(p.type(), p.x(), p.y(), p.z(), p.w(), p.d(), p.facing(), p.height(), p.style()), xa, za, xb, zb, 1);
                fill(d[0], y, d[1], d[0], y + 1, d[1], Blocks.AIR);
                for (int x = xa + 1; x < xb; x++) for (int z = za + 1; z < zb; z++) stock(x, y, z, 6);
            }
        }
    }

    /** A crate, a drum or a pallet: what a store is made of. {@code odds} is the chance (1 in N) a cell is left empty. */
    private void stock(int x, int y, int z, int odds) {
        int roll = rng.nextInt(odds);
        if (roll == 0) set(x, y, z, Blocks.BARREL);
        else if (roll == 1) set(x, y, z, Blocks.CHEST);
        else if (roll == 2) set(x, y, z, slab(Blocks.SMOOTH_STONE_SLAB, SlabType.BOTTOM));
    }

    // ---- structures

    /** A watchtower: a concrete shaft or a steel-framed one, a ladder inside, firing slits, and a roofed platform with a sandbag or concrete parapet and a searchlight. */
    private void tower(Piece p) {
        int s = p.w(), xa = x0(p), za = z0(p), xb = xa + s - 1, zb = za + s - 1, y = p.y(), h = p.height();
        boolean steel = (p.style() & 1) == 1;
        fill(xa, y - 1, za, xb, y - 1, zb, pal.heavy());
        shell(xa, y, za, xb, y + h - 1, zb, steel ? pal.trim() : pal.heavy());
        if (steel) {                                                                                    // an open lattice, steel at the corners, solid on the ladder side
            for (int x = xa + 1; x < xb; x++) {
                fill(x, y + 1, za, x, y + h - 2, za, Blocks.IRON_BARS);
                fill(x, y + 1, zb, x, y + h - 2, zb, Blocks.IRON_BARS);
            }
            for (int z = za + 1; z < zb; z++) {
                fill(xa, y + 1, z, xa, y + h - 2, z, Blocks.IRON_BARS);
                fill(xb, y + 1, z, xb, y + h - 2, z, Blocks.IRON_BARS);
            }
            fill(xa + 1, y + 1, zb, xb - 1, y + h - 2, zb, pal.trim());
        }
        for (int[] c : new int[][]{{xa, za}, {xb, za}, {xa, zb}, {xb, zb}}) fill(c[0], y, c[1], c[0], y + h + 3, c[1], pal.steel());
        fill(xa, y + h - 1, za, xb, y + h - 1, zb, steel ? pal.steel() : pal.floor());                 // the platform
        ring(xa, y + h, za, xb, zb, steel ? pal.bag() : pal.heavy());                                  // parapet
        for (int[] c : new int[][]{{xa, za}, {xb, za}, {xa, zb}, {xb, zb}}) fill(c[0], y + h, c[1], c[0], y + h + 3, c[1], pal.steel());
        for (int x = xa + 1; x < xb; x++) {                                                             // a firing slot over the parapet, glass over that
            for (int yy = y + h + 2; yy <= y + h + 3; yy++) {
                set(x, yy, za, pal.glass());
                set(x, yy, zb, pal.glass());
            }
        }
        for (int z = za + 1; z < zb; z++) {
            for (int yy = y + h + 2; yy <= y + h + 3; yy++) {
                set(xa, yy, z, pal.glass());
                set(xb, yy, z, pal.glass());
            }
        }
        // the roof is five above the platform: a soldier's post finds the highest floor within four, and it must find the platform, not the roof
        fill(xa, y + h + 4, za, xb, y + h + 4, zb, pal.trim());
        set(p.x(), y + h + 4, p.z(), pal.light());                                                      // the searchlight
        int lx = s > 3 ? xa + 1 : p.x(), lz = zb - 1;
        for (int yy = y; yy < y + h; yy++) set(lx, yy, lz, Blocks.LADDER.defaultBlockState().setValue(LadderBlock.FACING, Direction.NORTH));
        if (s > 3) set(lx, y + h - 1, lz, Blocks.AIR);
        if (!steel) for (int yy = y + 2; yy < y + h - 2; yy += 3) set(p.x(), yy, za, pal.glass());
        fill(p.x(), y, zb, p.x(), y + 1, zb, Blocks.AIR);                                               // a door at the foot
        ironDoor(p.x(), y, zb, Direction.SOUTH);
        if (s > 3) set(p.x() + 1, y, zb - 1, Blocks.CHEST, Direction.NORTH);
    }

    /** A firing position facing {@code facing}: sandbags, hesco with a bag top, or a low concrete pillbox with a slit; an ammunition drum behind. */
    private void nest(Piece p) {
        int y = p.y();
        Direction out = dir(p.facing());
        Direction side = out.getClockWise();
        int kind = Math.floorMod(p.style(), 3);
        for (int a = -2; a <= 2; a++) {
            for (int depth = 0; depth <= 2; depth++) {
                boolean rim = Math.abs(a) == 2 || depth == 2;
                if (!rim) continue;
                int x = p.x() + side.getStepX() * a + out.getStepX() * (depth - 1), z = p.z() + side.getStepZ() * a + out.getStepZ() * (depth - 1);
                set(x, y - 1, z, kind == 2 ? pal.floor() : pal.ground());
                boolean notch = depth == 2 && a == 0;
                switch (kind) {
                    case 0 -> {
                        set(x, y, z, pal.bag());
                        if (!notch) set(x, y + 1, z, pal.bag());
                    }
                    case 1 -> {
                        set(x, y, z, pal.hesco());
                        if (!notch) set(x, y + 1, z, pal.hesco());
                        set(x, y + 2, z, pal.bag());
                    }
                    default -> {
                        set(x, y, z, pal.heavy());
                        if (!notch) set(x, y + 1, z, pal.heavy());
                    }
                }
            }
        }
        int bx = p.x() - out.getStepX() * 2, bz = p.z() - out.getStepZ() * 2;
        set(bx, y, bz, Blocks.BARREL);
    }

    /** A communications mast: a steel mast with crossbars of rods, a cabinet at its foot, a solar cell. */
    private void mast(Piece p) {
        int y = p.y(), h = p.height();
        fill(p.x() - 1, y - 1, p.z() - 1, p.x() + 1, y - 1, p.z() + 1, pal.floor());
        fill(p.x(), y, p.z(), p.x(), y + 2, p.z(), pal.steel());
        fill(p.x(), y + 3, p.z(), p.x(), y + h - 2, p.z(), Blocks.IRON_BARS);
        for (int yy = y + 5; yy < y + h - 1; yy += 3) {                                                  // dipoles
            set(p.x() + 1, yy, p.z(), Blocks.END_ROD.defaultBlockState().setValue(BlockStateProperties.FACING, Direction.EAST));
            set(p.x() - 1, yy, p.z(), Blocks.END_ROD.defaultBlockState().setValue(BlockStateProperties.FACING, Direction.WEST));
        }
        set(p.x(), y + h - 1, p.z(), Blocks.LIGHTNING_ROD);
        set(p.x() + 1, y, p.z(), pal.steel());                                                           // the cabinet
        set(p.x() + 1, y + 1, p.z(), Blocks.IRON_TRAPDOOR);
        set(p.x(), y, p.z() + 1, Blocks.DAYLIGHT_DETECTOR);
    }

    /** A floodlight on a steel pole. */
    private void light(Piece p) {
        fill(p.x(), p.y(), p.z(), p.x(), p.y() + p.height() - 2, p.z(), Blocks.IRON_BARS);
        set(p.x(), p.y() + p.height() - 1, p.z(), pal.light());
        set(p.x(), p.y() + p.height(), p.z(), Blocks.IRON_TRAPDOOR);                                      // the shade
    }

    /** A vehicle bay: a paved hard-stand with yellow marking, a heavy back wall with floodlights, and hesco or concrete side walls; open at the front and the top so any vehicle clears. */
    private void bay(Piece p) {
        int xa = x0(p), za = z0(p), xb = xa + p.w() - 1, zb = za + p.d() - 1, y = p.y();
        fill(xa, y - 1, za, xb, y - 1, zb, pal.floor());
        ring(xa, y - 1, za, xb, zb, Blocks.YELLOW_CONCRETE);
        for (int[] c : new int[][]{{xa, za}, {xb, za}, {xa, zb}, {xb, zb}}) fill(c[0], y, c[1], c[0], y + p.height(), c[1], pal.steel());
        fill(xa, y, za, xb, y + 2, za, pal.heavy());                                                     // the back wall
        for (int z = za + 1; z < zb; z++) {
            Block b = (p.style() & 1) == 0 ? pal.hesco() : pal.heavy();
            fill(xa, y, z, xa, y + 1, z, b);
            fill(xb, y, z, xb, y + 1, z, b);
        }
        for (int x = xa + 1; x < xb; x += 4) set(x, y + 3, za, pal.light());
    }

    /** A trench: two wide, two deep, floored with concrete slab, the lip lined with bags and hesco in turn. */
    private void trench(Piece p) {
        boolean alongX = (p.facing() & 1) == 0;
        int len = p.w(), wid = p.d(), y = p.y();
        for (int i = -len / 2; i <= len / 2; i++) {
            for (int j = 0; j < wid; j++) {
                int x = p.x() + (alongX ? i : j), z = p.z() + (alongX ? j : i);
                fill(x, y - 3, z, x, y + 1, z, Blocks.AIR);
                set(x, y - 3, z, slab(Blocks.SMOOTH_STONE_SLAB, SlabType.TOP));
                set(x, y - 4, z, Blocks.DIRT);
            }
            int lx = p.x() + (alongX ? i : -1), lz = p.z() + (alongX ? -1 : i);
            int rx = p.x() + (alongX ? i : wid), rz = p.z() + (alongX ? wid : i);
            Block lip = Math.floorMod(i / 3, 2) == 0 ? pal.bag() : pal.hesco();
            set(lx, y, lz, lip);
            set(rx, y, rz, lip);
        }
        int bx = p.x() + (alongX ? 0 : 0), bz = p.z() + (alongX ? 0 : 0);
        set(bx, y - 2, bz, Blocks.BARREL);                                                                // an ammunition drum on the floor
    }
}
