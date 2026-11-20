package com.wf.wflib.contract.site;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BiomeTags;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * The materials a base is made of, picked by the climate it stands in. The look is a modern military one: poured and reinforced
 * concrete, steel, ballistic glass, hesco and sandbags, shipping containers. A mod with such blocks names them through
 * {@link SiteBlocks}; without one each has a vanilla stand-in, so the base still builds.
 *
 * @param heavy structural wall: reinforced concrete (perimeter, bunkers, towers)
 * @param body building walls: standard concrete or a regional stucco
 * @param trim plinth, corner posts, roofs: darker than the body
 * @param floor interior floor and paved aprons
 * @param slab roof edge and benches
 * @param glass ballistic glass for windows
 * @param steel hardened steel for frames, pylons and machinery
 * @param hesco hesco bastion: the filled-cage barrier
 * @param bag sandbags
 * @param ground what the graded pad is made of
 * @param light flush floodlight block
 * @param bed the barracks bed
 * @param containers shipping-container colours, in the order a stack takes them
 * @param bars the coping on the wall: iron bars, or a style's own (barbed wire)
 * @param weather per block, the worn variants a share of its placements are swapped for; empty for an unstyled base
 * @param weatherChance the share
 * @param plinth the ring at the foot of a building's wall
 * @param band the ring under the roof edge; null takes a container colour, as the plain look does
 * @param edge the strip along a lane, yard or helipad
 * @param mark the symbol on a pad and the dash on a lane
 * @param boomA one of the two blocks a gate post alternates
 * @param boomB the other
 * @param fence the chain-link of a fence wall
 * @param fencePost the posts of it
 * @param fenceWall the perimeter is a fence, not a wall of hesco or concrete
 * @param mixedWalls a wall may run stretches of hesco and of concrete; a styled base keeps to one material
 */
