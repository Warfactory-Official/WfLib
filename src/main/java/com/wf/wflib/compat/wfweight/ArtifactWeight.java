package com.wf.wflib.compat.wfweight;

import com.warfactory.ultimateweight.api.CarryModifiers;
import com.warfactory.ultimateweight.api.StaminaModifiers;
import com.wf.wflib.artifact.Artifact;
import com.wf.wflib.artifact.Artifacts;
import net.minecraft.world.entity.player.Player;

/**
 * Worn artefacts through WFWeight: {@code power_restore_speed} as stamina back every tick, resting or sprinting;
 * {@code additional_inventory_weight} onto carry capacity.
 */
public final class ArtifactWeight {
    private ArtifactWeight() {
    }

    public static void register() {
        StaminaModifiers.register(player -> {
            double share = 0.0D;
            if (player instanceof Player p) {
                for (Artifact a : Artifacts.wornArtifacts(p)) {
                    share += a.staminaPerTick();
                }
            }
            return share;
        });
        CarryModifiers.register(player -> {
            double kg = 0.0D;
            if (player instanceof Player p) {
                for (Artifact a : Artifacts.wornArtifacts(p)) {
                    kg += a.carry();
                }
            }
            return kg;
        });
    }
}
