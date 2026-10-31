package com.wf.wflib.physics.api;

import net.minecraft.world.level.Level;

/**
 * Tick stamp for "has this entity ticked yet this tick". Client game time is overwritten by the server's time packet
 * every 20 ticks => stamps misfire, a pose lands before its owner's {@code setOldPosAndRot} (0.3-block hitch per 20 t).
 */
public final class TickClock {

    private static long client;

    private TickClock() {
    }

    public static void advanceClient() {
        client++;
    }

    public static long now(Level level) {
        return level.isClientSide() ? client : level.getGameTime();
    }
}
