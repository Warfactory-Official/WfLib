package com.wf.wflib.contract.api;

import net.neoforged.bus.api.Event;

/**
 * A major engagement ended. {@code winner} is the faction with more survivors ("friendly" or "enemy"), or empty for a
 * draw. Economy mods listen to move prices with the war.
 */
public final class EngagementEndedEvent extends Event {
    private final String winner;
    private final int friendlySurvivors, enemySurvivors, perSide;

    public EngagementEndedEvent(String winner, int friendlySurvivors, int enemySurvivors, int perSide) {
        this.winner = winner;
        this.friendlySurvivors = friendlySurvivors;
        this.enemySurvivors = enemySurvivors;
        this.perSide = perSide;
    }

    public String winner() {
        return winner;
    }

    public int friendlySurvivors() {
        return friendlySurvivors;
    }

    public int enemySurvivors() {
        return enemySurvivors;
    }

    /** How many soldiers each side started with. */
    public int perSide() {
        return perSide;
    }
}
