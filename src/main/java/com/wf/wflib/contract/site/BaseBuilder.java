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
    /** The one kind of roof gear this base's buildings carry (a base reads as one hand's work). */
    private int gear;
    /** Blocks whose shape depends on their neighbours (bars, fences, stairs): re-shaped once everything stands. */
    private final List<BlockPos> shaped = new ArrayList<>();
    /** The plan's paved rectangles, so a road's curb stops where another road crosses it. */
    private final List<Piece> roads = new ArrayList<>();

    private BaseBuilder(ServerLevel level, Palette pal, RandomSource rng) {
        this.level = level;
        this.pal = pal;
        this.rng = rng;
    }

    public static void build(ServerLevel level, BasePlan plan, Palette pal, long seed) {
        BaseBuilder b = new BaseBuilder(level, pal, RandomSource.create(seed));
        b.gear = (int) Math.floorMod(seed, 4L);
        b.load(plan);
        b.grade(plan);
        for (Piece p : plan.pieces()) if (p.type() == BasePlan.Type.ROAD) b.roads.add(p);
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
                double d = plan.wall() != null ? Math.max(Math.abs(dx), Math.abs(dz)) : Math.sqrt(dx * dx + dz * dz);   // a walled base is square
                if (d > r + APRON) continue;
                int x = plan.cx() + dx, z = plan.cz() + dz;
                int natural = level.getHeight(Heightmap.Types.OCEAN_FLOOR_WG, x, z);       // first free block over the real ground
                int target = d <= r ? plan.padY() : plan.padY() + (int) Math.round((natural - plan.padY()) * ((d - r) / APRON));
                boolean paved = plan.wall() != null && pal.flag("paved") && Math.abs(dx) < plan.wall().half() && Math.abs(dz) < plan.wall().half();
                setColumn(x, z, natural, target, paved ? pal.floor() : pal.ground());
            }
        }
    }

    /** Makes the ground in a column end at {@code target} (the first free block): fills up or cuts down, then clears what stood above. */
    private void setColumn(int x, int z, int natural, int target, Block surface) {
        for (int y = Math.min(natural, target); y < target; y++) set(x, y, z, y == target - 1 ? surface.defaultBlockState() : Blocks.DIRT.defaultBlockState());
        if ((target - 1 >= natural || surface != pal.ground()) && target - 1 > level.getMinBuildHeight()) set(x, target - 1, z, surface.defaultBlockState());   // a paved compound replaces the turf too
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
        if (b instanceof IronBarsBlock || b instanceof FenceBlock || b instanceof WallBlock || b instanceof StairBlock
                || s.hasProperty(BlockStateProperties.NORTH) && s.hasProperty(BlockStateProperties.EAST)) shaped.add(new BlockPos(x, y, z));   // a modded fence joins up the same way
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

    /**
     * {@code upright} turned to lie along {@code axis}, for a lattice that has an orientation. NTM's scaffold names it by the face it was placed on:
     * {@code horizontal_*} stands upright, {@code vertical_east_west} / {@code vertical_north_south} lie along x / z. The block as it is when it has none.
     */
    private static BlockState lying(BlockState upright, Direction.Axis axis) {
        var prop = upright.getBlock().getStateDefinition().getProperty("orient");
        if (prop == null) return upright;
        return withValue(upright, prop, axis == Direction.Axis.X ? "vertical_east_west" : "vertical_north_south");
    }

    private static <T extends Comparable<T>> BlockState withValue(BlockState s, net.minecraft.world.level.block.state.properties.Property<T> prop, String name) {
        return prop.getValue(name).map(v -> s.setValue(prop, v)).orElse(s);
    }

    /** A light fixture hanging from what is above it (a post's shade, a roof): the style's lamp, else the flush light block. */
    private BlockState lamp() {
        return pal.part("lamp", pal.light());
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

    private void wall(BasePlan plan) {
        var w = plan.wall();
        int h = w.height(), half = w.half(), cx = plan.cx(), cz = plan.cz(), y = plan.padY();
        boolean thick = plan.kind() == BaseKind.FORTRESS && !pal.fenceWall();
        boolean hesco = w.style() == BasePlan.Wall.HESCO;
        int[][] inward = {{0, 1}, {-1, 0}, {0, -1}, {1, 0}};
        for (int side = 0; side < 4; side++) {
            for (int i = -half; i <= half; i++) {
                int x = side == 1 ? cx + half : side == 3 ? cx - half : cx + i;
                int z = side == 0 ? cz - half : side == 2 ? cz + half : cz + i;
                Direction.Axis run = side == 0 || side == 2 ? Direction.Axis.X : Direction.Axis.Z;
                switch (pal.layout("perimeter", "panel")) {
                    case "fence" -> fenceCell(x, z, y, h, i, run);
                    case "layered" -> layeredCell(x, z, y, i, run);
                    default -> wallCell(x, z, y, h, i, hesco, false);
                }
                if (thick) wallCell(x + inward[side][0], z + inward[side][1], y, h - 1, i, hesco, true);
            }
        }
        for (int side = 0; side < 4; side++) gate(plan, side, thick);
    }

    /** One column of the wall. Concrete is cast in panels four wide between taller posts, capped with a slab; hesco is a run of cages with a course of bags on top. */
    private void wallCell(int x, int z, int y, int h, int i, boolean hesco, boolean inner) {
        if (hesco) {
            fill(x, y, z, x, y + h - 2, z, pal.hesco());
            set(x, y + h - 1, z, pal.bag());
            return;
        }
        if (!inner && Math.floorMod(i, 4) == 0) {
            fill(x, y, z, x, y + h, z, pal.trim());                                                    // the post between panels
            return;
        }
        fill(x, y, z, x, y + h - 1, z, pal.heavy());
        if (!inner) set(x, y + h, z, slab(pal.slab(), SlabType.BOTTOM));                              // the panel's cap
    }

    /** One column of a chain-link fence: mesh the height of the wall, a taller capped post every fourth cell; a coil of wire along the top when the style has wire. */
    private void fenceCell(int x, int z, int y, int h, int i, Direction.Axis run) {
        int top = Math.max(3, h - 1);
        if (Math.floorMod(i, 4) == 0) {
            fill(x, y, z, x, y + top, z, pal.fencePost());
            set(x, y + top + 1, z, pal.bars());                                                       // a cap on each post
        } else {
            fill(x, y, z, x, y + top - 1, z, pal.fence());
            if (pal.parts().containsKey("wire")) set(x, y + top, z, wire(run));
        }
    }

    /** NTM's perimeter: a low wall two high, chain-link on it, a coil of wire along the top, a post every fourth cell. */
    private void layeredCell(int x, int z, int y, int i, Direction.Axis run) {
        fill(x, y, z, x, y + 1, z, pal.heavy());
        if (Math.floorMod(i, 4) == 0) {
            fill(x, y + 2, z, x, y + 4, z, pal.fencePost());
            return;
        }
        fill(x, y + 2, z, x, y + 3, z, pal.fence());
        set(x, y + 4, z, wire(run));
    }

    /** The style's wire, its coil laid along the wall: NTM's wire faces along its run (east or west along x, north or south along z), as in its military base. */
    private BlockState wire(Direction.Axis run) {
        BlockState w = pal.part("wire", pal.fence());
        return w.hasProperty(BlockStateProperties.HORIZONTAL_FACING)
                ? w.setValue(BlockStateProperties.HORIZONTAL_FACING, run == Direction.Axis.X ? Direction.EAST : Direction.NORTH) : w;
    }

    /** An opening five wide where a road meets the wall ({@code side} 0 north, 1 east, 2 south, 3 west): open to the sky, a lamp on a post either side. */
    private void gate(BasePlan plan, int side, boolean thick) {
        var w = plan.wall();
        int h = w.height(), half = w.half(), cx = plan.cx(), cz = plan.cz(), y = plan.padY();
        boolean alongX = side == 0 || side == 2;                                  // the wall runs along x here
        int gx = side == 1 ? half : side == 3 ? -half : 0, gz = side == 0 ? -half : side == 2 ? half : 0;
        int in = side == 0 || side == 3 ? 1 : -1;                                 // inward across the wall
        for (int k = -2; k <= 2; k++) {
            for (int d = 0; d <= (thick ? 1 : 0); d++) {
                int x = cx + gx + (alongX ? k : in * d), z = cz + gz + (alongX ? in * d : k);
                fill(x, y, z, x, y + Math.max(h, 5) + 1, z, Blocks.AIR);
                set(x, y - 1, z, pal.floor());
            }
        }
        boolean frame = "frame".equals(pal.layout("gate", "open"));
        int top = frame ? y + 5 : y + h + 1;
        for (int k = -3; k <= 3; k += 6) {
            for (int d = 0; d <= (thick ? 1 : 0); d++) {
                int x = cx + gx + (alongX ? k : in * d), z = cz + gz + (alongX ? in * d : k);
                fill(x, y, z, x, top, z, frame ? pal.steel() : pal.trim());
                if (d == 0 && !frame) set(x, top, z, pal.light());                   // a lamp set into the post's head
            }
        }
        if (frame) {                                                                // a steel frame: a striped header across the opening, lamps under it
            for (int k = -2; k <= 2; k++) {
                int x = cx + gx + (alongX ? k : 0), z = cz + gz + (alongX ? 0 : k);
                set(x, top, z, pal.part("hazard", pal.steel()));
                if (Math.abs(k) == 2) set(x + (alongX ? 0 : in), top - 1, z + (alongX ? in : 0), lamp());
            }
        }
        boolean main = w.axis() == 0 ? alongX : !alongX;
        if (main && pal.parts().containsKey("poleA")) {                             // the striped barrier pole, just inside, on one side
            int x = cx + gx + (alongX ? 4 : in * 2), z = cz + gz + (alongX ? in * 2 : 4);
            for (int yy = 0; yy < 4; yy++) set(x, y + yy, z, pal.parts().get(yy % 2 == 0 ? "poleA" : "poleB"));
        }
    }

    // ---- paving

    private boolean inOtherRoad(Piece self, int x, int z) {
        for (Piece r : roads) {
            if (r == self) continue;
            int xa = x0(r), za = z0(r);
            if (x >= xa && x < xa + r.w() && z >= za && z < za + r.d()) return true;
        }
        return false;
    }

    /** A road, street, plaza or lane (see {@link BasePlan.Type#ROAD}): paved at ground level; a road's curbs stop where another crosses it. */
    private void road(Piece p) {
        int xa = x0(p), za = z0(p), xb = xa + p.w() - 1, zb = za + p.d() - 1, y = p.y() - 1;
        boolean alongX = p.w() >= p.d();
        int mid = alongX ? p.z() : p.x();
        for (int x = xa; x <= xb; x++) {
            for (int z = za; z <= zb; z++) {
                if (p.facing() == 3) {
                    set(x, y, z, pal.flag("paved") ? pal.floor() : pal.ground());
                    continue;
                }
                boolean rim = p.facing() < 2 && (alongX ? z == za || z == zb : x == xa || x == xb);
                boolean dash = p.facing() == 0 && (alongX ? z == mid && Math.floorMod(x, 4) < 2 : x == mid && Math.floorMod(z, 4) < 2);
                if ((rim || dash) && inOtherRoad(p, x, z)) rim = dash = false;
                set(x, y, z, rim ? pal.edge() : dash ? pal.mark() : pal.floor());
            }
        }
    }

    /** A footpath: paved at ground level. */
    private void path(Piece p) {
        fill(x0(p), p.y() - 1, z0(p), x0(p) + p.w() - 1, p.y() - 1, z0(p) + p.d() - 1, pal.floor());
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
            case HELIPAD -> {
                if (pal.flag("silo")) silo(p);
                else helipad(p);
            }
            case CHECKPOINT -> checkpoint(p);
            case BARRIER -> barrier(p, plan);
            case ROAD -> road(p);
            case PATH -> path(p);
            case BUNKER -> bunker(p);
            case DEPOT -> depot(p);
            case TOWER -> {
                boolean corner = plan.wall() != null && Math.abs(p.x() - plan.cx()) == plan.wall().half() && Math.abs(p.z() - plan.cz()) == plan.wall().half();
                if ("lattice".equals(pal.layout(corner ? "cornerTowers" : "gateTowers", "lookout"))) latticeTower(p);
                else tower(p);
            }
            case NEST -> nest(p);
            case MAST -> {
                if ("lattice".equals(pal.layout("mast", "pole"))) latticeMast(p);
                else mast(p);
            }
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
        ring(xa, y, za, xb, zb, pal.plinth());
        if (((st >> 4) & 1) == 0) ring(xa, y + h - 1, za, xb, zb, pal.band() != null ? pal.band() : pal.trim());              // one band colour for the whole base
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
        int kind = gear;
        if (p.w() <= 3) return;
        switch (kind) {
            case 0 -> {                                                                              // a solar array
                for (int x = xa + 1; x < xb; x += 2) for (int z = za + 1; z < zb; z += 2) set(x, top, z, Blocks.DAYLIGHT_DETECTOR);
            }
            case 1 -> {                                                                              // air handling
                set(xa + 1, top, za + 1, pal.steel());
                set(xa + 1, top + 1, za + 1, Blocks.IRON_TRAPDOOR);
                set(xb - 1, top, zb - 1, pal.steel());
                set(xb - 1, top + 1, zb - 1, Blocks.IRON_TRAPDOOR);
            }
            case 2 -> {                                                                              // a water tank on a stand
                set(xb - 1, top, za + 1, pal.steel());
                set(xb - 1, top + 1, za + 1, Blocks.BARREL);
                set(xb - 2, top, za + 1, pal.steel());
                set(xb - 2, top + 1, za + 1, Blocks.BARREL);
                set(xa + 1, top, zb - 1, Blocks.IRON_TRAPDOOR);
            }
            default -> {                                                                             // one air handler in the middle
                set(p.x(), top, p.z(), pal.steel());
                set(p.x(), top + 1, p.z(), Blocks.IRON_TRAPDOOR);
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

    /** Every building of a base is of the same body; only the armoury, the bunkers and the medical block differ, for what they are. */
    private Block bodyFor(Piece p) {
        return pal.body();
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
                case 1, 2 -> Blocks.CHEST;
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
                set(tx + dx, y, tz, slab(pal.slab(), SlabType.BOTTOM));
                set(tx + dx, y, tz - 1, Blocks.POLISHED_ANDESITE_STAIRS, Direction.SOUTH);
                set(tx + dx, y, tz + 1, Blocks.POLISHED_ANDESITE_STAIRS, Direction.NORTH);
            }
        }
        // the cache cell and the strip beside it stay clear for the contract's barrel
    }

    /** Bunks: a row of beds along each long wall, a locker between each pair; a steel door and a bench. */
    private void barracks(Piece p) {
        hall(p);
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
        building(small, pal.body(), false);
        int xa = x0(p), za = z0(p), xb = xa + p.w() - 1, zb = za + p.d() - 1, y = p.y();
        for (int x = xa + 1; x < xb - 1; x++) if (!nearDoor(small, x, zb - 1)) { bed(x, y, zb - 1, Direction.EAST, pal.bed()); break; }
        set(xa + 1, y, za + 1, Blocks.CRAFTING_TABLE);
        set(xb - 1, y, za + 1, Blocks.BARREL);
        set(xb - 1, y, zb - 1, Blocks.CHEST, Direction.WEST);
    }

    /** Smokers and a furnace on a counter, a sink, a cold store; long tables with benches. */
    private void mess(Piece p) {
        hall(p);
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
            set(x, y, z, slab(pal.slab(), SlabType.BOTTOM));
            Direction a = alongX ? Direction.SOUTH : Direction.EAST, b2 = a.getOpposite();
            set(x + (alongX ? 0 : -1), y, z + (alongX ? -1 : 0), Blocks.POLISHED_ANDESITE_STAIRS, a);
            set(x + (alongX ? 0 : 1), y, z + (alongX ? 1 : 0), Blocks.POLISHED_ANDESITE_STAIRS, b2);
        }
    }

    /** White concrete with a red cross on the side wall: beds, a brewing stand and a sink, supply chests. */
    private void medical(Piece p) {
        Piece white = new Piece(p.type(), p.x(), p.y(), p.z(), p.w(), p.d(), p.facing(), p.height(), p.style() | 0x10);
        building(white, pal.mark(), true);
        int xa = x0(p), za = z0(p), xb = xa + p.w() - 1, zb = za + p.d() - 1, y = p.y();
        // the cross, on the wall to the left of the door
        Direction left = dir(p.facing()).getCounterClockWise();
        int wx = left == Direction.WEST ? xa : left == Direction.EAST ? xb : p.x();
        int wz = left == Direction.NORTH ? za : left == Direction.SOUTH ? zb : p.z();
        int ax = left.getAxis() == Direction.Axis.X ? 0 : 1, az = left.getAxis() == Direction.Axis.X ? 1 : 0;
        for (int k = -1; k <= 1; k++) {
            set(wx + ax * k, y + 2, wz + az * k, pal.boomA());
            if (k != 0) set(wx, y + 2 + k, wz, pal.boomA());
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
        if (p.w() >= 5) ring(xa, y - 1, za, xb, zb, pal.edge());
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

    /** A landing pad: the floor material, an edge ring, a marked H, corner lamps. No walls; helicopters want the sky. */
    private void helipad(Piece p) {
        int y = p.y() - 1, r = p.w() / 2;
        for (int dx = -r; dx <= r; dx++) {
            for (int dz = -r; dz <= r; dz++) {
                boolean edge = Math.max(Math.abs(dx), Math.abs(dz)) == r;
                boolean h = (Math.abs(dx) == 2 && Math.abs(dz) <= 2) || (dz == 0 && Math.abs(dx) <= 2);
                boolean ring = Math.abs(Math.hypot(dx, dz) - (r - 1)) < 0.6;
                BlockState b = (h ? pal.mark() : ring ? pal.edge() : edge ? pal.plinth() : pal.floor()).defaultBlockState();
                if (ring && pal.parts().containsKey("hazard")) b = pal.parts().get("hazard");
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
    }

    /** A wing of barriers running out from a gate, so that an approach is a funnel: hesco for a hesco wall, else concrete jersey blocks. */
    private void barrier(Piece p, BasePlan plan) {
        boolean alongZ = p.facing() == 0, hesco = plan.wall() != null && plan.wall().style() == BasePlan.Wall.HESCO;
        for (int i = -3; i <= 3; i++) {
            int x = p.x() + (alongZ ? 0 : i), z = p.z() + (alongZ ? i : 0);
            set(x, p.y() - 1, z, pal.ground());
            if (hesco) {
                fill(x, p.y(), z, x, p.y() + 1, z, pal.hesco());
            } else {
                set(x, p.y(), z, pal.heavy());
                set(x, p.y() + 1, z, slab(pal.slab(), SlabType.BOTTOM));
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
        else if (roll == 2) set(x, y, z, slab(pal.slab(), SlabType.BOTTOM));
    }

    // ---- structures

    /**
     * A watchtower, one design for every tower of a base: a concrete shaft with posts at the corners, a steel door facing into the
     * base and a ladder up the back; an open lookout on top (a breast-high parapet, corner posts, a roof overhanging it) with a
     * searchlight under the roof.
     */
    private void tower(Piece p) {
        int s = p.w(), xa = x0(p), za = z0(p), xb = xa + s - 1, zb = za + s - 1, y = p.y(), h = p.height();
        Direction out = dir(p.facing()), back = out.getOpposite();
        fill(xa, y - 1, za, xb, y - 1, zb, pal.heavy());
        shell(xa, y, za, xb, y + h - 1, zb, pal.heavy());
        for (int[] c : new int[][]{{xa, za}, {xb, za}, {xa, zb}, {xb, zb}}) fill(c[0], y, c[1], c[0], y + h + 3, c[1], pal.trim());
        fill(xa, y + h - 1, za, xb, y + h - 1, zb, pal.trim());                                        // the floor of the lookout
        for (int x = xa + 1; x < xb; x++) {                                                             // the parapet, between the posts
            set(x, y + h, za, pal.heavy());
            set(x, y + h, zb, pal.heavy());
        }
        for (int z = za + 1; z < zb; z++) {
            set(xa, y + h, z, pal.heavy());
            set(xb, y + h, z, pal.heavy());
        }
        // the roof is five above the platform: a soldier's post finds the highest floor within four, and it must find the platform, not the roof
        fill(xa - 1, y + h + 4, za - 1, xb + 1, y + h + 4, zb + 1, pal.trim());
        set(p.x(), y + h + 3, p.z(), lamp());                                                           // the searchlight, under the roof
        int r = s / 2 - 1, lx = p.x() + back.getStepX() * r, lz = p.z() + back.getStepZ() * r;
        for (int yy = y; yy < y + h; yy++) set(lx, yy, lz, Blocks.LADDER.defaultBlockState().setValue(LadderBlock.FACING, out));
        set(lx, y + h - 1, lz, Blocks.AIR);                                                             // the hatch
        int dx = p.x() + out.getStepX() * (s / 2), dz = p.z() + out.getStepZ() * (s / 2);
        fill(dx, y, dz, dx, y + 1, dz, Blocks.AIR);
        ironDoor(dx, y, dz, out);
        if (s > 3) {
            Direction side = out.getClockWise();
            set(p.x() + side.getStepX(), y, p.z() + side.getStepZ(), Blocks.CHEST, side.getOpposite());
        }
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

    /** A communications mast: a steel foot, a pole with a rod on top, a cabinet beside it. */
    private void mast(Piece p) {
        int y = p.y(), h = p.height();
        fill(p.x() - 1, y - 1, p.z() - 1, p.x() + 1, y - 1, p.z() + 1, pal.floor());
        fill(p.x(), y, p.z(), p.x(), y + 2, p.z(), pal.steel());
        fill(p.x(), y + 3, p.z(), p.x(), y + h - 2, p.z(), Blocks.IRON_BARS);
        set(p.x(), y + h - 1, p.z(), Blocks.LIGHTNING_ROD);
        set(p.x() + 1, y, p.z(), pal.steel());                                                           // the cabinet
        set(p.x() + 1, y + 1, p.z(), Blocks.IRON_TRAPDOOR);
    }

    /** A floodlight on a steel pole. */
    private void light(Piece p) {
        fill(p.x(), p.y(), p.z(), p.x(), p.y() + p.height() - 2, p.z(), Blocks.IRON_BARS);
        set(p.x(), p.y() + p.height() - 1, p.z(), lamp());
        set(p.x(), p.y() + p.height(), p.z(), Blocks.IRON_TRAPDOOR);                                      // the shade
    }

    /** A vehicle bay: a paved hard-stand open to its road, a heavy back wall with floodlights, low side walls, posts at the corners; open at the top so any vehicle clears. */
    private void bay(Piece p) {
        int xa = x0(p), za = z0(p), xb = xa + p.w() - 1, zb = za + p.d() - 1, y = p.y();
        Direction out = dir(p.facing());
        fill(xa, y - 1, za, xb, y - 1, zb, pal.floor());
        if (pal.parts().containsKey("hazard")) {                                    // the hard-stand edged in hazard stripes
            for (int x = xa; x <= xb; x++) for (int z = za; z <= zb; z++) if (x == xa || x == xb || z == za || z == zb) set(x, y - 1, z, pal.parts().get("hazard"));
        }
        for (int x = xa; x <= xb; x++) {
            for (int z = za; z <= zb; z++) {
                Direction side = x == xa ? Direction.WEST : x == xb ? Direction.EAST : z == za ? Direction.NORTH : z == zb ? Direction.SOUTH : null;
                if (side == null || side == out) continue;
                boolean corner = (x == xa || x == xb) && (z == za || z == zb);
                if (corner) fill(x, y, z, x, y + p.height(), z, pal.trim());
                else if (side == out.getOpposite()) fill(x, y, z, x, y + 2, z, pal.heavy());
                else fill(x, y, z, x, y + 1, z, pal.heavy());
            }
        }
        int bx = p.x() - out.getStepX() * (p.w() / 2), bz = p.z() - out.getStepZ() * (p.d() / 2);
        Direction along = out.getClockWise();
        for (int k = -2; k <= 2; k += 4) set(bx + along.getStepX() * k, y + 2, bz + along.getStepZ() * k, pal.light());   // set into the back wall
    }

    // ---- NTM pieces

    /** A barracks or mess: a flat-roofed building, or a Quonset hall when the style says so. */
    private void hall(Piece p) {
        if ("quonset".equals(pal.layout("hall", "flat"))) quonset(p);
        else building(p, bodyFor(p), true);
    }

    /**
     * A Quonset hall: a barrel roof stepped down to the ground along the long side, gable ends of the body block with a window,
     * a steel door through the curve on the street side, lamps along the ridge.
     */
    private void quonset(Piece p) {
        int xa = x0(p), za = z0(p), xb = xa + p.w() - 1, zb = za + p.d() - 1, y = p.y();
        boolean alongX = p.w() >= p.d();
        int width = alongX ? p.d() : p.w(), length = alongX ? p.w() : p.d();
        double c = (width - 1) / 2.0, r = width / 2.0 + 0.1;
        int[] hi = new int[width];
        for (int i = 0; i < width; i++) hi[i] = 1 + (int) Math.floor(Math.sqrt(Math.max(0, r * r - (i - c) * (i - c))));
        BlockState arch = pal.part("arch", pal.trim());
        fill(xa - 1, y - 1, za - 1, xb + 1, y - 1, zb + 1, pal.floor());
        for (int j = 0; j < length; j++) {
            boolean end = j == 0 || j == length - 1;
            for (int i = 0; i < width; i++) {
                for (int yy = 0; yy <= hi[i]; yy++) {
                    int x = alongX ? xa + j : xa + i, z = alongX ? za + i : za + j;
                    boolean shell = i == 0 || i == width - 1 || yy == hi[i] || hi[i - 1] < yy || hi[i + 1] < yy;
                    if (shell) set(x, y + yy, z, arch);
                    else if (end) set(x, y + yy, z, yy == 2 && Math.abs(i - c) <= 1 ? pal.glass().defaultBlockState() : pal.body().defaultBlockState());
                    else set(x, y + yy, z, Blocks.AIR.defaultBlockState());
                }
            }
            int mid = (int) Math.floor(c);
            if (!end && j % 3 == 1) {
                int x = alongX ? xa + j : xa + mid, z = alongX ? za + mid : za + j;
                set(x, y + hi[mid], z, pal.light());
            }
        }
        Direction out = dir(p.facing());
        int dx = p.x() + out.getStepX() * (p.w() / 2), dz = p.z() + out.getStepZ() * (p.d() / 2);
        if (out.getStepX() > 0) dx = xb;
        if (out.getStepX() < 0) dx = xa;
        if (out.getStepZ() > 0) dz = zb;
        if (out.getStepZ() < 0) dz = za;
        fill(dx, y, dz, dx, y + 1, dz, Blocks.AIR);
        ironDoor(dx, y, dz, out);
        Direction along = out.getClockWise();
        fill(dx + along.getStepX(), y, dz + along.getStepZ(), dx + along.getStepX(), y + 1, dz + along.getStepZ(), Blocks.AIR);
    }

    /**
     * A steel lattice tower on stilts, as at NTM's gates: four lattice legs, a steel ladder column in the middle, a cab of
     * steel plate with a band of windows and an overhanging roof, a searchlight under it.
     */
    private void latticeTower(Piece p) {
        int s = p.w(), xa = x0(p), za = z0(p), xb = xa + s - 1, zb = za + s - 1, y = p.y(), h = p.height();
        Direction out = dir(p.facing());
        BlockState leg = pal.part("lattice", pal.steel()), cab = pal.part("cab", pal.steel());
        for (int[] c : new int[][]{{xa, za}, {xb, za}, {xa, zb}, {xb, zb}}) {
            fill(c[0], y - 1, c[1], c[0], y - 1, c[1], pal.heavy());
            for (int yy = y; yy < y + h - 1; yy++) set(c[0], yy, c[1], leg);
        }
        BlockState braceX = lying(leg, Direction.Axis.X), braceZ = lying(leg, Direction.Axis.Z);
        for (int yy = y + 3; yy < y + h - 1; yy += 4) {                                   // a brace round the legs every four, lying along its run
            for (int x = xa + 1; x < xb; x++) {
                set(x, yy, za, braceX);
                set(x, yy, zb, braceX);
            }
            for (int z = za + 1; z < zb; z++) {
                set(xa, yy, z, braceZ);
                set(xb, yy, z, braceZ);
            }
        }
        fill(p.x(), y, p.z(), p.x(), y + h - 2, p.z(), pal.steel());                     // the ladder column
        int lx = p.x() + out.getStepX(), lz = p.z() + out.getStepZ();
        for (int yy = y; yy < y + h; yy++) set(lx, yy, lz, Blocks.LADDER.defaultBlockState().setValue(LadderBlock.FACING, out));
        for (int x = xa; x <= xb; x++) for (int z = za; z <= zb; z++) set(x, y + h - 1, z, cab);   // the cab floor
        set(lx, y + h - 1, lz, Blocks.AIR.defaultBlockState());                           // the hatch
        for (int yy = y + h; yy <= y + h + 3; yy++) {
            for (int x = xa; x <= xb; x++) {
                for (int z = za; z <= zb; z++) {
                    boolean rim = x == xa || x == xb || z == za || z == zb, corner = (x == xa || x == xb) && (z == za || z == zb);
                    if (!rim) continue;
                    boolean window = !corner && (yy == y + h + 1 || yy == y + h + 2);
                    set(x, yy, z, window ? pal.glass().defaultBlockState() : cab);
                }
            }
        }
        for (int x = xa - 1; x <= xb + 1; x++) for (int z = za - 1; z <= zb + 1; z++) set(x, y + h + 4, z, cab);   // the roof
        set(p.x(), y + h + 3, p.z(), lamp());
    }

    /** A radio mast of lattice on the plaza: three by three legs, platforms every five, a steel spar and a beacon on top, a cabinet at its foot. */
    private void latticeMast(Piece p) {
        int y = p.y(), top = y + 18;
        BlockState leg = pal.part("lattice", pal.steel());
        fill(p.x() - 2, y - 1, p.z() - 2, p.x() + 2, y - 1, p.z() + 2, pal.heavy());
        for (int yy = y; yy < top; yy++) {
            for (int dx = -1; dx <= 1; dx += 2) for (int dz = -1; dz <= 1; dz += 2) set(p.x() + dx, yy, p.z() + dz, leg);
            if ((yy - y) % 5 == 4) {                                                       // a brace ring, each piece lying along its side
                for (int d = -1; d <= 1; d += 2) {
                    set(p.x(), yy, p.z() + d, lying(leg, Direction.Axis.X));
                    set(p.x() + d, yy, p.z(), lying(leg, Direction.Axis.Z));
                }
            }
        }
        fill(p.x(), y + 1, p.z(), p.x(), top + 3, p.z(), pal.steel());
        set(p.x(), top + 4, p.z(), Blocks.LIGHTNING_ROD);
        set(p.x(), top - 1, p.z() + 1, lamp());
        set(p.x(), y, p.z(), pal.steel());
    }

    /**
     * A missile silo pad, as on NTM's silo: a hazard-striped square, a concrete apron, a steel hatch in the middle with the
     * style's hatch block, bollards at the corners and lamps on two of them.
     */
    private void silo(Piece p) {
        int y = p.y() - 1, r = p.w() / 2;
        BlockState hazard = pal.part("hazard", pal.edge());
        for (int dx = -r; dx <= r; dx++) {
            for (int dz = -r; dz <= r; dz++) {
                int m = Math.max(Math.abs(dx), Math.abs(dz));
                boolean big = r >= 5;
                BlockState b = m == r || big && m == r - 1 ? hazard : m <= (big ? 2 : 1) ? pal.steel().defaultBlockState() : pal.heavy().defaultBlockState();
                set(p.x() + dx, y, p.z() + dz, b);
            }
        }
        if (pal.parts().containsKey("hatch")) set(p.x(), y, p.z(), pal.parts().get("hatch"));
        for (int[] c : new int[][]{{-r, -r}, {r, -r}, {-r, r}, {r, r}}) {
            set(p.x() + c[0], y + 1, p.z() + c[1], pal.steel());
            if (c[0] < 0) set(p.x() + c[0], y + 2, p.z() + c[1], pal.light());
        }
    }

    /** A sandbag revetment across an approach: a line of bags two high on the ground, a firing notch every fourth, an ammunition drum behind. No digging. */
    private void trench(Piece p) {
        boolean alongX = (p.facing() & 1) == 0;
        int len = p.w(), y = p.y();
        for (int i = -len / 2; i <= len / 2; i++) {
            int x = p.x() + (alongX ? i : 0), z = p.z() + (alongX ? 0 : i);
            set(x, y - 1, z, pal.ground());
            set(x, y, z, pal.bag());
            if (Math.floorMod(i, 4) != 0) set(x, y + 1, z, pal.bag());
        }
        set(p.x() + (alongX ? 0 : 1), y, p.z() + (alongX ? 1 : 0), Blocks.BARREL);
    }
}
