package com.wf.wflib.explosion.api;

import net.minecraft.world.entity.Entity;

/** Extra non-lethal payload per blast-damaged entity. */
public interface ICustomDamageHandler {

    /** {@code distanceScaled}: fraction of blast radius, {@code [0,1]}, 0 = epicentre. */
    void handleAttack(ExplosionAEF explosion, Entity entity, double distanceScaled);
}
