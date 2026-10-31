package com.wf.wflib.hazard.api;

import com.wf.wflib.core.api.OrderedRegistry;
import net.minecraft.world.entity.player.Player;

/** What soaks up a player's radiation dose before it lands: worn gear owned by other systems. */
public final class DoseShields {

    @FunctionalInterface
    public interface Shield {
        /** @return the dose left after this shield; positive doses only. */
        double shield(Player player, double dose);
    }

    public static final OrderedRegistry<Shield> SHIELDS = OrderedRegistry.create("hazard", "dose shields");

    private DoseShields() {
    }

    public static double apply(Player player, double dose) {
        double left = dose;
        for (Shield shield : SHIELDS.values()) {
            left = shield.shield(player, left);
        }
        return left;
    }
}
