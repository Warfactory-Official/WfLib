package com.wf.wflib.warhead.api;

import com.wf.wflib.mist.api.GasCloud;
import com.wf.wflib.mist.api.WFFluids;
import com.wf.wflib.explosion.api.ExplosionCreator;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.phys.Vec3;
import com.wf.wflib.core.api.WFLib;

/**
 * Chemical warhead: instead of a blast, it disperses a wall-respecting cloud of gas over the target area (see
 * {@link GasCloud}).
 */
public final class GasWarhead {

    /**
     * Registered warhead id (also selectable from the dispenser GUI).
     */
    public static final ResourceLocation ID = ResourceLocation.fromNamespaceAndPath(WFLib.MODID, "gas");

    private GasWarhead() {
    }

    /**
     * The agent released. Mustard gas: a persistent, blistering, area-denial cloud.
     */
    private static Fluid agent() {
        return WFFluids.MUSTARD_GAS.get();
    }

    /**
     * Warhead entry point (registered as {@link #ID}).
     */
    public static void detonate(WarheadCarrier source, Vec3 pos) {
        Level level = source.level();
        if (level.isClientSide) {
            return;
        }
        GasCloud.spawn(level, agent(), pos);
        ExplosionCreator.composeEffectSmall(level, pos.x, pos.y, pos.z);
    }
}
