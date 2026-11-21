package com.wf.wflib.continuum;

import com.hbm.items.armor.ArmorFSB;
import com.hbm.items.armor.ArmorFSBFueled;
import com.hbm.items.armor.ArmorFSBPowered;
import com.wf.wflib.soldier.SoldierCatalogue;
import net.minecraft.world.item.ItemStack;

import java.util.IdentityHashMap;
import java.util.Map;

/**
 * Continuum's power armour (the powered and fuelled suits: HEV, AJR, BJ, DNT, the envsuit, Digamma, Desh, the diesel suit) goes on soldiers as a whole set or
 * not at all. A set is the pieces sharing one armour material, as Continuum counts it for its own full-set bonus.
 */
final class PowerArmour {
    private PowerArmour() {
    }

    private static final Map<Object, String> IDS = new IdentityHashMap<>();

    static void install() {
        SoldierCatalogue.SETS.add(PowerArmour::set);
        ContinuumBridge.LOG.info("Soldiers: Continuum's power armour is issued as whole sets");
    }

    /** The set a power armour piece belongs to (by its material), or null for anything else. */
    static synchronized String set(ItemStack stack) {
        if (!(stack.getItem() instanceof ArmorFSBPowered || stack.getItem() instanceof ArmorFSBFueled)) return null;
        return IDS.computeIfAbsent(((ArmorFSB) stack.getItem()).material, m -> "hbm_power_" + IDS.size());
    }
}
