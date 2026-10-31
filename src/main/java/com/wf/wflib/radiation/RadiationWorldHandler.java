package com.wf.wflib.radiation;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DoublePlantBlock;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.TallGrassBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;

// 1.7 surface decay; 26.2: Material.LEAVES -> instanceof.
public final class RadiationWorldHandler {

    /** NTM waste_earth/waste_leaves absent here => dirt / null (= air); a pack with waste blocks overrides. */
    private static final BlockState WASTE_EARTH = Blocks.DIRT.defaultBlockState();
    private static volatile @org.jetbrains.annotations.Nullable BlockState wasteLeaves;

    private RadiationWorldHandler() {
    }

    // 1.12.2: CE's 4-arg isLegacy overload (mycelium, sand -> trinitite) is callerless - CE
    // RadiationWorldHandler:55 against its only callers RadiationSystemNT:531,552, both 3-arg - so it is out
    // of scope rather than unported.
    static void decayBlock(ServerLevel world, BlockPos pos, BlockState state) {
        Block block = state.getBlock();
        if (block instanceof DoublePlantBlock) {
            BlockPos base = pos.immutable();
            DoubleBlockHalf half = state.getValue(DoublePlantBlock.HALF);
            BlockPos lower = half == DoubleBlockHalf.LOWER ? base : base.below();
            BlockPos upper = half == DoubleBlockHalf.LOWER ? base.above() : base;
            world.setBlock(upper, Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS);
            world.setBlock(lower, Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS);
            return;
        }
        if (block == Blocks.GRASS_BLOCK) {
            world.setBlock(pos, WASTE_EARTH, Block.UPDATE_CLIENTS);
            return;
        }
        // 1.7: Blocks.tallgrass is ONE block, metas 0/1/2 - TallGrassBlock is exactly its 26.2 pair
        if (block instanceof TallGrassBlock) {
            world.setBlock(pos, Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS);
            return;
        }
        BlockState leaves = wasteLeaves;
        if (block instanceof LeavesBlock && (leaves == null || block != leaves.getBlock())) {
            if (leaves != null && world.getRandom().nextInt(7) <= 5) {
                world.setBlock(pos, leaves, Block.UPDATE_CLIENTS);
            } else {
                world.setBlock(pos, Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS);
            }
        }
    }
}
