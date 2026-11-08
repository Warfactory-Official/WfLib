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
public final class SoldierLights {
    /** Blocks within which a player is told, and ticks between repeats of "on". */
    static final double TELL_RANGE = 160;
    static final int REPEAT = 40;

    private SoldierLights() {}

    /** Client: ask the server to switch the headlamp this player wears. */
    public static void requestToggle() {
        PacketDistributor.sendToServer(new Toggle());
    }

    /** Client: whether the server has the channel {@link #requestToggle} needs. */
    public static boolean canToggle(net.minecraft.network.protocol.common.ClientCommonPacketListener connection) {
        return connection.hasChannel(Toggle.TYPE);
    }

    static void register(PayloadRegistrar registrar) {
        registrar.playToClient(Lit.TYPE, Lit.CODEC, Lit::handle);
        registrar.playToServer(Toggle.TYPE, Toggle.CODEC, Toggle::handle);
    }

    static void send(ServerPlayer soldier, boolean on) {
        Lit packet = new Lit(soldier.getId(), on);
        for (ServerPlayer viewer : soldier.serverLevel().players()) {
            if (SoldierAi.scripted(viewer) || viewer.distanceToSqr(soldier) > TELL_RANGE * TELL_RANGE
                    || !viewer.connection.hasChannel(Lit.TYPE)) continue;
            PacketDistributor.sendToPlayer(viewer, packet);
        }
    }

    /** The wearer's key: switch the headlamp they wear. */
    record Toggle() implements CustomPacketPayload {
        static final Type<Toggle> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(WFLib.MODID, "headlamp_toggle"));
        static final StreamCodec<FriendlyByteBuf, Toggle> CODEC = StreamCodec.unit(new Toggle());

        @Override
        public Type<Toggle> type() { return TYPE; }

        static void handle(Toggle packet, IPayloadContext context) {
            context.enqueueWork(() -> { if (context.player() instanceof ServerPlayer p) Headlamp.toggle(p); });
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
