package com.wf.wflib.explosion.api;

import net.minecraft.world.level.Level;

public interface IExplosionSFX {

    void doEffect(ExplosionAEF explosion, Level level, double x, double y, double z, float size);
}
