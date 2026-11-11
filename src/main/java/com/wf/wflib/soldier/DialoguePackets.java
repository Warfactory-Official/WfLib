package com.wf.wflib.soldier;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

import java.util.List;

/**
 * The dialogue's wire format (optional channel: a client without it still gets the old sneak-use gestures). The server owns the
 * conversation: it sends each {@link Node}, the client answers with the {@link Pick}ed option's index, and the server checks it
 * against the options it sent. {@link Trade} carries the soldier's inventory while the trade screen is open.
 */
public final class DialoguePackets {
    private DialoguePackets() {}

    static void register(PayloadRegistrar r) {
        r.playToClient(Node.TYPE, Node.CODEC, Node::handle);
        r.playToClient(Trade.TYPE, Trade.CODEC, Trade::handle);
        r.playToClient(Close.TYPE, Close.CODEC, Close::handle);
        r.playToServer(Pick.TYPE, Pick.CODEC, Pick::handle);
        r.playToServer(Move.TYPE, Move.CODEC, Move::handle);
    }

    /** One line of the conversation and what can be said back; {@code json} is built by {@link Dialogue}. */
    public record Node(String json) implements CustomPacketPayload {
        public static final Type<Node> TYPE = new Type<>(ResourceLocation.parse("wflib:dialogue_node"));
        static final StreamCodec<FriendlyByteBuf, Node> CODEC = CustomPacketPayload.codec((p, b) -> b.writeUtf(p.json, 16384), b -> new Node(b.readUtf(16384)));

        @Override public Type<Node> type() { return TYPE; }

        static void handle(Node packet, IPayloadContext ctx) {
            ctx.enqueueWork(() -> com.wf.wflib.soldier.client.DialogueClient.node(packet.json));
        }
    }

    /** The soldier's inventory (36 slots, then feet, legs, chest, head, off hand) for the trade screen. */
    public record Trade(int entity, List<ItemStack> items) implements CustomPacketPayload {
        public static final Type<Trade> TYPE = new Type<>(ResourceLocation.parse("wflib:dialogue_trade"));
        static final StreamCodec<RegistryFriendlyByteBuf, Trade> CODEC = StreamCodec.composite(
                ByteBufCodecs.VAR_INT, Trade::entity, ItemStack.OPTIONAL_LIST_STREAM_CODEC, Trade::items, Trade::new);

        @Override public Type<Trade> type() { return TYPE; }

        static void handle(Trade packet, IPayloadContext ctx) {
            ctx.enqueueWork(() -> com.wf.wflib.soldier.client.DialogueClient.trade(packet));
        }
    }

    /** The conversation is over (the soldier walked off, died, or the server ended it). */
    public record Close() implements CustomPacketPayload {
        public static final Type<Close> TYPE = new Type<>(ResourceLocation.parse("wflib:dialogue_close"));
        static final StreamCodec<FriendlyByteBuf, Close> CODEC = CustomPacketPayload.codec((p, b) -> { }, b -> new Close());

        @Override public Type<Close> type() { return TYPE; }

        static void handle(Close packet, IPayloadContext ctx) {
            ctx.enqueueWork(com.wf.wflib.soldier.client.DialogueClient::close);
        }
    }

    /** The player chose option {@code index} of the last node; -1 ends the conversation. */
    public record Pick(int index) implements CustomPacketPayload {
        public static final Type<Pick> TYPE = new Type<>(ResourceLocation.parse("wflib:dialogue_pick"));
        static final StreamCodec<FriendlyByteBuf, Pick> CODEC = CustomPacketPayload.codec((p, b) -> b.writeVarInt(p.index), b -> new Pick(b.readVarInt()));

        @Override public Type<Pick> type() { return TYPE; }

        static void handle(Pick packet, IPayloadContext ctx) {
            ctx.enqueueWork(() -> {
                if (ctx.player() instanceof ServerPlayer p && !com.wf.wflib.soldier.api.SoldierPlayer.class.isInstance(p)) Dialogue.pick(p, packet.index);
            });
        }
    }

    /** Trade screen: move the stack in slot {@code slot} of the soldier's (or the player's) inventory to the other side. */
    public record Move(boolean fromSoldier, int slot) implements CustomPacketPayload {
        public static final Type<Move> TYPE = new Type<>(ResourceLocation.parse("wflib:dialogue_move"));
        static final StreamCodec<FriendlyByteBuf, Move> CODEC = CustomPacketPayload.codec((p, b) -> {
            b.writeBoolean(p.fromSoldier);
            b.writeVarInt(p.slot);
        }, b -> new Move(b.readBoolean(), b.readVarInt()));

        @Override public Type<Move> type() { return TYPE; }

        static void handle(Move packet, IPayloadContext ctx) {
            ctx.enqueueWork(() -> {
                if (ctx.player() instanceof ServerPlayer p && !com.wf.wflib.soldier.api.SoldierPlayer.class.isInstance(p)) Dialogue.move(p, packet.fromSoldier, packet.slot);
            });
        }
    }
}
