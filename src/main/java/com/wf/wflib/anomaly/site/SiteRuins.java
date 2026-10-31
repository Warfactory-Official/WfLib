package com.wf.wflib.anomaly.site;

import com.wf.wflib.anomaly.site.SiteLayout.Pad;
import com.wf.wflib.anomaly.site.SiteLayout.Ruin;
import com.wf.wflib.anomaly.site.SiteLayout.Surface;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Vec3i;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ChainBlock;
import net.minecraft.world.level.block.LadderBlock;
import net.minecraft.world.level.block.RailBlock;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.RailShape;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import com.wf.wflib.anomaly.api.SitePlan;
import com.wf.wflib.anomaly.api.SiteStyle;

/**
 * Ruins in a site: what people left before the anomalies came. Each claims level ground (a pad) away from the
 * motif's bowls and trenches, sets its blocks as props (or a vanilla template, rotted), and marks the spots that
 * matter in it: a street, a doorway, a bunker's room.
 */
final class SiteRuins {
    private SiteRuins() {
    }

    static void plan(SiteLayout l, SitePlan.Overlay overlay) {
        switch (overlay) {
            case VILLAGE_RUIN -> village(l);
            case CAMP -> camp(l);
            case WATCHTOWER -> watchtower(l);
            case TRAIN_WRECK -> train(l);
            case RADIO_MAST -> mast(l);
            case TEMPLE -> temple(l);
            case BUNKER -> bunker(l);
        }
    }

    /** A level rectangle {@code w} x {@code d} (x by z) for a ruin, between {@code from} and {@code to} outlines out. */
    @Nullable
    static Pad claim(SiteLayout l, int w, int d, double from, double to) {
        for (int tries = 0; tries < 40; tries++) {
            double a = l.random.nextDouble() * Mth.TWO_PI;
            double out = l.plan.radius() * (from + (to - from) * l.random.nextDouble());
            int x0 = l.cx + (int) Math.round(Math.cos(a) * out) - w / 2;
            int z0 = l.cz + (int) Math.round(Math.sin(a) * out) - d / 2;
            int x1 = x0 + w - 1;
            int z1 = z0 + d - 1;
            int[][] corners = {{x0, z0}, {x1, z0}, {x0, z1}, {x1, z1}, {(x0 + x1) / 2, (z0 + z1) / 2}};
            int lo = Integer.MAX_VALUE;
            int hi = Integer.MIN_VALUE;
            boolean ok = true;
            for (int[] c : corners) {
                SiteLayout.Column col = l.column(c[0], c[1]);
                ok &= col.kind() == Surface.PLAIN && l.u(c[0], c[1]) < 0.9D && !l.terrain.wet(c[0], c[1])
                        && !l.inPad(c[0], c[1]);
                lo = Math.min(lo, col.y());
                hi = Math.max(hi, col.y());
            }
            if (ok && hi - lo <= 4) {
                Pad p = new Pad(x0, z0, x1, z1, (lo + hi + 1) / 2);
                l.pads.add(p);
                return p;
            }
        }
        return null;
    }

    private static BlockState b(Block block) {
        return block.defaultBlockState();
    }

    /** Free spots round a pad's edge (doorways, the street), {@code weight} each. */
    private static void around(SiteLayout l, Pad p, double weight) {
        for (int x = p.minX() - 2; x <= p.maxX() + 2; x += 2) {
            for (int z = p.minZ() - 2; z <= p.maxZ() + 2; z += 2) {
                boolean edge = x < p.minX() || x > p.maxX() || z < p.minZ() || z > p.maxZ();
                if (edge && l.u(x, z) < 0.9D && !l.inPadStrict(x, z)) {
                    l.spot(x, z, weight);
                }
            }
        }
    }

    // --- village ----------------------------------------------------------------------------------------------------

