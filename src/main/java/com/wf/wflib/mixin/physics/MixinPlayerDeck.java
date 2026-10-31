package com.wf.wflib.mixin.physics;

import com.wf.wflib.physics.api.DeckAttachment;
import com.wf.wflib.physics.api.DeckCollision;
import com.wf.wflib.physics.api.VehicleParenting;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Player.class)
public abstract class MixinPlayerDeck {

    /** Deck counts as ground: suppresses vanilla's sneak-edge fall check over it. */
    @Inject(method = "canFallAtLeast", at = @At("RETURN"), cancellable = true)
    private void wflib$deckIsGround(double dx, double dz, float depth, CallbackInfoReturnable<Boolean> cir) {
        if (!cir.getReturnValueZ()) {
            return;
        }
        Player self = (Player) (Object) this;
        DeckAttachment attachment = VehicleParenting.attachmentOf(self);
        if (!VehicleParenting.isUsable(attachment, self)) {
            return;
        }
        if (DeckCollision.supportsOffset(self, attachment.vehicle().deckSnapshot(), attachment, dx, dz, depth)) {
            cir.setReturnValue(false);
        }
    }
}
