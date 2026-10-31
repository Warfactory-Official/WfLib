package com.wf.wflib.radiation;

import com.wf.wflib.core.api.WFSystems;
import com.wf.wflib.explosion.api.RadiationSink;
import com.wf.wflib.hazard.api.ContaminationUtil;
import com.wf.wflib.radiation.api.RadiationConfig;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;

/** Explosion radiation: entity dose, fallout, field increments. */
final class ExplosionRadiation implements RadiationSink {
    @Override
    public void radiate(Level level, double x, double y, double z, double range, double rad) {
        if (WFSystems.enabled("hazard")) {
            ContaminationUtil.radiate(level, x, y, z, range, rad);
        }
    }

    @Override
    public void deposit(ServerLevel level, double x, double z, double blastRadius) {
        FalloutData.deposit(level, x, z, blastRadius * FalloutData.RANGE_PER_RADIUS, RadiationConfig.get().falloutRad());
    }

    @Override
    public void increment(ServerLevel level, BlockPos pos, double amount) {
        RadiationSystemNT.incrementRad(level, pos, amount);
    }
}
