package com.wf.wflib.anomaly.api.event;

import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.Event;

/**
 * Psy reaching a player (STALKER's telepatic hits), in STALKER psy health: 1 is all of it. A mod that models psy
 * (a stress mod) takes it with {@link #handle}; unhandled, it hurts as {@code wflib:psy} ({@code wflib:psy}).
 */
public class PsyExposureEvent extends Event {
    private final ServerPlayer player;
    private final float psy;
    private boolean handled;

    public PsyExposureEvent(ServerPlayer player, float psy) {
        this.player = player;
        this.psy = psy;
    }

    public ServerPlayer player() {
        return player;
    }

    public float psy() {
        return psy;
    }

    public void handle() {
        handled = true;
    }

    public boolean handled() {
        return handled;
    }
}