    private static final String[] PLAINS = {"plains_small_house_1", "plains_small_house_2", "plains_small_house_3",
            "plains_small_house_4", "plains_small_house_5", "plains_small_house_6", "plains_small_house_7",
            "plains_small_house_8", "plains_medium_house_1", "plains_medium_house_2", "plains_butcher_shop_1",
            "plains_fisher_cottage_1", "plains_masons_house_1", "plains_tool_smith_1"};
    private static final String[] DESERT = {"desert_small_house_1", "desert_small_house_2", "desert_small_house_3",
            "desert_small_house_4", "desert_small_house_5", "desert_small_house_6", "desert_small_house_7",
            "desert_small_house_8", "desert_medium_house_1", "desert_medium_house_2", "desert_mason_1",
            "desert_tool_smith_1"};
    private static final String[] SAVANNA = {"savanna_small_house_1", "savanna_small_house_2", "savanna_small_house_3",
            "savanna_small_house_4", "savanna_small_house_5", "savanna_small_house_6", "savanna_small_house_7",
            "savanna_small_house_8", "savanna_medium_house_1", "savanna_medium_house_2", "savanna_mason_1"};
    private static final String[] SNOWY = {"snowy_small_house_1", "snowy_small_house_2", "snowy_small_house_3",
            "snowy_small_house_4", "snowy_small_house_5", "snowy_small_house_6", "snowy_small_house_7",
            "snowy_small_house_8", "snowy_medium_house_1", "snowy_medium_house_2", "snowy_medium_house_3"};
    private static final String[] TAIGA = {"taiga_small_house_1", "taiga_small_house_2", "taiga_small_house_3",
            "taiga_small_house_4", "taiga_small_house_5", "taiga_medium_house_1", "taiga_medium_house_2",
            "taiga_medium_house_3", "taiga_medium_house_4", "taiga_masons_house_1"};

    static String[] houses(SiteStyle style) {
        return switch (style) {
            case DESERT, BADLANDS -> DESERT;
            case SAVANNA -> SAVANNA;
            case SNOWY -> SNOWY;
            case TAIGA -> TAIGA;
            case PLAINS, JUNGLE, SWAMP -> PLAINS;
        };
    }

    /**
     * Two to four of the biome's village houses, rotted the further in the tier (integrity 0.85 at tier 1 to 0.55 at
     * 4) and mossed, round a beaten street where the anomalies sit.
     */
    private static void village(SiteLayout l) {
        String[] names = houses(l.style);
        String folder = names[0].substring(0, names[0].indexOf('_'));
        List<Pad> placed = new ArrayList<>();
        int n = 2 + l.random.nextInt(3);
        for (int i = 0; i < n * 3 && placed.size() < n; i++) {
            ResourceLocation id = ResourceLocation.withDefaultNamespace("village/" + folder + "/houses/"
                    + names[l.random.nextInt(names.length)]);
            Rotation rot = Rotation.getRandom(l.random);
            Vec3i size = l.templates.size(id);
            if (size == null) {
                continue;
            }
            boolean quarter = rot == Rotation.CLOCKWISE_90 || rot == Rotation.COUNTERCLOCKWISE_90;
            int w = quarter ? size.getZ() : size.getX();
            int d = quarter ? size.getX() : size.getZ();
            Pad p = claim(l, w, d, 0.15D, 0.7D);
            if (p == null) {
                continue;
            }
            placed.add(p);
            // A template's origin is its min corner before rotation: rotated about it, shift back into the pad.
            int ox = switch (rot) {
                case CLOCKWISE_90, CLOCKWISE_180 -> p.maxX();
                default -> p.minX();
            };
            int oz = switch (rot) {
                case CLOCKWISE_180, COUNTERCLOCKWISE_90 -> p.maxZ();
                default -> p.minZ();
            };
            float integrity = 0.9F - 0.08F * l.plan.tier() - 0.05F * l.random.nextFloat();
            l.ruins.add(new Ruin(id, new BlockPos(ox, p.y(), oz), rot, integrity, 0.25F + 0.1F * l.plan.tier()));
        }
        // The street: a beaten path from each house to the middle.
        for (Pad p : placed) {
            int px = (p.minX() + p.maxX()) / 2;
            int pz = (p.minZ() + p.maxZ()) / 2;
            double len = Math.hypot(l.cx - px, l.cz - pz);
            for (double s = 0.0D; s <= len; s += 0.7D) {
                int x = Mth.floor(px + (l.cx - px) * s / len);
                int z = Mth.floor(pz + (l.cz - pz) * s / len);
                if (!l.inPadStrict(x, z) && l.column(x, z).kind() == Surface.PLAIN) {
                    l.put(x, l.height(x, z), z, SiteLayout.hash(x, z, 71) < 0.7D ? Blocks.DIRT_PATH : Blocks.GRAVEL);
                    if (Math.round(s) % 3 == 0) {
                        l.spot(x, z, 4.0D);
                    }
                }
            }
            around(l, p, 2.0D);
        }
    }

