package com.wf.wflib.mixin.explosion;

import com.wf.wflib.explosion.ExplosionSystem;
import com.wf.wflib.explosion.api.client.Removals;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ClientLevel.class)
public abstract class MixinClientLevelRemovals {

    @Inject(method = "setBlocksDirty(Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/world/level/block/state/BlockState;)V",
            at = @At("HEAD"))
    private void wfrecordRemoval(BlockPos pos, BlockState from, BlockState to, CallbackInfo ci) {
        if (ExplosionSystem.active()) Removals.changed(pos, from, to);
    }
}
