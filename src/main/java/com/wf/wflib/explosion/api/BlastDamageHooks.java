package com.wf.wflib.explosion.api;

import com.wf.wflib.core.api.OrderedRegistry;
import net.minecraft.world.level.Level;

/** Victims a blast must hurt that are not loaded entities: records walking off-world. */
public final class BlastDamageHooks {

    @FunctionalInterface
    public interface Hook {
        /** @param processor source of falloff curve, range mutator, blast shape (parity with a real-entity hit) */
        void damage(EntityProcessorCross processor, ExplosionAEF explosion, Level level, double x, double y, double z,
                    float size);
    }

    public static final OrderedRegistry<Hook> HOOKS = OrderedRegistry.create("blast damage hooks");

    private BlastDamageHooks() {
    }

    public static void damage(EntityProcessorCross processor, ExplosionAEF explosion, Level level, double x, double y,
                              double z, float size) {
        for (Hook hook : HOOKS.values()) {
            hook.damage(processor, explosion, level, x, y, z, size);
        }
    }
}
