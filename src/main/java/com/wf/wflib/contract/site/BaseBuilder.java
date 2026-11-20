package com.wf.wflib.contract.site;

import com.wf.wflib.contract.site.BasePlan.Piece;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LadderBlock;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.SlabType;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.level.levelgen.Heightmap;

/**
 * Puts a {@link BasePlan} into the world: grades the pad level (cutting hills, filling hollows, blending an apron into the land
 * around it), then raises the wall, towers, buildings, nests, trenches and the rest of what the plan lists. All plain blocks.
 * The caller loads the chunks and keeps them. Server thread; one call can place tens of thousands of blocks.
 */
public final class BaseBuilder {
    private static final int FLAGS = 2 | 16;
    private static final int APRON = 6, CLEAR = 16;

    private final ServerLevel level;
    private final Palette pal;
    private final RandomSource rng;
    private final BlockPos.MutableBlockPos at = new BlockPos.MutableBlockPos();

    private BaseBuilder(ServerLevel level, Palette pal, RandomSource rng) {
        this.level = level;
        this.pal = pal;
        this.rng = rng;
    }

    /** Builds {@code plan}; returns the number of blocks it touched (for the log). */
    public static void build(ServerLevel level, BasePlan plan, Palette pal, long seed) {
        BaseBuilder b = new BaseBuilder(level, pal, RandomSource.create(seed));
        b.load(plan);
        b.grade(plan);
        if (plan.wall() != null) b.wall(plan);
        for (Piece p : plan.pieces()) b.piece(p, plan);
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
        level.setBlock(at.set(x, y, z), s, FLAGS);
    }

