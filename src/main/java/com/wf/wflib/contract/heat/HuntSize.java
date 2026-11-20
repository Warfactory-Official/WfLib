package com.wf.wflib.contract.heat;

/** How the heat thresholds map to what happens. Defaults; a config file can move them later. */
public final class HuntSize {
    /** Heat at which a hunter team is sent. */
    public static final int HUNT_AT = 40;
    /** Heat at which a faction's side of the board closes to the operator. */
    public static final int RESTRICT_AT = 70;
    /** Kills of a faction's soldiers add this much heat with it. */
    public static final double PER_KILL = 2;

    private HuntSize() {
    }

    /** Hunters sent at {@code heat}: 4, 6, 8, 10 as it climbs past 40, 60, 80, 100; 0 below the hunting threshold. */
    public static int hunters(double heat) {
        if (heat < HUNT_AT) return 0;
        if (heat < 60) return 4;
        if (heat < 80) return 6;
        if (heat < 100) return 8;
        return 10;
    }

    /** Points one bribe takes off at {@code heat}: ten at most, and never more than there is to take. */
    public static int bribePoints(double heat) {
        return (int) Math.min(10, Math.ceil(heat));
    }

    public static boolean restricted(double heat) {
        return heat >= RESTRICT_AT;
    }

    /** What it costs, in whole units, to buy {@code points} of heat off with a contact at the current heat. */
    public static int bribeCost(double heat, int points) {
        return (int) Math.min(Integer.MAX_VALUE, Math.round(points * (50 + heat * 10)));
    }
}
