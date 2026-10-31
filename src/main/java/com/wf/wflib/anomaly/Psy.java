package com.wf.wflib.anomaly;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.neoforged.neoforge.common.NeoForge;
import com.wf.wflib.anomaly.api.event.PsyExposureEvent;

/** Telepatic hits: to a player, a {@link PsyExposureEvent}; unhandled, or to anything else, hurt. */
public final class Psy {
    private Psy() {
    }

    /** {@code units} of psy (1: a stalker's whole psy health) at {@code living}; {@code source} if it hurts. */
    public static void expose(LivingEntity living, float units, DamageSource source) {
        if (units <= 0.0F) {
            return;
        }
        if (living instanceof ServerPlayer player && NeoForge.EVENT_BUS.post(new PsyExposureEvent(player, units))
                .handled()) {
            return;
        }
        living.hurt(source, units * MincerTuning.FULL_HIT);
    }
}
