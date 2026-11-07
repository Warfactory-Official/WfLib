package com.wf.wflib.mixin.ragdoll;

import com.wf.wflib.ragdoll.api.Ragdolls;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** No walking or gravity for a body the sim owns; the owner's client would otherwise fight its stream. */
@Mixin(Player.class)
public abstract class MixinPlayerRagdoll {

    @Inject(method = "travel", at = @At("HEAD"), cancellable = true)
    private void wflib$noTravel(Vec3 input, CallbackInfo ci) {
        if (Ragdolls.active((Player) (Object) this)) {
            ci.cancel();
        }
    }
}
