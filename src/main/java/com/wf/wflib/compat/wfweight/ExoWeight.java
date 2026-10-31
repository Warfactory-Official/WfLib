package com.wf.wflib.compat.wfweight;

import com.warfactory.ultimateweight.api.CarryModifiers;
import com.warfactory.ultimateweight.api.GroupLimitModifiers;
import com.wf.wflib.armor.api.exo.Exo;
import net.minecraft.world.entity.player.Player;

/** Exoskeleton carry capacity and group limits. */
public final class ExoWeight {
    private ExoWeight() {
    }

    public static void register() {
        CarryModifiers.register(player -> player instanceof Player p ? Exo.effect(p).carryBonusKg() : 0.0D);
        GroupLimitModifiers.register((player, group) -> player instanceof Player p ? Exo.effect(p).groupBonus(group) : 0);
    }
}
