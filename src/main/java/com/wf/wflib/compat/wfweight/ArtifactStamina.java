package com.wf.wflib.compat.wfweight;

import com.warfactory.ultimateweight.api.StaminaModifiers;
import com.wf.wflib.artifact.ArtifactItem;
import com.wf.wflib.artifact.Artifacts;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

/** Worn artefacts' {@code power_restore_speed}: stamina back faster (or slower) every tick, resting or sprinting. */
public final class ArtifactStamina {
    private ArtifactStamina() {
    }

    public static void register() {
        StaminaModifiers.register(player -> {
            double share = 0.0D;
            if (player instanceof Player p) {
                for (ItemStack stack : Artifacts.worn(p)) {
                    if (stack.getItem() instanceof ArtifactItem item) {
                        share += item.artifact().staminaPerTick();
                    }
                }
            }
            return share;
        });
    }
}