    private void set(int x, int y, int z, Block b) {
        set(x, y, z, b.defaultBlockState());
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

    private BlockState slab(Block b, SlabType type) {
        return b.defaultBlockState().setValue(SlabBlock.TYPE, type);
    }

    // ---------------------------------------------------------------- the wall

    private void wall(BasePlan plan) {
        var w = plan.wall();
        int h = w.height(), half = w.half(), cx = plan.cx(), cz = plan.cz(), y = plan.padY();
        boolean thick = plan.kind() == BaseKind.FORTRESS;
        fill(cx - half, y, cz - half, cx + half, y + h - 1, cz - half, pal.wall());
        fill(cx - half, y, cz + half, cx + half, y + h - 1, cz + half, pal.wall());
        fill(cx - half, y, cz - half, cx - half, y + h - 1, cz + half, pal.wall());
        fill(cx + half, y, cz - half, cx + half, y + h - 1, cz + half, pal.wall());
        if (thick) {                                           // a second skin and a wall-walk behind the merlons
            fill(cx - half + 1, y + h - 2, cz - half + 1, cx + half - 1, y + h - 2, cz - half + 1, pal.floor());
            fill(cx - half + 1, y + h - 2, cz + half - 1, cx + half - 1, y + h - 2, cz + half - 1, pal.floor());
            fill(cx - half + 1, y + h - 2, cz - half + 1, cx - half + 1, y + h - 2, cz + half - 1, pal.floor());
            fill(cx + half - 1, y + h - 2, cz - half + 1, cx + half - 1, y + h - 2, cz + half - 1, pal.floor());
        }
        // merlons: every other block stands one higher
        for (int i = -half; i <= half; i += 2) {
            set(cx + i, y + h, cz - half, pal.wall());
            set(cx + i, y + h, cz + half, pal.wall());
            set(cx - half, y + h, cz + i, pal.wall());
            set(cx + half, y + h, cz + i, pal.wall());
        }
        // arrow slits
        for (int i = -half + 4; i <= half - 4; i += 5) {
            set(cx + i, y + h - 2, cz - half, Blocks.IRON_BARS);
            set(cx + i, y + h - 2, cz + half, Blocks.IRON_BARS);
            set(cx - half, y + h - 2, cz + i, Blocks.IRON_BARS);
            set(cx + half, y + h - 2, cz + i, Blocks.IRON_BARS);
        }
        // the two gates, five wide, with a beam over them and a lantern each side
        for (int side = -1; side <= 1; side += 2) {
            int gx = w.axis() == 0 ? 0 : side * half, gz = w.axis() == 0 ? side * half : 0;
            for (int k = -2; k <= 2; k++) {
                int x = cx + gx + (w.axis() == 0 ? k : 0), z = cz + gz + (w.axis() == 0 ? 0 : k);
                fill(x, y, z, x, y + h - 2, z, Blocks.AIR);
                set(x, y + h - 1, z, pal.frame());
                set(x, y + h, z, Blocks.AIR);
            }
            for (int k = -3; k <= 3; k += 6) {
                int x = cx + gx + (w.axis() == 0 ? k : 0), z = cz + gz + (w.axis() == 0 ? 0 : k);
                fill(x, y, z, x, y + h, z, pal.frame());
                set(x, y + 3, z + (w.axis() == 0 ? side * -1 : 0), Blocks.LANTERN);
            }
        }
    }

    // ---------------------------------------------------------------- pieces

    private void piece(Piece p, BasePlan plan) {
        switch (p.type()) {
            case COMMAND -> hall(p, true);
            case BARRACKS -> hall(p, false);
            case BUNKER -> bunker(p);
            case DEPOT -> depot(p);
            case TENT -> tent(p);
            case TOWER -> tower(p);
            case NEST -> nest(p);
            case CAMP -> camp(p);
            case MAST -> mast(p);
            case BAY -> bay(p);
            case LIGHT -> light(p);
            case TRENCH -> trench(p);
        }
    }

    private int x0(Piece p) {
        return p.x() - p.w() / 2;
    }

    private int z0(Piece p) {
        return p.z() - p.d() / 2;
    }

    /** A flat-roofed building: floor, walls with a post at each corner, windows, a door on the {@code facing} side, a roof. The command post is furnished as an office. */
    private void hall(Piece p, boolean command) {
        int xa = x0(p), za = z0(p), xb = xa + p.w() - 1, zb = za + p.d() - 1, y = p.y(), h = p.height();
        fill(xa, y - 1, za, xb, y - 1, zb, pal.floor());
        shell(xa, y, za, xb, y + h - 1, zb, pal.wall());
        for (int[] c : new int[][]{{xa, za}, {xb, za}, {xa, zb}, {xb, zb}}) fill(c[0], y, c[1], c[0], y + h, c[1], pal.frame());
        fill(xa - 1, y + h, za - 1, xb + 1, y + h, zb + 1, pal.roof());
        fill(xa - 1, y + h + 1, za - 1, xb + 1, y + h + 1, za - 1, Blocks.AIR);               // clear
        for (int x = xa - 1; x <= xb + 1; x++) {
            set(x, y + h + 1, za - 1, slab(pal.roofSlab(), SlabType.BOTTOM));
            set(x, y + h + 1, zb + 1, slab(pal.roofSlab(), SlabType.BOTTOM));
        }
        for (int z = za; z <= zb; z++) {
            set(xa - 1, y + h + 1, z, slab(pal.roofSlab(), SlabType.BOTTOM));
            set(xb + 1, y + h + 1, z, slab(pal.roofSlab(), SlabType.BOTTOM));
        }
        for (int x = xa + 2; x < xb - 1; x += 3) {                       // windows
            set(x, y + 1, za, Blocks.IRON_BARS);
            set(x, y + 1, zb, Blocks.IRON_BARS);
        }
        door(p, xa, za, xb, zb, y);
        int cxm = p.x(), czm = p.z();
        set(cxm, y + h - 1, czm, Blocks.LANTERN);
        if (command) {
            set(cxm, y, czm, Blocks.CARTOGRAPHY_TABLE);
            set(xa + 1, y, zb - 1, Blocks.CHEST);
            set(xb - 1, y, zb - 1, Blocks.CHEST);
            set(xb - 1, y, za + 1, Blocks.LECTERN);
            set(xa + 1, y, za + 1, Blocks.BARREL);                       // the cache barrel is filled by the contract
        } else {
            for (int z = za + 1; z < zb; z += 2) {                       // bunks along the long walls: a bedroll of wool and a carpet
                boolean alongX = p.w() >= p.d();
                if (alongX) break;
            }
            if (p.w() >= p.d()) {
                for (int x = xa + 1; x < xb - 1; x += 2) {
                    set(x, y, za + 1, Blocks.RED_WOOL);
                    set(x + 1, y, za + 1, Blocks.WHITE_CARPET);
                    set(x, y, zb - 1, Blocks.RED_WOOL);
                    set(x + 1, y, zb - 1, Blocks.WHITE_CARPET);
                }
            } else {
                for (int z = za + 1; z < zb - 1; z += 2) {
                    set(xa + 1, y, z, Blocks.RED_WOOL);
                    set(xa + 1, y, z + 1, Blocks.WHITE_CARPET);
                    set(xb - 1, y, z, Blocks.RED_WOOL);
                    set(xb - 1, y, z + 1, Blocks.WHITE_CARPET);
                }
            }
            set(xb - 1, y, zb - 1, Blocks.BARREL);
        }
    }

    /** A doorway two high and two wide in the middle of the wall {@code facing} points at (0 north, 1 east, 2 south, 3 west). */
    private void door(Piece p, int xa, int za, int xb, int zb, int y) {
        int dx = p.x(), dz = p.z();
        switch (p.facing() & 3) {
            case 0 -> fill(dx, y, za, dx + 1, y + 1, za, Blocks.AIR);
            case 1 -> fill(xb, y, dz, xb, y + 1, dz + 1, Blocks.AIR);
            case 2 -> fill(dx, y, zb, dx + 1, y + 1, zb, Blocks.AIR);
            default -> fill(xa, y, dz, xa, y + 1, dz + 1, Blocks.AIR);
        }
    }

    /** Thick-walled, low and slitted, with the roof banked over with earth. */
    private void bunker(Piece p) {
        int xa = x0(p), za = z0(p), xb = xa + p.w() - 1, zb = za + p.d() - 1, y = p.y();
        fill(xa - 1, y - 1, za - 1, xb + 1, y - 1, zb + 1, pal.floor());
        fill(xa - 1, y, za - 1, xb + 1, y + 2, zb + 1, pal.wall());
        fill(xa + 1, y, za + 1, xb - 1, y + 1, zb - 1, Blocks.AIR);
        fill(xa - 1, y + 3, za - 1, xb + 1, y + 3, zb + 1, pal.ground());
        for (int x = xa + 1; x < xb; x += 2) {
            set(x, y + 1, za - 1, Blocks.IRON_BARS);
            set(x, y + 1, zb + 1, Blocks.IRON_BARS);
        }
        door(new Piece(p.type(), p.x(), p.y(), p.z(), p.w() + 2, p.d() + 2, p.facing(), p.height()), xa - 1, za - 1, xb + 1, zb + 1, y);
        set(p.x(), y + 1, p.z(), Blocks.LANTERN);
        set(xa + 1, y, za + 1, Blocks.BARREL);
    }

    /** An open shed of crates, barrels and hay: what the garrison lives on. */
    private void depot(Piece p) {
        int xa = x0(p), za = z0(p), xb = xa + p.w() - 1, zb = za + p.d() - 1, y = p.y();
        fill(xa, y - 1, za, xb, y - 1, zb, pal.floor());
        for (int[] c : new int[][]{{xa, za}, {xb, za}, {xa, zb}, {xb, zb}}) fill(c[0], y, c[1], c[0], y + 2, c[1], pal.frame());
        fill(xa, y + 3, za, xb, y + 3, zb, pal.roof());
        for (int x = xa + 1; x < xb; x++) {
            for (int z = za + 1; z < zb; z++) {
                int roll = rng.nextInt(7);
                if (roll == 0) set(x, y, z, Blocks.BARREL);
                else if (roll == 1) set(x, y, z, Blocks.HAY_BLOCK);
                else if (roll == 2) {
                    set(x, y, z, Blocks.CHEST);
                }
            }
        }
    }

    /** A lean-to of wool over a bedroll, open at both ends. */
    private void tent(Piece p) {
        int xa = x0(p), za = z0(p), xb = xa + p.w() - 1, zb = za + p.d() - 1, y = p.y();
        fill(xa, y, za, xb, y + 2, zb, Blocks.AIR);
        fill(xa, y, za, xa, y + 1, zb, pal.cloth());
        fill(xb, y, za, xb, y + 1, zb, pal.cloth());
        fill(xa, y + 2, za, xb, y + 2, zb, pal.cloth());
        set(xa + 1, y, za + 1, Blocks.RED_WOOL);
        set(xa + 1, y, za + 2, Blocks.WHITE_CARPET);
        set(xb - 1, y, zb - 1, Blocks.BARREL);
        set(p.x(), y, p.z(), Blocks.LANTERN);
    }

    /** A square tower with a ladder inside, arrow slits, and an open platform with a parapet at {@code y + height}. */
    private void tower(Piece p) {
        int s = p.w(), xa = x0(p), za = z0(p), xb = xa + s - 1, zb = za + s - 1, y = p.y(), h = p.height();
        fill(xa, y - 1, za, xb, y - 1, zb, pal.wall());
        shell(xa, y, za, xb, y + h - 1, zb, pal.wall());
        fill(xa, y + h - 1, za, xb, y + h - 1, zb, pal.floor());                   // the platform
        for (int[] c : new int[][]{{xa, za}, {xb, za}, {xa, zb}, {xb, zb}}) fill(c[0], y, c[1], c[0], y + h, c[1], pal.frame());
        // parapet round the rim
        for (int x = xa; x <= xb; x++) {
            set(x, y + h, za, pal.fence());
            set(x, y + h, zb, pal.fence());
        }
        for (int z = za; z <= zb; z++) {
            set(xa, y + h, z, pal.fence());
            set(xb, y + h, z, pal.fence());
        }
        // the ladder runs up the south wall inside; the platform has a hatch over it
        int lx = s > 3 ? xa + 1 : p.x(), lz = zb - 1;
        for (int yy = y; yy < y + h; yy++) set(lx, yy, lz, Blocks.LADDER.defaultBlockState().setValue(LadderBlock.FACING, Direction.NORTH));
        set(lx, y + h - 1, lz, Blocks.AIR);
        for (int yy = y + 2; yy < y + h - 2; yy += 3) set(p.x(), yy, za, Blocks.IRON_BARS);
        fill(p.x(), y, zb, p.x(), y + 1, zb, Blocks.AIR);                           // a door at the foot
        set(p.x(), y + h + 1, p.z(), Blocks.LANTERN);
    }

    /** A bag-walled firing position: a horseshoe two high round a floor of dirt, open at the back, with the notch for the gun at the front. */
    private void nest(Piece p) {
        int y = p.y();
        Direction out = Direction.from2DDataValue(p.facing() & 3);
        Direction side = out.getClockWise();
        for (int a = -2; a <= 2; a++) {
            for (int depth = 0; depth <= 2; depth++) {
                boolean rim = Math.abs(a) == 2 || depth == 2;
                if (!rim) continue;
                int x = p.x() + side.getStepX() * a + out.getStepX() * (depth - 1), z = p.z() + side.getStepZ() * a + out.getStepZ() * (depth - 1);
                set(x, y - 1, z, pal.ground());
                set(x, y, z, pal.bag());
                if (!(depth == 2 && a == 0)) set(x, y + 1, z, pal.bag());              // the notch at the front stays one low
            }
        }
        int bx = p.x() - out.getStepX() * 2, bz = p.z() - out.getStepZ() * 2;
        set(bx, y, bz, Blocks.BARREL);
    }

    private void camp(Piece p) {
        int y = p.y();
        set(p.x(), y - 1, p.z(), Blocks.COARSE_DIRT);
        set(p.x(), y, p.z(), Blocks.CAMPFIRE);
        set(p.x() + 2, y, p.z(), slab(Blocks.SPRUCE_SLAB, SlabType.BOTTOM));
        set(p.x() - 2, y, p.z(), slab(Blocks.SPRUCE_SLAB, SlabType.BOTTOM));
        set(p.x(), y, p.z() + 2, slab(Blocks.SPRUCE_SLAB, SlabType.BOTTOM));
        set(p.x(), y, p.z() - 2, Blocks.BARREL);
    }

    private void mast(Piece p) {
        int y = p.y();
        fill(p.x() - 1, y - 1, p.z() - 1, p.x() + 1, y - 1, p.z() + 1, pal.floor());
        fill(p.x(), y, p.z(), p.x(), y + p.height() - 2, p.z(), Blocks.IRON_BARS);
        set(p.x(), y + p.height() - 1, p.z(), Blocks.LIGHTNING_ROD);
        set(p.x() + 1, y + 2, p.z(), Blocks.LANTERN.defaultBlockState().setValue(net.minecraft.world.level.block.LanternBlock.HANGING, false));
        set(p.x() + 1, y, p.z(), Blocks.CHEST);
    }

    private void light(Piece p) {
        fill(p.x(), p.y(), p.z(), p.x(), p.y() + p.height() - 2, p.z(), pal.fence());
        set(p.x(), p.y() + p.height() - 1, p.z(), Blocks.LANTERN);
    }

    /** An open vehicle shed: a hard floor, four posts, a back wall, no roof so the heights of any vehicle clear. */
    private void bay(Piece p) {
        int xa = x0(p), za = z0(p), xb = xa + p.w() - 1, zb = za + p.d() - 1, y = p.y();
        fill(xa, y - 1, za, xb, y - 1, zb, pal.floor());
        for (int[] c : new int[][]{{xa, za}, {xb, za}, {xa, zb}, {xb, zb}}) fill(c[0], y, c[1], c[0], y + p.height(), c[1], pal.frame());
        fill(xa, y, za, xb, y + 2, za, pal.wall());                                   // the back wall
        for (int x = xa + 1; x < xb; x += 4) set(x, y + 3, za, Blocks.LANTERN);
    }

    /** A trench: two wide, two deep, lined with duckboards, a lip of bags along each side. */
    private void trench(Piece p) {
        boolean alongX = (p.facing() & 1) == 0;
        int len = p.w(), wid = p.d(), y = p.y();
        for (int i = -len / 2; i <= len / 2; i++) {
            for (int j = 0; j < wid; j++) {
                int x = p.x() + (alongX ? i : j), z = p.z() + (alongX ? j : i);
                fill(x, y - 3, z, x, y + 1, z, Blocks.AIR);
                set(x, y - 3, z, slab(Blocks.SPRUCE_SLAB, SlabType.TOP));
                set(x, y - 4, z, Blocks.DIRT);
            }
            int lx = p.x() + (alongX ? i : -1), lz = p.z() + (alongX ? -1 : i);
            int rx = p.x() + (alongX ? i : wid), rz = p.z() + (alongX ? wid : i);
            set(lx, y, lz, pal.bag());
            set(rx, y, rz, pal.bag());
        }
    }
}
