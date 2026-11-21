package com.wf.wflib.continuum;

import com.hbm.particle.helper.ExplosionCreator;
import net.minecraft.world.level.Level;

/**
 * Continuum's small, standard and large explosion effects drawn by WFLib's (docs, 3.9, experimental): the same three sizes, but as GemRender's shockwave, cloud and
 * thrown-ground effects, which WFLib's own blasts use, instead of Continuum's particles. Uses Continuum's {@code ExplosionCreator.INTERCEPT}; the nuclear cloud is
 * Continuum's own entity and stays Continuum's. Off, nothing changes.
 */
final class ExplosionFxBridge {
    private ExplosionFxBridge() {
    }

    static void install() {
        ExplosionCreator.INTERCEPT.register(ExplosionFxBridge::draw);
        ContinuumBridge.LOG.info("Explosion effects: Continuum's small, standard and large explosions are drawn by WFLib's");
    }

    private static boolean draw(Level level, double x, double y, double z, ExplosionCreator.Size size) {
        switch (size) {
            case SMALL -> com.wf.wflib.explosion.api.ExplosionCreator.composeEffectSmall(level, x, y, z);
            case STANDARD -> com.wf.wflib.explosion.api.ExplosionCreator.composeEffectStandard(level, x, y, z);
            case LARGE -> com.wf.wflib.explosion.api.ExplosionCreator.composeEffectLarge(level, x, y, z);
        }
        return true;
    }
}
