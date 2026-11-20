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
 */
public record Palette(Block heavy, Block body, Block trim, Block floor, Block slab, Block glass, Block steel, Block hesco, Block bag,
                      Block ground, Block light, Block bed, List<Block> containers) {
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
                    List.of(Blocks.GREEN_CONCRETE, Blocks.BLUE_CONCRETE, Blocks.GRAY_CONCRETE, Blocks.RED_CONCRETE));
            case ARID -> new Palette(mod("heavy", Blocks.GRAY_CONCRETE), Blocks.SMOOTH_SANDSTONE,
                    Blocks.TERRACOTTA, Blocks.CUT_SANDSTONE, Blocks.SMOOTH_SANDSTONE_SLAB, mod("glass", Blocks.TINTED_GLASS),
                    mod("steel", Blocks.IRON_BLOCK), mod("hesco", Blocks.TUFF), mod("bag", Blocks.PACKED_MUD),
                    Blocks.PACKED_MUD, Blocks.SEA_LANTERN, Blocks.BROWN_BED,
                    List.of(Blocks.ORANGE_CONCRETE, Blocks.BROWN_CONCRETE, Blocks.LIGHT_GRAY_CONCRETE, Blocks.YELLOW_CONCRETE));
            case COLD -> new Palette(mod("heavy", Blocks.GRAY_CONCRETE), Blocks.WHITE_CONCRETE,
                    Blocks.GRAY_CONCRETE, Blocks.POLISHED_ANDESITE, Blocks.SMOOTH_STONE_SLAB, mod("glass", Blocks.TINTED_GLASS),
                    mod("steel", Blocks.IRON_BLOCK), mod("hesco", Blocks.TUFF), mod("bag", Blocks.PACKED_MUD),
                    Blocks.SNOW_BLOCK, Blocks.SEA_LANTERN, Blocks.GRAY_BED,
                    List.of(Blocks.BLUE_CONCRETE, Blocks.RED_CONCRETE, Blocks.LIGHT_GRAY_CONCRETE, Blocks.CYAN_CONCRETE));
        });
    }

    public static Palette at(ServerLevel level, BlockPos pos) {
        var biome = level.getBiome(pos);
        if (biome.is(BiomeTags.IS_BADLANDS) || biome.value().getBaseTemperature() > 1.5f) return of(Climate.ARID);
        if (biome.value().getBaseTemperature() < 0.15f) return of(Climate.COLD);
        return of(Climate.TEMPERATE);
    }
}
