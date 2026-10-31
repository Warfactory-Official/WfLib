package com.wf.wflib.armor.api.exo;

public enum PartState {
    OK, DAMAGED, CRITICAL, DESTROYED;

    public static final double DAMAGED_BELOW = 0.6;
    public static final double CRITICAL_BELOW = 0.25;

    public static PartState of(int hp, int maxHp) {
        if (hp <= 0) {
            return DESTROYED;
        }
        double f = (double) hp / maxHp;
        return f < CRITICAL_BELOW ? CRITICAL : f < DAMAGED_BELOW ? DAMAGED : OK;
    }

    public boolean atLeast(PartState other) {
        return ordinal() >= other.ordinal();
    }
}
