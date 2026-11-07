package com.wf.wflib.mixin.soldier;

import com.wf.wflib.soldier.SoldierSections;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** A changed block makes its section's collision copy stale (see {@link SoldierSections}). */
@Mixin(LevelChunk.class)
abstract class MixinLevelChunkSoldier {
    @Shadow
    @Final
    net.minecraft.world.level.Level level;

    @Inject(method = "setBlockState(Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;Z)Lnet/minecraft/world/level/block/state/BlockState;",
            at = @At("RETURN"))
    private void wflib$soldierSectionChanged(BlockPos pos, BlockState state, boolean moved, CallbackInfoReturnable<BlockState> cir) {
        if (cir.getReturnValue() != null && !level.isClientSide) SoldierSections.dirty(level.dimension(), pos);
    }
}
