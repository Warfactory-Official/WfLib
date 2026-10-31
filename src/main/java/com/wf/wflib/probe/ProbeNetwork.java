package com.wf.wflib.probe;

import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;
import com.wf.wflib.probe.api.ProbeActions;

/** Payloads of {@code probe-1}; optional, so a client without wflib may still join. */
public final class ProbeNetwork {

    private ProbeNetwork() {
    }

    static void register(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar = event.registrar("probe-1").optional();

        registrar.playToServer(ProbeRequestPacket.TYPE, ProbeRequestPacket.STREAM_CODEC,
                (pkt, ctx) -> ctx.enqueueWork(() -> {
                    if (ctx.player() instanceof ServerPlayer player) {
                        ProbeSync.onRequest(pkt, player);
                    }
                }));
        registrar.playToServer(ProbeActionPacket.TYPE, ProbeActionPacket.STREAM_CODEC,
                (pkt, ctx) -> ctx.enqueueWork(() -> {
                    if (ctx.player() instanceof ServerPlayer player) {
                        ProbeActions.perform(pkt.pos(), pkt.entityId(), pkt.action(), pkt.arg(), player);
                    }
                }));
        registrar.playToClient(ProbeDataPacket.TYPE, ProbeDataPacket.STREAM_CODEC,
                (pkt, ctx) -> ctx.enqueueWork(
                        () -> com.wf.wflib.probe.client.ProbeClientData.accept(pkt)));
        registrar.playToClient(ProbeJobPacket.TYPE, ProbeJobPacket.STREAM_CODEC,
                (pkt, ctx) -> ctx.enqueueWork(() -> com.wf.wflib.probe.client.ProbeJobOverlay.accept(pkt)));
    }

    static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            ProbeSync.forget(player);
            ProbeActions.forget(player);
        }
    }
}