    // --- camp -------------------------------------------------------------------------------------------------------

    /** Tents round a cold fire, crates and barrels, a log to sit on: a camp that did not make it. */
    private static void camp(SiteLayout l) {
        Pad p = claim(l, 11, 11, 0.0D, 0.45D);
        if (p == null) {
            return;
        }
        int cx = (p.minX() + p.maxX()) / 2;
        int cz = (p.minZ() + p.maxZ()) / 2;
        int y = p.y();
        l.put(cx, y + 1, cz, Blocks.CAMPFIRE.defaultBlockState().setValue(net.minecraft.world.level.block.CampfireBlock.LIT,
                false));
        Block[] cloth = {Blocks.GREEN_WOOL, Blocks.BROWN_WOOL, Blocks.GRAY_WOOL, Blocks.LIGHT_GRAY_WOOL};
        Direction[] around = {Direction.NORTH, Direction.EAST, Direction.SOUTH, Direction.WEST};
        int tents = 2 + l.random.nextInt(2);
        for (int i = 0; i < tents; i++) {
            Direction d = around[(i + l.random.nextInt(4)) % 4];
            tent(l, cx + d.getStepX() * 4, y + 1, cz + d.getStepZ() * 4, d.getClockWise(),
                    cloth[l.random.nextInt(cloth.length)], i == 0 && l.random.nextFloat() < 0.5F);
        }
        for (int i = 0; i < 4; i++) {
            int x = p.minX() + 1 + l.random.nextInt(9);
            int z = p.minZ() + 1 + l.random.nextInt(9);
            if (!l.hasProp(x, y + 1, z) && Math.abs(x - cx) + Math.abs(z - cz) > 1) {
                l.put(x, y + 1, z, l.random.nextFloat() < 0.5F ? Blocks.BARREL : Blocks.COMPOSTER);
            }
        }
        BlockState seat = Blocks.SPRUCE_LOG.defaultBlockState().setValue(net.minecraft.world.level.block.RotatedPillarBlock.AXIS,
                Direction.Axis.X);
        for (int k = -1; k <= 1; k++) {
            l.put(cx + k, y + 1, cz + 2, seat);
        }
        around(l, p, 3.0D);
    }

    /** An A-frame of cloth over a ridge of fence, {@code collapsed}: one side down. */
    private static void tent(SiteLayout l, int x, int y, int z, Direction along, Block cloth, boolean collapsed) {
        Direction side = along.getClockWise();
        for (int a = -1; a <= 1; a++) {
            int ax = x + along.getStepX() * a;
            int az = z + along.getStepZ() * a;
            if (!collapsed) {
                l.put(ax, y + 1, az, Blocks.SPRUCE_FENCE);
            }
            for (int s = -1; s <= 1; s += 2) {
                l.put(ax + side.getStepX() * s, y, az + side.getStepZ() * s, cloth);
                if (!collapsed || s < 0) {
                    l.put(ax + side.getStepX() * s, y + 1, az + side.getStepZ() * s,
                            collapsed ? Blocks.AIR : cloth);
                }
            }
            l.put(ax, y + 2, az, collapsed ? Blocks.AIR : cloth);
        }
    }

    // --- watchtower -------------------------------------------------------------------------------------------------

