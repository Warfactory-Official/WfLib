package com.wf.wflib.mixin;

import com.wf.wflib.mine.MineSectionWatch;
import com.wf.wflib.radiation.RadiationCarrier;
import com.wf.wflib.radiation.RadiationSystemNT;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Unique;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Bumps the revision of any subchunk a {@link MineSectionWatch} client is resting on, so a mine can sleep through
 * every tick in which nothing under it moved. Carries radiation sidecar bytes; feeds radiation shield edits.
 */
@Mixin(LevelChunk.class)
public class MixinLevelChunk implements RadiationCarrier {

    @Unique
    private byte @Nullable [] wflib$radiation;

    @Override
    public byte @Nullable [] wflib$getRadiation() {
        return wflib$radiation;
    }

    @Override
    public void wflib$setRadiation(byte @Nullable [] bytes) {
        wflib$radiation = bytes;
    }

    // HEAD: section still holds the outgoing state.
    @Inject(method = "setBlockState(Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;Z)Lnet/minecraft/world/level/block/state/BlockState;",
            at = @At("HEAD"))
    private void wflib$radShieldEdit(BlockPos pos, BlockState state, boolean isMoving,
                                     CallbackInfoReturnable<BlockState> cir) {
        RadiationSystemNT.onBlockStateReplaced((LevelChunk) (Object) this, pos, state);
    }

    @Inject(method = "setBlockState(Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;Z)Lnet/minecraft/world/level/block/state/BlockState;",
            at = @At("RETURN"))
    private void wflib$noteSectionChange(BlockPos pos, BlockState state, boolean isMoving,
                                                CallbackInfoReturnable<BlockState> cir) {
        if (!MineSectionWatch.isWatching() || cir.getReturnValue() == null) {
            return;
        }
        MineSectionWatch.onBlockChanged(((LevelChunk) (Object) this).getLevel(), pos);
    }
}
