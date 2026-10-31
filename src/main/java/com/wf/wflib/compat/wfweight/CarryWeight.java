package com.wf.wflib.compat.wfweight;

import com.warfactory.ultimateweight.v1211.WeightViews1211;
import com.wf.wflib.carry.api.Carry;
import net.minecraft.world.item.ItemStack;

/** Carried stack counted as an inventory source. */
public final class CarryWeight {
    private CarryWeight() {
    }

    public static void register() {
        WeightViews1211.registerInventorySource((player, out) -> {
            ItemStack carried = Carry.carried(player);
            if (!carried.isEmpty()) {
                out.add(carried);
            }
        });
    }
}