    /** A 3x3 wooden tower on log posts, a ladder up one face, a platform with a rail; some posts gone, it leans. */
    private static void watchtower(SiteLayout l) {
        Pad p = claim(l, 5, 5, 0.1D, 0.6D);
        if (p == null) {
            return;
        }
        int x0 = p.minX() + 1;
        int z0 = p.minZ() + 1;
        int y = p.y() + 1;
        int h = 8 + l.random.nextInt(5);
        int broken = l.random.nextInt(4);
        int[][] posts = {{0, 0}, {2, 0}, {0, 2}, {2, 2}};
        for (int i = 0; i < 4; i++) {
            int top = i == broken ? h / 2 + l.random.nextInt(h / 3) : h;
            for (int k = 0; k < top; k++) {
                l.put(x0 + posts[i][0], y + k, z0 + posts[i][1], Blocks.STRIPPED_SPRUCE_LOG);
            }
        }
        for (int k = 2; k < h; k += 3) {
            l.put(x0 + 1, y + k, z0, Blocks.SPRUCE_FENCE);
            l.put(x0 + 1, y + k, z0 + 2, Blocks.SPRUCE_FENCE);
        }
        for (int dx = 0; dx <= 2; dx++) {
            for (int dz = 0; dz <= 2; dz++) {
                if (SiteLayout.hash(x0 + dx, z0 + dz, 81) < 0.8D) {
                    l.put(x0 + dx, y + h, z0 + dz, Blocks.SPRUCE_PLANKS);
                }
                boolean rim = dx != 1 || dz != 1;
                if (rim && SiteLayout.hash(x0 + dx, z0 + dz, 82) < 0.7D) {
                    l.put(x0 + dx, y + h + 1, z0 + dz, Blocks.SPRUCE_FENCE);
                }
            }
        }
        BlockState ladder = Blocks.LADDER.defaultBlockState().setValue(LadderBlock.FACING, Direction.NORTH);
        for (int k = 0; k < h; k++) {
            if (SiteLayout.hash(x0, z0, 83 + k) < 0.85D) {
                l.put(x0 + 1, y + k, z0 - 1, ladder);
            }
        }
        // A rung or two and planks fallen at its foot.
        for (int i = 0; i < 4; i++) {
            int x = x0 - 2 + l.random.nextInt(7);
            int z = z0 - 2 + l.random.nextInt(7);
            if (!l.hasProp(x, l.height(x, z) + 1, z)) {
                l.put(x, l.height(x, z) + 1, z, Blocks.SPRUCE_SLAB);
            }
        }
        around(l, p, 3.0D);
    }

    // --- train ------------------------------------------------------------------------------------------------------

    /** A stretch of track on a gravel bed across the site, wagons off it, one on its side. */
    private static void train(SiteLayout l) {
        double a = l.random.nextDouble() * Math.PI;
        boolean alongX = Math.abs(Math.cos(a)) > Math.abs(Math.sin(a));
        int half = (int) (l.plan.radius() * 0.8D);
        int y = l.baseY;
        Pad bed = alongX ? new Pad(l.cx - half, l.cz - 1, l.cx + half, l.cz + 1, y)
                : new Pad(l.cx - 1, l.cz - half, l.cx + 1, l.cz + half, y);
        l.pads.add(bed);
        BlockState rail = Blocks.RAIL.defaultBlockState().setValue(RailBlock.SHAPE,
                alongX ? RailShape.EAST_WEST : RailShape.NORTH_SOUTH);
        for (int s = -half; s <= half; s++) {
            int x = alongX ? l.cx + s : l.cx;
            int z = alongX ? l.cz : l.cz + s;
            for (int w = -1; w <= 1; w++) {
                l.put(alongX ? x : x + w, y, alongX ? z + w : z, Blocks.GRAVEL);
            }
            // Torn up in places.
            if (SiteLayout.hash(x, z, 91) < 0.75D) {
                l.put(x, y + 1, z, rail);
            }
            if (Math.abs(s) % 4 == 0) {
                l.spot(alongX ? x : x + 3, alongX ? z + 3 : z, 3.0D);
            }
        }
        int wagons = 2 + l.random.nextInt(2);
        for (int i = 0; i < wagons; i++) {
            int along = -half / 2 + i * 8 + l.random.nextInt(3);
            int off = (i % 2 == 0 ? 3 : -3) + l.random.nextInt(2);
            int x = alongX ? l.cx + along : l.cx + off;
            int z = alongX ? l.cz + off : l.cz + along;
            wagon(l, x, l.height(x, z) + 1, z, alongX ^ (i == 1), i == 1);
        }
    }

    /** A boxcar: iron frame, plank walls, an open door; {@code onSide}: tipped over, lower and wider. */
    private static void wagon(SiteLayout l, int x, int y, int z, boolean alongX, boolean onSide) {
        int len = 6;
        int wid = onSide ? 3 : 2;
        int hgt = onSide ? 2 : 3;
        for (int a = 0; a < len; a++) {
            for (int w = 0; w <= wid; w++) {
                for (int h = 0; h <= hgt; h++) {
                    boolean shell = a == 0 || a == len - 1 || w == 0 || w == wid || h == 0 || h == hgt;
                    if (!shell) {
                        continue;
                    }
                    int bx = alongX ? x + a : x + w;
                    int bz = alongX ? z + w : z + a;
                    boolean frame = (a == 0 || a == len - 1) && (h == 0 || h == hgt);
                    boolean door = !onSide && w == 0 && a >= 2 && a <= 3 && h >= 1;
                    if (door || SiteLayout.hash(bx, bz, 100 + h) < 0.12D) {
                        continue;
                    }
                    l.put(bx, y + h, bz, frame || h == 0 ? Blocks.IRON_BLOCK
                            : SiteLayout.hash(bx, bz, h) < 0.5D ? Blocks.SPRUCE_PLANKS : Blocks.STRIPPED_SPRUCE_WOOD);
                }
            }
        }
    }

