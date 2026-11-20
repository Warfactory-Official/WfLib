package com.wf.wflib.contract.pda;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

/** The PDA app's three messages: ask for the state, do something, and the state itself. */
public final class PmcNet {
    private static final ResourceLocation REQUEST = ResourceLocation.fromNamespaceAndPath(com.wf.wflib.core.api.WFLib.MODID, "contract_request");
    private static final ResourceLocation ACT = ResourceLocation.fromNamespaceAndPath(com.wf.wflib.core.api.WFLib.MODID, "contract_act");
    private static final ResourceLocation STATE = ResourceLocation.fromNamespaceAndPath(com.wf.wflib.core.api.WFLib.MODID, "contract_state");
    /** A snapshot is small; this bounds what a client will accept. */
    private static final int MAX_JSON = 1 << 18;

    private PmcNet() {
    }

    public record Request() implements CustomPacketPayload {
        public static final Type<Request> TYPE = new Type<>(REQUEST);
        public static final StreamCodec<FriendlyByteBuf, Request> CODEC = StreamCodec.unit(new Request());

        @Override
        public Type<Request> type() {
            return TYPE;
        }
    }

    /** @param verb accept, abandon, analyze or bribe; {@code arg} is a contract id or a faction */
    public record Act(String verb, String arg) implements CustomPacketPayload {
        public static final Type<Act> TYPE = new Type<>(ACT);
        public static final StreamCodec<FriendlyByteBuf, Act> CODEC = StreamCodec.composite(
                ByteBufCodecs.stringUtf8(16), Act::verb, ByteBufCodecs.stringUtf8(128), Act::arg, Act::new);

        @Override
        public Type<Act> type() {
            return TYPE;
        }
    }

    /** What the world-stats overlay shows as {@code {threat}}: the operator's tier today. */
    public record Threat(String text) implements CustomPacketPayload {
        public static final Type<Threat> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(com.wf.wflib.core.api.WFLib.MODID, "contract_threat"));
        public static final StreamCodec<FriendlyByteBuf, Threat> CODEC = StreamCodec.composite(ByteBufCodecs.stringUtf8(96), Threat::text, Threat::new);

        @Override
        public Type<Threat> type() {
            return TYPE;
        }
    }

    public record State(String json) implements CustomPacketPayload {
        public static final Type<State> TYPE = new Type<>(STATE);
        public static final StreamCodec<FriendlyByteBuf, State> CODEC = StreamCodec.composite(
                ByteBufCodecs.stringUtf8(MAX_JSON), State::json, State::new);

        @Override
        public Type<State> type() {
            return TYPE;
        }
    }

    /** Mod bus. {@code client} receives the state; it is only called on a client. */
    public static void register(RegisterPayloadHandlersEvent event, java.util.function.Consumer<Snapshot> client, java.util.function.Consumer<String> threat) {
        PayloadRegistrar r = event.registrar("contract-1");
        r.playToServer(Request.TYPE, Request.CODEC, (pkt, ctx) -> {
            if (ctx.player() instanceof ServerPlayer p) PdaService.send(p, "");
        });
        r.playToServer(Act.TYPE, Act.CODEC, (pkt, ctx) -> {
            if (ctx.player() instanceof ServerPlayer p) PdaService.act(p, pkt.verb(), pkt.arg());
        });
        r.playToClient(Threat.TYPE, Threat.CODEC, (pkt, ctx) -> ctx.enqueueWork(() -> threat.accept(pkt.text())));
        r.playToClient(State.TYPE, State.CODEC, (pkt, ctx) -> ctx.enqueueWork(() -> client.accept(Snapshot.fromJson(pkt.json()))));
    }
}
