package com.wf.wflib.explosion.api;

import com.wf.wflib.fire.api.FireType;
import com.wf.wflib.fire.api.WFFire;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;


public class CustomDamageHandlerAmat implements ICustomDamageHandler {

    /** {@code strength}: fire-ignition ticks at the epicentre. */
    private final float strength;

    public CustomDamageHandlerAmat(float strength) {
        this.strength = strength;
    }

    @Override
    public void handleAttack(ExplosionAEF explosion, Entity entity, double distanceScaled) {
        if (entity instanceof LivingEntity living) {
            int ticks = (int) Mth.clamp(this.strength * (1.0 - distanceScaled), 0.0, this.strength);
            if (ticks > 0) {
                WFFire.ignite(living, FireType.PHOSPHORUS, ticks);
            }
        }
    }
}
