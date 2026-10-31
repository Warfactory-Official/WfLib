package com.wf.wflib.armor.api.exo;

import com.wf.wflib.core.api.WFLib;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.HashMap;
import java.util.Map;

public final class ExoPackets {
    private ExoPackets() {
    }

    public static void register(RegisterPayloadHandlersEvent event) {
        var r = event.registrar("exo-1");
        r.playToClient(State.TYPE, State.STREAM_CODEC, State::handle);
        r.playToServer(Popup.TYPE, Popup.STREAM_CODEC, Popup::handle);
    }

    /** C2S: popup opened on the garment at inventory-menu index {@code index}, or closed ({@code -1}). */
    public record Popup(int index) implements CustomPacketPayload {
        public static final Type<Popup> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(WFLib.MODID, "exo_popup"));
        public static final StreamCodec<ByteBuf, Popup> STREAM_CODEC = net.minecraft.network.codec.ByteBufCodecs.VAR_INT
                .map(i -> new Popup(i - 1), p -> p.index + 1).cast();

        @Override
        public Type<Popup> type() {
            return TYPE;
        }

        void handle(IPayloadContext ctx) {
            ctx.enqueueWork(() -> {
                var player = ctx.player();
                if (index < 0) {
                    ExoPopup.close(player);
                } else if (!ExoPopup.open(player, index)) {
                    ExoPopup.close(player);
                    player.inventoryMenu.sendAllDataToRemote();
                }
            });
        }
    }

    /** S2C: the local player's aggregate effect. */
    public record State(ExoEffect effect) implements CustomPacketPayload {
        public static final Type<State> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(WFLib.MODID, "exo_state"));
        public static final StreamCodec<ByteBuf, State> STREAM_CODEC = StreamCodec.of(State::write, State::read);

        private static void write(ByteBuf raw, State s) {
            FriendlyByteBuf buf = new FriendlyByteBuf(raw);
            ExoEffect e = s.effect;
            buf.writeBoolean(e.canSprint());
            buf.writeDouble(e.carryBonusKg());
            buf.writeVarInt(e.groupLimits().size());
            e.groupLimits().forEach((k, v) -> {
                buf.writeUtf(k);
                buf.writeVarInt(v);
            });
            buf.writeDouble(e.recoilFactor());
            buf.writeDouble(e.swayFactor());
            buf.writeBoolean(e.lifeSupport());
            buf.writeBoolean(e.shoulder());
            buf.writeBoolean(e.triggerLocked());
        }

        private static State read(ByteBuf raw) {
            FriendlyByteBuf buf = new FriendlyByteBuf(raw);
            boolean sprint = buf.readBoolean();
            double carry = buf.readDouble();
            int n = buf.readVarInt();
            Map<String, Integer> groups = new HashMap<>();
            for (int i = 0; i < n; i++) {
                groups.put(buf.readUtf(), buf.readVarInt());
            }
            return new State(new ExoEffect(sprint, carry, groups, buf.readDouble(), buf.readDouble(),
                    buf.readBoolean(), buf.readBoolean(), buf.readBoolean()));
        }

        @Override
        public Type<State> type() {
            return TYPE;
        }

        void handle(IPayloadContext ctx) {
            ctx.enqueueWork(() -> Exo.setClient(effect));
        }
    }
}
