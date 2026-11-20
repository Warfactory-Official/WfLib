package com.wf.wflib.mist.api;

import com.wf.wflib.mist.spray.SprayCurtainEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.phys.Vec3;

import java.util.UUID;

public final class SprayCurtains {

    private SprayCurtains() {
    }

    public static void spawn(Level level, Fluid fluid, Vec3 at, int radius, int speed, UUID igniterFaction) {
        SprayCurtainEntity.spawn(level, fluid, at, radius, speed, igniterFaction);
    }
}
