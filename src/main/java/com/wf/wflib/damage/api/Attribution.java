package com.wf.wflib.damage.api;

import com.wf.wflib.recon.api.ReconOwners;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

/**
 * Who a kill belongs to when {@link DamageSource#getEntity()} is not a player (unmanned vehicle, drone, offline mine
 * layer). Opt-in: a consumer with no use for it never calls it.
 */
public final class Attribution {

    private Attribution() {
    }

    /**
     * @return the stamped faction ({@link Attributed}), else the attacker's {@link ReconOwners} side; null for none.
     *         A player attacker => null: the consumer already has the player.
     */
    @Nullable
    public static UUID faction(DamageSource source) {
        if (source instanceof Attributed attributed && attributed.attributedFaction() != null) {
            return attributed.attributedFaction();
        }
        Entity attacker = source.getEntity();
        return attacker == null || attacker instanceof Player ? null : ReconOwners.owningEntity(attacker);
    }
}
