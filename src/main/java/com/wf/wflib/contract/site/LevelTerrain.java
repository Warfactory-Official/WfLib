package com.wf.wflib.contract.site;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BiomeTags;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.level.levelgen.Heightmap;

/** {@link Terrain} over a real level. Reading a column generates its chunk when {@code generate} is set. */
public final class LevelTerrain implements Terrain {
    private final ServerLevel level;
    private final boolean generate;

    public LevelTerrain(ServerLevel level, boolean generate) {
        this.level = level;
        this.generate = generate;
    }

    @Override
    public boolean available(int x, int z) {
        if (!level.getWorldBorder().isWithinBounds(x, z)) return false;
        if (generate) return level.getChunk(x >> 4, z >> 4, ChunkStatus.FULL, true) != null;
        return level.hasChunk(x >> 4, z >> 4);
    }

    @Override
    public int ground(int x, int z) {
        return level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
    }

    private BlockState top(int x, int z) {
        return level.getBlockState(new BlockPos(x, ground(x, z) - 1, z));
    }

    @Override
    public Surface surface(int x, int z) {
        BlockState s = top(x, z);
        if (s.getFluidState().is(net.minecraft.tags.FluidTags.LAVA)) return Surface.LAVA;
        if (!s.getFluidState().isEmpty()) return Surface.WATER;
        if (s.is(BlockTags.SAND) || s.is(Blocks.GRAVEL) || s.is(Blocks.POWDER_SNOW) || s.is(Blocks.SOUL_SAND) || s.is(Blocks.MUD)) return Surface.LOOSE;
        if (s.is(BlockTags.ICE)) return Surface.SLIPPERY;
        return Surface.SOLID;
    }

    @Override
    public boolean wet(int x, int z) {
        var biome = level.getBiome(new BlockPos(x, ground(x, z), z));
        return biome.is(BiomeTags.IS_OCEAN) || biome.is(BiomeTags.IS_RIVER) || biome.is(BiomeTags.IS_BEACH);
    }

    @Override
    public boolean hollow(int x, int z) {
        int y = ground(x, z) - 1;
        for (int d = 1; d <= 3; d++) {
            BlockState s = level.getBlockState(new BlockPos(x, y - d, z));
            if (s.isAir() || !s.getFluidState().isEmpty()) return true;
        }
        return false;
    }

    @Override
    public boolean cover(int x, int z) {
        int y = ground(x, z);
        BlockState s = level.getBlockState(new BlockPos(x, y, z));
        return s.is(BlockTags.LOGS) || s.is(BlockTags.LEAVES) || s.is(Blocks.COBBLESTONE) || s.is(Blocks.MOSSY_COBBLESTONE) || s.is(Blocks.STONE)
                || level.getHeight(Heightmap.Types.WORLD_SURFACE, x, z) > y + 1;
    }
}