public record Palette(Block heavy, Block body, Block trim, Block floor, Block slab, Block glass, Block steel, Block hesco, Block bag,
                      Block ground, Block light, Block bed, List<Block> containers, Block bars, Map<Block, List<Block>> weather, float weatherChance,
                      Block plinth, Block band, Block edge, Block mark, Block boomA, Block boomB, Block fence, Block fencePost, boolean fenceWall,
                      boolean mixedWalls) {
    public enum Climate { TEMPERATE, ARID, COLD }

    private static final Map<Climate, Palette> CACHE = new EnumMap<>(Climate.class);

    /** The data-chosen block for a role, else the stand-in. Never called at class-init: registries are not filled yet. */
    private static Block mod(String role, Block fallback) {
        return SiteBlocks.get(role, fallback);
    }

    /** The data changed: palettes are rebuilt on next use. */
    static synchronized void reset() {
        CACHE.clear();
    }

    public static synchronized Palette of(Climate c) {
        return CACHE.computeIfAbsent(c, k -> switch (k) {
            case TEMPERATE -> new Palette(mod("heavy", Blocks.GRAY_CONCRETE), mod("body", Blocks.LIGHT_GRAY_CONCRETE),
                    Blocks.GRAY_CONCRETE, Blocks.POLISHED_ANDESITE, Blocks.SMOOTH_STONE_SLAB, mod("glass", Blocks.TINTED_GLASS),
                    mod("steel", Blocks.IRON_BLOCK), mod("hesco", Blocks.TUFF), mod("bag", Blocks.PACKED_MUD),
                    Blocks.COARSE_DIRT, Blocks.SEA_LANTERN, Blocks.GREEN_BED,
                    List.of(Blocks.GREEN_CONCRETE, Blocks.BLUE_CONCRETE, Blocks.GRAY_CONCRETE, Blocks.RED_CONCRETE), Blocks.IRON_BARS, Map.of(), 0f,
                    Blocks.GRAY_CONCRETE, null, Blocks.YELLOW_CONCRETE, Blocks.WHITE_CONCRETE, Blocks.RED_CONCRETE, Blocks.WHITE_CONCRETE, Blocks.IRON_BARS, Blocks.IRON_BARS, false, false);
            case ARID -> new Palette(mod("heavy", Blocks.GRAY_CONCRETE), Blocks.SMOOTH_SANDSTONE,
                    Blocks.TERRACOTTA, Blocks.CUT_SANDSTONE, Blocks.SMOOTH_SANDSTONE_SLAB, mod("glass", Blocks.TINTED_GLASS),
                    mod("steel", Blocks.IRON_BLOCK), mod("hesco", Blocks.TUFF), mod("bag", Blocks.PACKED_MUD),
                    Blocks.PACKED_MUD, Blocks.SEA_LANTERN, Blocks.BROWN_BED,
                    List.of(Blocks.ORANGE_CONCRETE, Blocks.BROWN_CONCRETE, Blocks.LIGHT_GRAY_CONCRETE, Blocks.YELLOW_CONCRETE), Blocks.IRON_BARS, Map.of(), 0f,
                    Blocks.GRAY_CONCRETE, null, Blocks.YELLOW_CONCRETE, Blocks.WHITE_CONCRETE, Blocks.RED_CONCRETE, Blocks.WHITE_CONCRETE, Blocks.IRON_BARS, Blocks.IRON_BARS, false, false);
            case COLD -> new Palette(mod("heavy", Blocks.GRAY_CONCRETE), Blocks.WHITE_CONCRETE,
                    Blocks.GRAY_CONCRETE, Blocks.POLISHED_ANDESITE, Blocks.SMOOTH_STONE_SLAB, mod("glass", Blocks.TINTED_GLASS),
                    mod("steel", Blocks.IRON_BLOCK), mod("hesco", Blocks.TUFF), mod("bag", Blocks.PACKED_MUD),
                    Blocks.SNOW_BLOCK, Blocks.SEA_LANTERN, Blocks.GRAY_BED,
                    List.of(Blocks.BLUE_CONCRETE, Blocks.RED_CONCRETE, Blocks.LIGHT_GRAY_CONCRETE, Blocks.CYAN_CONCRETE), Blocks.IRON_BARS, Map.of(), 0f,
                    Blocks.GRAY_CONCRETE, null, Blocks.YELLOW_CONCRETE, Blocks.WHITE_CONCRETE, Blocks.RED_CONCRETE, Blocks.WHITE_CONCRETE, Blocks.IRON_BARS, Blocks.IRON_BARS, false, false);
        });
    }

    /** The climate's palette in the look the base at {@code pos} takes (see {@link SiteStyles}). */
    public static Palette at(ServerLevel level, BlockPos pos) {
        return climate(level, pos).styled(SiteStyles.pick(pos.asLong() * 0x9E3779B97F4A7C15L >>> 20));
    }

    /** As {@link #at}, the look forced: {@code style} a style's id, "plain" for none, null to pick as usual. */
    public static Palette at(ServerLevel level, BlockPos pos, String style) {
        if (style == null) return at(level, pos);
        return climate(level, pos).styled("plain".equals(style) ? null : SiteStyles.byId(style));
    }

    private static Palette climate(ServerLevel level, BlockPos pos) {
        var biome = level.getBiome(pos);
        if (biome.is(BiomeTags.IS_BADLANDS) || biome.value().getBaseTemperature() > 1.5f) return of(Climate.ARID);
        if (biome.value().getBaseTemperature() < 0.15f) return of(Climate.COLD);
        return of(Climate.TEMPERATE);
    }

    /** This palette with {@code style}'s roles over its own; the palette itself when null. */
    public Palette styled(SiteStyles.Style style) {
        if (style == null) return this;
        Map<String, Block> r = style.roles();
        Block trim2 = r.getOrDefault("trim", trim);
        return new Palette(r.getOrDefault("heavy", heavy), r.getOrDefault("body", body), trim2, r.getOrDefault("floor", floor),
                r.getOrDefault("slab", slab), r.getOrDefault("glass", glass), r.getOrDefault("steel", steel), r.getOrDefault("hesco", hesco),
                r.getOrDefault("bag", bag), r.getOrDefault("ground", ground), r.getOrDefault("light", light), bed,
                style.containers().isEmpty() ? containers : style.containers(),
                r.getOrDefault("bars", bars), style.weather(), style.weatherChance(),
                r.getOrDefault("plinth", trim2), r.getOrDefault("band", trim2), r.getOrDefault("edge", edge), r.getOrDefault("mark", mark),
                r.getOrDefault("boomA", boomA), r.getOrDefault("boomB", boomB), r.getOrDefault("fence", fence), r.getOrDefault("fencePost", fencePost),
                style.fenceWall(), style.mixedWalls());
    }

    /** {@code state}, or a worn variant of it: a share of a weathering block's placements. Draws from {@code rng} only for a block that has variants. */
    public net.minecraft.world.level.block.state.BlockState weathered(net.minecraft.world.level.block.state.BlockState state, net.minecraft.util.RandomSource rng) {
        if (weather.isEmpty()) return state;
        List<Block> worn = weather.get(state.getBlock());
        if (worn == null || rng.nextFloat() >= weatherChance) return state;
        return worn.get(rng.nextInt(worn.size())).defaultBlockState();
    }
}
