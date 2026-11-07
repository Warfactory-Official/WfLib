package com.wf.wflib.labs.api;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;

import java.util.UUID;

/** An air defence the {@code interception} table sets up and leaves running on real ticks. */
public interface LabDefender {
    String id();

    /**
     * Builds the defence on the ground at {@code at} for side {@code team}, armed and engaging on its own. Server
     * thread, chunks around {@code at} entity-ticking.
     */
    Site place(ServerLevel level, BlockPos at, UUID team);

    /** One placed defence. */
    interface Site {
        /** Called after every lab tick. */
        default void tick() {
        }

        /** Interceptors / rounds / beam shots spent since {@link #place}. */
        int spent();

        void remove();
    }
}
