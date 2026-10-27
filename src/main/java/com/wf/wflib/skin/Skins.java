package com.wf.wflib.skin;

import com.mojang.serialization.Codec;
import com.wf.wflib.WFLib;
import io.netty.buffer.ByteBuf;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.List;

/** Chosen {@link Skinnable} look per stack; client asks with {@link Choose}, the server checks and stamps it. */
public final class Skins {
    private static final DeferredRegister.DataComponents COMPONENTS =
            DeferredRegister.createDataComponents(Registries.DATA_COMPONENT_TYPE, WFLib.MODID);

    /** Index into {@link Skinnable#skins()}; absent => 0. */
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<Integer>> SKIN =
            COMPONENTS.registerComponentType("skin", b -> b.persistent(Codec.INT).networkSynchronized(ByteBufCodecs.VAR_INT));

    private Skins() {
    }

    public static void register(IEventBus modBus) {
        COMPONENTS.register(modBus);
        modBus.addListener((RegisterPayloadHandlersEvent e) -> e.registrar(WFLib.MODID).versioned("skin-1")
                .playToServer(Choose.TYPE, Choose.STREAM_CODEC, Choose::handle));
    }

    /** {@code stack}'s looks; empty => not skinnable. */
    public static List<Skinnable.Skin> of(ItemStack stack) {
        return stack.getItem() instanceof Skinnable s ? s.skins() : List.of();
    }

    /** Worn look, clamped to the item's current list (a pack edit can shorten it). */
    public static int index(ItemStack stack) {
        int n = of(stack).size();
        return n == 0 ? 0 : Math.clamp(stack.getOrDefault(SKIN.get(), 0), 0, n - 1);
    }

    /** {@code index} out of range => false, stack untouched. */
    public static boolean set(ItemStack stack, int index) {
        if (index < 0 || index >= of(stack).size()) {
            return false;
        }
        if (index == 0) {
            stack.remove(SKIN.get());
        } else {
            stack.set(SKIN.get(), index);
        }
        return true;
    }

    /**
     * C2S: wear skin {@code index}. {@code slot} = the player's inventory menu slot (hovered in the inventory, armour
     * included), {@link #MAIN_HAND} = held item.
     */
    public record Choose(int slot, int index) implements CustomPacketPayload {
        public static final int MAIN_HAND = -1;
        public static final Type<Choose> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(WFLib.MODID, "skin_choose"));
        public static final StreamCodec<ByteBuf, Choose> STREAM_CODEC = StreamCodec.composite(
                ByteBufCodecs.VAR_INT, Choose::slot, ByteBufCodecs.VAR_INT, Choose::index, Choose::new);

        @Override
        public Type<Choose> type() {
            return TYPE;
        }

        void handle(IPayloadContext ctx) {
            ctx.enqueueWork(() -> {
                if (!(ctx.player() instanceof ServerPlayer p)) {
                    return;
                }
                ItemStack stack;
                if (slot == MAIN_HAND) {
                    stack = p.getItemInHand(InteractionHand.MAIN_HAND);
                } else if (slot >= 0 && slot < p.inventoryMenu.slots.size()) {
                    stack = p.inventoryMenu.getSlot(slot).getItem();
                } else {
                    return;
                }
                if (set(stack, index)) {
                    p.inventoryMenu.broadcastChanges();
                }
            });
        }
    }
}
