package com.wf.wflib.explosion.nuke;

import com.wf.wflib.explosion.EntityNukeExplosionMK5;
import com.wf.wflib.explosion.api.EntityNukeTorex;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

public final class Nuke {

    private Nuke() {
    }

    /** {@code radius}: MK5 carve radius (ray strength = 2 radius); also cloud scale. */
    public static void detonate(Level level, Vec3 center, int radius) {
        if (level.isClientSide) return;
        level.addFreshEntity(EntityNukeExplosionMK5.statFac(level, radius, center.x, center.y, center.z));
        level.addFreshEntity(new EntityNukeTorex(level, center, radius));
    }
}
