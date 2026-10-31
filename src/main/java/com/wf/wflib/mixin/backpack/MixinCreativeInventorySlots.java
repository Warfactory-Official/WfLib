package com.wf.wflib.mixin.backpack;

import com.wf.wflib.backpack.BackpackSystem;
import net.minecraft.client.gui.screens.inventory.CreativeModeInventoryScreen;
import net.minecraft.core.NonNullList;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Inventory tab swaps {@code ItemPickerMenu.slots} for wrappers without growing {@code lastSlots}/{@code remoteSlots};
 * backpack slots push it past 54 => {@code broadcastChanges} (JEI give) overruns.
 */
@Mixin(CreativeModeInventoryScreen.class)
public abstract class MixinCreativeInventorySlots {

    @Inject(method = "selectTab", at = @At("TAIL"))
    private void wflib$padSlotTracking(CreativeModeTab tab, CallbackInfo ci) {
        if (!BackpackSystem.active()) {
            return;
        }
        CreativeModeInventoryScreen.ItemPickerMenu picker = ((CreativeModeInventoryScreen) (Object) this).getMenu();
        AccessorContainerMenu menu = (AccessorContainerMenu) picker;
        pad(menu.wfLastSlots(), picker.slots.size());
        pad(menu.wfRemoteSlots(), picker.slots.size());
    }

    private static void pad(NonNullList<ItemStack> list, int size) {
        while (list.size() < size) {
            list.add(ItemStack.EMPTY);
        }
    }
}
