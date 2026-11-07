package com.wf.wflib.mixin.ragdoll;

import com.wf.wflib.ragdoll.RagdollServer;
import net.minecraft.network.protocol.PacketUtils;
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** The sim owns the position of a ragdolled player: client moves dropped, no moved-wrongly, no flying kick. */
@Mixin(ServerGamePacketListenerImpl.class)
public abstract class MixinServerGamePacketListenerRagdoll {

    @Shadow
    public ServerPlayer player;

    @Shadow
    private boolean clientIsFloating;

    @Inject(method = "handleMovePlayer", cancellable = true, at = @At(value = "INVOKE", shift = At.Shift.AFTER,
            target = "Lnet/minecraft/network/protocol/PacketUtils;ensureRunningOnSameThread(Lnet/minecraft/network/protocol/Packet;"
                    + "Lnet/minecraft/network/PacketListener;Lnet/minecraft/server/level/ServerLevel;)V"))
    private void wflib$ragdollOwnsPosition(ServerboundMovePlayerPacket packet, CallbackInfo ci) {
        if (RagdollServer.owned(player)) {
            clientIsFloating = false;
            ci.cancel();
        }
    }
}