    // --- radio mast -------------------------------------------------------------------------------------------------

    /** A lattice mast of iron bars on a concrete footing, rods at the top, chains for guys; a hut at its foot. */
    private static void mast(SiteLayout l) {
        Pad p = claim(l, 7, 7, 0.0D, 0.5D);
        if (p == null) {
            return;
        }
        int x0 = p.minX() + 2;
        int z0 = p.minZ() + 2;
        int y = p.y() + 1;
        int h = 16 + l.random.nextInt(9);
        for (int dx = 0; dx <= 2; dx++) {
            for (int dz = 0; dz <= 2; dz++) {
                l.put(x0 + dx, y - 1, z0 + dz, Blocks.GRAY_CONCRETE);
            }
        }
        for (int k = 0; k < h; k++) {
            for (int[] c : new int[][] {{0, 0}, {2, 0}, {0, 2}, {2, 2}}) {
                l.put(x0 + c[0], y + k, z0 + c[1], Blocks.IRON_BARS);
            }
            if (k % 4 == 0) {
                for (int s = 0; s <= 2; s++) {
                    l.put(x0 + s, y + k, z0, Blocks.IRON_BARS);
                    l.put(x0 + s, y + k, z0 + 2, Blocks.IRON_BARS);
                }
            }
        }
        l.put(x0 + 1, y + h, z0 + 1, Blocks.LIGHTNING_ROD);
        l.put(x0 + 1, y + h - 1, z0 + 1, Blocks.IRON_BARS);
        BlockState chain = Blocks.CHAIN.defaultBlockState().setValue(ChainBlock.AXIS, Direction.Axis.Y);
        for (Direction d : Direction.Plane.HORIZONTAL) {
            // Guys: chain hanging from the mast's middle, down to a block anchor.
            int ax = x0 + 1 + d.getStepX() * 6;
            int az = z0 + 1 + d.getStepZ() * 6;
            int ay = l.height(ax, az);
            l.put(ax, ay + 1, az, Blocks.COBBLESTONE_WALL);
            for (int k = 2; k < 5; k++) {
                l.put(ax - d.getStepX(), ay + k, az - d.getStepZ(), chain);
            }
        }
        around(l, p, 3.0D);
    }

    // --- temple -----------------------------------------------------------------------------------------------------

