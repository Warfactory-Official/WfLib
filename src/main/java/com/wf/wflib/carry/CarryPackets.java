package com.wf.wflib.carry;

import com.wf.wflib.WFLib;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public final class CarryPackets {
    private CarryPackets() {
    }

    /** C2S: key {@code key.wflib.carry}; {@code entity} = dropped item aimed at, -1 = main hand. */
    public record Pickup(int entity) implements CustomPacketPayload {
        public static final Type<Pickup> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(WFLib.MODID, "carry_pickup"));
        public static final StreamCodec<ByteBuf, Pickup> STREAM_CODEC = ByteBufCodecs.VAR_INT.map(Pickup::new, Pickup::entity);

        @Override
        public Type<Pickup> type() {
            return TYPE;
        }

        void handle(IPayloadContext ctx) {
            ctx.enqueueWork(() -> {
                if (ctx.player() instanceof ServerPlayer p) {
                    Carry.pickup(p, entity);
                }
            });
        }
    }

    /** C2S: drop key while carrying. */
    public record Drop() implements CustomPacketPayload {
        public static final Type<Drop> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(WFLib.MODID, "carry_drop"));
        public static final StreamCodec<ByteBuf, Drop> STREAM_CODEC = StreamCodec.unit(new Drop());

        @Override
        public Type<Drop> type() {
            return TYPE;
        }

        void handle(IPayloadContext ctx) {
            ctx.enqueueWork(() -> {
                if (ctx.player() instanceof ServerPlayer p) {
                    Carry.drop(p);
                }
            });
        }
    }

    /** S2C: entity's carried stack. */
    public record Carried(int entity, ItemStack stack) implements CustomPacketPayload {
        public static final Type<Carried> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(WFLib.MODID, "carry_carried"));
        public static final StreamCodec<RegistryFriendlyByteBuf, Carried> STREAM_CODEC = StreamCodec.composite(
                ByteBufCodecs.VAR_INT, Carried::entity, ItemStack.OPTIONAL_STREAM_CODEC, Carried::stack, Carried::new);

        @Override
        public Type<Carried> type() {
            return TYPE;
        }

        void handle(IPayloadContext ctx) {
            ctx.enqueueWork(() -> {
                if (ctx.player().level().getEntity(entity) instanceof Player p) {
                    p.setData(Carry.CARRIED, stack);
                }
            });
        }
    }
}
