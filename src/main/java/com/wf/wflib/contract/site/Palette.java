package com.wf.wflib.contract.site;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BiomeTags;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;

/** The materials a base is made of, picked by the climate it stands in. Every block is a plain vanilla one so any pack renders it. */
public record Palette(Block wall, Block slab, Block fence, Block floor, Block frame, Block roof, Block roofSlab, Block bag, Block ground, Block cloth) {
    public static final Palette TEMPERATE = new Palette(Blocks.STONE_BRICKS, Blocks.STONE_BRICK_SLAB, Blocks.COBBLESTONE_WALL, Blocks.SMOOTH_STONE,
            Blocks.DARK_OAK_LOG, Blocks.SPRUCE_PLANKS, Blocks.SPRUCE_SLAB, Blocks.COARSE_DIRT, Blocks.COARSE_DIRT, Blocks.GREEN_WOOL);
    public static final Palette ARID = new Palette(Blocks.SANDSTONE, Blocks.SANDSTONE_SLAB, Blocks.SANDSTONE_WALL, Blocks.SMOOTH_SANDSTONE,
            Blocks.SPRUCE_LOG, Blocks.SPRUCE_PLANKS, Blocks.SPRUCE_SLAB, Blocks.SANDSTONE, Blocks.PACKED_MUD, Blocks.BROWN_WOOL);
    public static final Palette COLD = new Palette(Blocks.DEEPSLATE_BRICKS, Blocks.DEEPSLATE_BRICK_SLAB, Blocks.DEEPSLATE_BRICK_WALL, Blocks.POLISHED_DEEPSLATE,
            Blocks.SPRUCE_LOG, Blocks.SPRUCE_PLANKS, Blocks.SPRUCE_SLAB, Blocks.SNOW_BLOCK, Blocks.COARSE_DIRT, Blocks.WHITE_WOOL);

    public static Palette at(ServerLevel level, BlockPos pos) {
        var biome = level.getBiome(pos);
        if (biome.is(BiomeTags.IS_BADLANDS) || biome.value().getBaseTemperature() > 1.5f) return ARID;
        if (biome.value().getBaseTemperature() < 0.15f) return COLD;
        return TEMPERATE;
    }
}
