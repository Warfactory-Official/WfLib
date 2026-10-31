package com.wf.wflib.mixin.backpack;

import net.minecraft.core.NonNullList;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(AbstractContainerMenu.class)
public interface AccessorContainerMenu {

    @Accessor("lastSlots")
    NonNullList<ItemStack> wfLastSlots();

    @Accessor("remoteSlots")
    NonNullList<ItemStack> wfRemoteSlots();
}
