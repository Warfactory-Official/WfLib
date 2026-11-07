package com.wf.wflib.soldier;

import com.wf.wflib.core.api.WFLib;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

/**
 * Tells nearby clients which soldiers have their light on; the clients draw the beam. Optional channel: a client
 * without it simply sees dark soldiers. The server repeats "on" every couple of seconds and clients let it lapse, so a
 * soldier that dies or leaves view never leaves a light behind.
 */
final class SoldierLights {
    /** Blocks within which a player is told, and ticks between repeats of "on". */
    static final double TELL_RANGE = 160;
    static final int REPEAT = 40;

    private SoldierLights() {}

    static void register(PayloadRegistrar registrar) {
        registrar.playToClient(Lit.TYPE, Lit.CODEC, Lit::handle);
    }

    static void send(ServerPlayer soldier, boolean on) {
        Lit packet = new Lit(soldier.getId(), on);
        for (ServerPlayer viewer : soldier.serverLevel().players()) {
            if (viewer == soldier || SoldierAi.scripted(viewer) || viewer.distanceToSqr(soldier) > TELL_RANGE * TELL_RANGE
                    || !viewer.connection.hasChannel(Lit.TYPE)) continue;
            PacketDistributor.sendToPlayer(viewer, packet);
        }
    }

    record Lit(int entity, boolean on) implements CustomPacketPayload {
        static final Type<Lit> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(WFLib.MODID, "soldier_light"));
        static final StreamCodec<FriendlyByteBuf, Lit> CODEC = CustomPacketPayload.codec(
                (p, buf) -> { buf.writeVarInt(p.entity); buf.writeBoolean(p.on); },
                buf -> new Lit(buf.readVarInt(), buf.readBoolean()));

        @Override
        public Type<Lit> type() { return TYPE; }

        static void handle(Lit packet, IPayloadContext context) {
            context.enqueueWork(() -> com.wf.wflib.soldier.client.SoldierLightClient.set(packet.entity, packet.on));
        }
    }
}
