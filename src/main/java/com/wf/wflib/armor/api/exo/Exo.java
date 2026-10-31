package com.wf.wflib.armor.api.exo;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;

/** Aggregate exoskeleton effect of a wearer: server = authoritative, client = synced for the local player only. */
public final class Exo {
    private static volatile ExoEffect clientLocal = ExoEffect.NONE;

    private Exo() {
    }

    public static ExoEffect effect(LivingEntity entity) {
        if (!(entity instanceof Player player)) {
            return ExoEffect.NONE;
        }
        return player.level().isClientSide ? (player.isLocalPlayer() ? clientLocal : ExoEffect.NONE)
                : ExoServer.effect(player);
    }

    public static boolean triggerLocked(LivingEntity entity) {
        return effect(entity).triggerLocked();
    }

    public static double recoilMul(LivingEntity entity) {
        return effect(entity).recoilMul(ExoConfig.get().recoilFloor());
    }

    public static double swayMul(LivingEntity entity) {
        return effect(entity).swayMul(ExoConfig.get().swayFloor());
    }

    public static boolean canSprint(LivingEntity entity) {
        return effect(entity).canSprint();
    }

    static void setClient(ExoEffect effect) {
        clientLocal = effect;
    }
}