    /**
     * A stepped stone platform with a shrine of pillars on top, in the biome's stone (mossy in the jungle,
     * sandstone in the desert), broken: blocks missing, pillars fallen.
     */
    private static void temple(SiteLayout l) {
        int size = 11 + 2 * l.random.nextInt(3);
        Pad p = claim(l, size, size, 0.0D, 0.35D);
        if (p == null) {
            return;
        }
        Block main;
        Block worn;
        Block step;
        switch (l.style) {
            case DESERT -> {
                main = Blocks.SANDSTONE;
                worn = Blocks.CUT_SANDSTONE;
                step = Blocks.SANDSTONE_STAIRS;
            }
            case BADLANDS -> {
                main = Blocks.RED_SANDSTONE;
                worn = Blocks.CUT_RED_SANDSTONE;
                step = Blocks.RED_SANDSTONE_STAIRS;
            }
            case JUNGLE, SWAMP -> {
                main = Blocks.MOSSY_COBBLESTONE;
                worn = Blocks.MOSSY_STONE_BRICKS;
                step = Blocks.MOSSY_COBBLESTONE_STAIRS;
            }
            default -> {
                main = Blocks.STONE_BRICKS;
                worn = Blocks.CRACKED_STONE_BRICKS;
                step = Blocks.STONE_BRICK_STAIRS;
            }
        }
        int y = p.y();
        int levels = 2 + l.random.nextInt(2);
        for (int lv = 0; lv < levels; lv++) {
            for (int x = p.minX() + lv * 2; x <= p.maxX() - lv * 2; x++) {
                for (int z = p.minZ() + lv * 2; z <= p.maxZ() - lv * 2; z++) {
                    double h = SiteLayout.hash(x, z, 110 + lv);
                    if (h < 0.08D) {
                        continue;
                    }
                    l.put(x, y + 1 + lv, z, h < 0.6D ? main : worn);
                }
            }
        }
        // Stairs up the south face's middle.
        int mid = (p.minX() + p.maxX()) / 2;
        for (int lv = 0; lv < levels; lv++) {
            for (int dx = -1; dx <= 1; dx++) {
                l.put(mid + dx, y + 1 + lv, p.maxZ() - lv * 2 + 1,
                        step.defaultBlockState().setValue(StairBlock.FACING, Direction.NORTH));
            }
        }
        // The shrine on top: pillars at the corners, a lintel, an altar.
        int top = y + levels + 1;
        int ix0 = p.minX() + levels * 2;
        int ix1 = p.maxX() - levels * 2;
        int iz0 = p.minZ() + levels * 2;
        int iz1 = p.maxZ() - levels * 2;
        for (int[] c : new int[][] {{ix0, iz0}, {ix1, iz0}, {ix0, iz1}, {ix1, iz1}}) {
            int ph = SiteLayout.hash(c[0], c[1], 120) < 0.3D ? 1 : 4;
            for (int k = 0; k < ph; k++) {
                l.put(c[0], top + k, c[1], Blocks.CHISELED_STONE_BRICKS);
            }
        }
        l.put((ix0 + ix1) / 2, top, (iz0 + iz1) / 2, worn);
        for (int x = ix0 + 1; x < ix1; x += 2) {
            l.spots.add(new SiteLayout.Spot(x, top, iz0 + 1, 4.0D, false));
        }
        l.spots.add(new SiteLayout.Spot((ix0 + ix1) / 2, top + 1, (iz0 + iz1) / 2 - 1, 5.0D, false));
        around(l, p, 2.0D);
    }

    // --- bunker -----------------------------------------------------------------------------------------------------

    /**
     * A concrete hatch with stairs down to a room under the site: its anomaly waits in there, its artefact with it.
     */
    private static void bunker(SiteLayout l) {
        Pad p = claim(l, 5, 9, 0.1D, 0.5D);
        if (p == null) {
            return;
        }
        int x0 = p.minX();
        int z0 = p.minZ();
        int y = p.y();
        // The hatch: a concrete box over the stair head, open to the south.
        for (int dx = 0; dx < 5; dx++) {
            for (int dz = 0; dz < 4; dz++) {
                for (int dy = 1; dy <= 3; dy++) {
                    boolean wall = dx == 0 || dx == 4 || dz == 0 || dy == 3;
                    boolean door = dz == 3 || (dx >= 1 && dx <= 3 && dz >= 1 && dy < 3);
                    if (wall && !(dz == 3 && dx >= 1 && dx <= 3 && dy < 3)) {
                        l.put(x0 + dx, y + dy, z0 + dz, Blocks.GRAY_CONCRETE);
                    } else if (door) {
                        l.put(x0 + dx, y + dy, z0 + dz, Blocks.AIR);
                    }
                }
            }
        }
        // Stairs down northward from inside the hatch, then the room.
        int depth = 7;
        for (int s = 0; s < depth; s++) {
            for (int dx = 1; dx <= 3; dx++) {
                int z = z0 + 1 + s;
                l.put(x0 + dx, y - s, z, Blocks.STONE_BRICK_STAIRS.defaultBlockState()
                        .setValue(StairBlock.FACING, Direction.NORTH));
                for (int h = 1; h <= 3; h++) {
                    l.put(x0 + dx, y - s + h, z, Blocks.AIR);
                }
            }
        }
        int ry = y - depth;
        int rz0 = z0 + depth;
        for (int dx = -2; dx <= 6; dx++) {
            for (int dz = 0; dz <= 6; dz++) {
                for (int h = -1; h <= 4; h++) {
                    boolean shell = dx == -2 || dx == 6 || dz == 6 || h == -1 || h == 4;
                    l.put(x0 + dx, ry + h, rz0 + dz, shell ? (SiteLayout.hash(dx, dz, h) < 0.8D ? Blocks.GRAY_CONCRETE
                            : Blocks.CRACKED_STONE_BRICKS) : Blocks.AIR);
                }
            }
        }
        l.put(x0 + 2, ry + 3, rz0 + 3, Blocks.CHAIN);
        l.spots.add(new SiteLayout.Spot(x0 + 2, ry, rz0 + 4, 8.0D, false));
    }
}
