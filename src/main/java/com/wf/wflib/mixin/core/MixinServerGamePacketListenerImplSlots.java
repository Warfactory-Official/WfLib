package com.wf.wflib.mixin.core;

import com.wf.wflib.core.api.MenuSlots;
import net.minecraft.network.protocol.game.ServerboundSetCreativeModeSlotPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Creative slot writes for {@link MenuSlots} groups; vanilla accepts 1..45 only. */
@Mixin(ServerGamePacketListenerImpl.class)
public abstract class MixinServerGamePacketListenerImplSlots {
    @Shadow
    public ServerPlayer player;

    @Inject(method = "handleSetCreativeModeSlot", cancellable = true, at = @At(value = "INVOKE", shift = At.Shift.AFTER,
            target = "Lnet/minecraft/network/protocol/PacketUtils;ensureRunningOnSameThread(Lnet/minecraft/network/protocol/Packet;Lnet/minecraft/network/PacketListener;Lnet/minecraft/server/level/ServerLevel;)V"))
    private void wflib$groupSlot(ServerboundSetCreativeModeSlotPacket packet, CallbackInfo ci) {
        int index = packet.slotNum();
        if (MenuSlots.groupAt(player, index) == null) {
            return;
        }
        ci.cancel();
        ItemStack stack = packet.itemStack();
        Slot slot = player.inventoryMenu.getSlot(index);
        if (!player.gameMode.isCreative() || !slot.isActive() || !stack.isItemEnabled(player.level().enabledFeatures())
                || !stack.isEmpty() && (!slot.mayPlace(stack) || stack.getCount() > slot.getMaxStackSize(stack))) {
            player.inventoryMenu.sendAllDataToRemote();
            return;
        }
        slot.setByPlayer(stack);
        player.inventoryMenu.broadcastChanges();
    }
}
