package com.wf.wflib.compat.gt;

import com.gregtechceu.gtceu.api.capability.GTCapabilityHelper;
import com.gregtechceu.gtceu.api.capability.IElectricItem;
import net.minecraft.world.item.ItemStack;

/** GTCEu electric item access; -1 = not an electric item. */
public final class GregtechBattery {
    private GregtechBattery() {
    }

    public static long charge(ItemStack stack) {
        IElectricItem e = GTCapabilityHelper.getElectricItem(stack);
        return e == null ? -1 : e.getCharge();
    }

    public static long maxCharge(ItemStack stack) {
        IElectricItem e = GTCapabilityHelper.getElectricItem(stack);
        return e == null ? -1 : e.getMaxCharge();
    }

    public static long discharge(ItemStack stack, long eu, boolean simulate) {
        IElectricItem e = GTCapabilityHelper.getElectricItem(stack);
        return e == null ? -1 : e.discharge(eu, Integer.MAX_VALUE, true, false, simulate);
    }
}
