package com.wf.wflib.physics.api.event;

import net.minecraft.world.level.Level;
import net.neoforged.bus.api.Event;

/** Game bus, tick thread. Posted after the engine dropped its own per-level state. */
public final class PhysicsLevelUnloadEvent extends Event {

    private final Level level;

    public PhysicsLevelUnloadEvent(Level level) {
        this.level = level;
    }

    public Level level() {
        return level;
    }

}
