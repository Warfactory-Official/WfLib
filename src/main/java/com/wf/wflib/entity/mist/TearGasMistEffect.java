package com.wf.wflib.entity.mist;

import com.wf.wflib.armor.ArmorExposure;
import com.wf.wflib.armor.ProtectionType;
import com.wf.wflib.entity.MistEntity;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;

/** Tear gas: blinded, coughing, slowed while in it and a few seconds after; no damage. */
public class TearGasMistEffect implements MistEffect {
    /** Condition points per exposure interval at full concentration; mustard 150. */
    private static final double EXPOSURE_TIER = 30.0D;

    @Override
    public void affect(MistEntity mist, Entity target, double intensity) {
        if (!(target instanceof LivingEntity living) || living.isInvulnerable()) return;

        int duration = (int) (80 * intensity) + 60;
        living.addEffect(new MobEffectInstance(MobEffects.BLINDNESS, duration, 0));
        living.addEffect(new MobEffectInstance(MobEffects.CONFUSION, duration, 0));
        living.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, duration, 1));
        living.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, duration, 1));
        // TODO: no respirator gate (no agent has one): a sealed mask should spare the eyes.
        ArmorExposure.contact(living, ProtectionType.CHEMICAL, EXPOSURE_TIER * intensity);
    }

    @Override
    public int color(MistEntity mist) {
        return 0xE4E4D0;
    }
}
