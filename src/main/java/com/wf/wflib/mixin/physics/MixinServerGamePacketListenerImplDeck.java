package com.wf.wflib.mixin.physics;

import com.wf.wflib.physics.api.DeckAttachment;
import com.wf.wflib.physics.api.VehicleParenting;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.ModifyConstant;

/** Lets a player standing on a deck move as fast as the deck is carrying it. */
@Mixin(ServerGamePacketListenerImpl.class)
public abstract class MixinServerGamePacketListenerImplDeck {

    @Shadow
    public ServerPlayer player;

    @ModifyConstant(method = "handleMovePlayer", constant = @Constant(floatValue = 100.0F))
    private float wflib$allowCarriedMotion(float allowance) {
        DeckAttachment attachment = VehicleParenting.attachmentOf(this.player);
        if (attachment == null || !attachment.gripped()) {
            return allowance;
        }
        // allowance is a squared distance; carry contribution added squared too.
        return allowance + (float) attachment.lastCarrySpeedSqr();
    }

    /** Server's deck is 1-2 ticks ahead of client's; widen move tolerance by that lead to avoid snapback. */
    @ModifyConstant(method = "handleMovePlayer", constant = @Constant(doubleValue = 0.0625))
    private double wflib$allowDeckLead(double tolerance) {
        DeckAttachment attachment = VehicleParenting.attachmentOf(this.player);
        if (attachment == null || !attachment.gripped()) {
            return tolerance;
        }
        double slack = Math.sqrt(tolerance) + 2 * Math.sqrt(attachment.lastCarrySpeedSqr());
        return slack * slack;
    }
}
