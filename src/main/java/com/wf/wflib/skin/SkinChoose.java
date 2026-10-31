package com.wf.wflib.skin;

import com.wf.wflib.skin.api.Skins;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import com.wf.wflib.core.api.WFLib;

/**
 * C2S: wear skin {@code index}. {@code slot} = the player's inventory menu slot (hovered in the inventory, armour
 * included), {@link #MAIN_HAND} = held item.
 */
public record SkinChoose(int slot, int index) implements CustomPacketPayload {
    public static final int MAIN_HAND = -1;
    public static final Type<SkinChoose> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(WFLib.MODID, "skin_choose"));
    public static final StreamCodec<ByteBuf, SkinChoose> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, SkinChoose::slot, ByteBufCodecs.VAR_INT, SkinChoose::index, SkinChoose::new);

    @Override
    public Type<SkinChoose> type() {
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
            if (Skins.set(stack, index)) {
                p.inventoryMenu.broadcastChanges();
            }
        });
    }
}
