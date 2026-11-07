package com.wf.wflib.mixin.soldier;

import com.wf.wflib.soldier.api.SoldierConnection;
import net.minecraft.network.Connection;
import net.minecraft.network.PacketSendListener;
import net.minecraft.network.protocol.Packet;
import net.minecraft.server.network.ServerCommonPacketListenerImpl;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ServerCommonPacketListenerImpl.class)
abstract class ServerCommonPacketListenerImplMixin {
    @Shadow
    @Final
    protected Connection connection;

    /** A soldier has no client and no channel: nothing is sent to it (before the payload checks that read the channel). */
    @Inject(method = "send(Lnet/minecraft/network/protocol/Packet;Lnet/minecraft/network/PacketSendListener;)V",
            at = @At("HEAD"), cancellable = true)
    private void wflib$soldierHasNoClient(Packet<?> packet, PacketSendListener listener, CallbackInfo ci) {
        if (connection == SoldierConnection.INSTANCE) ci.cancel();
    }
}
