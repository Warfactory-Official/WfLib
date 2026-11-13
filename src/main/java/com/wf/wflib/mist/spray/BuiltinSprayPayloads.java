package com.wf.wflib.mist.spray;

import com.wf.wflib.mist.api.WFFluids;
import com.wf.wflib.warhead.api.AgentOrangeWarhead;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CampfireBlock;
import net.minecraft.world.level.block.FarmBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.Fluids;

/** Defoliant, water, lava, kerosene. */
public final class BuiltinSprayPayloads {

    private BuiltinSprayPayloads() {
    }

    public static void register() {
        SprayPayloads.register(WFFluids.AGENT_ORANGE.getId(), SprayPayload.INERT.blocks(BuiltinSprayPayloads::defoliant));
        SprayPayloads.register(id(Fluids.WATER), SprayPayload.INERT.blocks(BuiltinSprayPayloads::water));
        SprayPayloads.register(id(Fluids.LAVA), SprayPayload.INERT.blocks(BuiltinSprayPayloads::incendiary));
        SprayPayloads.register(WFFluids.KEROSENE.getId(), SprayPayload.INERT.blocks(BuiltinSprayPayloads::kerosene));
    }

    private static ResourceLocation id(Fluid fluid) {
        return BuiltInRegistries.FLUID.getKey(fluid);
    }

    private static boolean burning(BlockState state) {
        return state.is(Blocks.FIRE) || state.is(Blocks.SOUL_FIRE)
                || state.getBlock() instanceof CampfireBlock && state.getValue(CampfireBlock.LIT);
    }

    private static SprayEffect.Touch defoliant(ServerLevel level, BlockPos pos, BlockState state, Fluid fluid) {
        if (state.is(AgentOrangeWarhead.TARGETS)) {
            return SprayEffect.Touch.edit(pos, Blocks.AIR.defaultBlockState(), false);
        }
        return SprayEffect.plain(level, pos, state);
    }

    private static SprayEffect.Touch water(ServerLevel level, BlockPos pos, BlockState state, Fluid fluid) {
        if (state.is(Blocks.FIRE) || state.is(Blocks.SOUL_FIRE)) {
            return SprayEffect.Touch.edit(pos, Blocks.AIR.defaultBlockState(), false);
        }
        if (state.is(Blocks.FARMLAND) && state.getValue(FarmBlock.MOISTURE) < FarmBlock.MAX_MOISTURE) {
            return SprayEffect.Touch.edit(pos, state.setValue(FarmBlock.MOISTURE, FarmBlock.MAX_MOISTURE), true);
        }
        if (state.getBlock() instanceof CampfireBlock && state.getValue(CampfireBlock.LIT)) {
            return SprayEffect.Touch.edit(pos, state.setValue(CampfireBlock.LIT, false), true);
        }
        return SprayEffect.plain(level, pos, state);
    }

    /** Fire above the first solid. */
    private static SprayEffect.Touch incendiary(ServerLevel level, BlockPos pos, BlockState state, Fluid fluid) {
        SprayEffect.Touch plain = SprayEffect.plain(level, pos, state);
        if (!plain.stop() || state.isAir() || !state.getFluidState().isEmpty()) {
            return plain;
        }
        return fireAbove(level, pos, plain);
    }

    /** Burns only where the column meets flame; no flame => wets the ground. */
    private static SprayEffect.Touch kerosene(ServerLevel level, BlockPos pos, BlockState state, Fluid fluid) {
        if (burning(state)) {
            return fireAbove(level, pos, SprayEffect.Touch.STOP);
        }
        return SprayEffect.plain(level, pos, state);
    }

    private static SprayEffect.Touch fireAbove(ServerLevel level, BlockPos pos, SprayEffect.Touch otherwise) {
        BlockPos above = pos.above();
        if (level.getBlockState(above).isAir() && Blocks.FIRE.defaultBlockState().canSurvive(level, above)) {
            return SprayEffect.Touch.edit(above, Blocks.FIRE.defaultBlockState(), true);
        }
        return otherwise;
    }
}
