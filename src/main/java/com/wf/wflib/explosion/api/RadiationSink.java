package com.wf.wflib.explosion.api;

import com.wf.wflib.core.api.Hook;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;

/** What an explosion leaves behind as radiation; the default does nothing, a radiation system replaces it. */
public interface RadiationSink {
    Hook<RadiationSink> SINK = Hook.of("explosion.radiation", new RadiationSink() {
        @Override
        public void radiate(Level level, double x, double y, double z, double range, double rad) {
        }

        @Override
        public void deposit(ServerLevel level, double x, double z, double blastRadius) {
        }

        @Override
        public void increment(ServerLevel level, BlockPos pos, double amount) {
        }
    });

    /** Dose to entities within {@code range} of the point. */
    void radiate(Level level, double x, double y, double z, double range, double rad);

    /** Lasting ground contamination around a blast of {@code blastRadius}. */
    void deposit(ServerLevel level, double x, double z, double blastRadius);

    /** Adds {@code amount} to the field at {@code pos}. */
    void increment(ServerLevel level, BlockPos pos, double amount);
}
