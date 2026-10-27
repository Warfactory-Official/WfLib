package com.wf.wflib.mixin;

import com.wf.wflib.hazard.HazardSystem;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.inventory.AbstractContainerMenu;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Every menu a player gets (inventory included) feeds slot deltas to the inventory hazard scan. */
@Mixin(ServerPlayer.class)
public abstract class MixinServerPlayerHazards {

    @Inject(method = "initMenu", at = @At("TAIL"))
    private void wflib$watchMenuForHazards(AbstractContainerMenu container, CallbackInfo ci) {
        container.addSlotListener(new HazardSystem.MenuWatcher((ServerPlayer) (Object) this));
    }
}
