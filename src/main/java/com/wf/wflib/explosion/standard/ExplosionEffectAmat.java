package com.wf.wflib.explosion.standard;

import com.wf.wflib.explosion.api.ExplosionAEF;
import com.wf.wflib.explosion.api.IExplosionSFX;
import com.wf.wflib.explosion.api.ExplosionCreator;
import net.minecraft.world.level.Level;


public class ExplosionEffectAmat implements IExplosionSFX {

    @Override
    public void doEffect(ExplosionAEF explosion, Level level, double x, double y, double z, float size) {
        if (level.isClientSide) {
            return;
        }
        ExplosionCreator.composeEffectLarge(level, x, y, z);
    }
}
