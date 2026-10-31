package com.wf.wflib.armor.api.exo;

/** Pure: spec + power + part states -> effect. */
public final class ExoResolver {
    public static final double PENALTY = 1.5;
    public static final double DAMAGED_COMP_SCALE = 0.5;

    private ExoResolver() {
    }

    /** {@code states[i]} = state of {@code spec.parts().get(i)}. */
    public static boolean reactorDown(ExoSpec spec, PartState[] states) {
        for (int i = 0; i < spec.parts().size(); i++) {
            if (spec.parts().get(i).binds().contains(ExoFeature.REACTOR) && states[i] == PartState.DESTROYED) {
                return true;
            }
        }
        return false;
    }

    public static double drawMultiplier(ExoSpec spec, PartState[] states) {
        double m = 1;
        for (int i = 0; i < spec.parts().size(); i++) {
            if (spec.parts().get(i).binds().contains(ExoFeature.REACTOR) && states[i].atLeast(PartState.CRITICAL)) {
                m = 2;
            }
        }
        return m;
    }

    public static ExoEffect effective(ExoSpec spec, boolean powered, PartState[] states) {
        ExoStats base = powered && !reactorDown(spec, states) ? spec.powered() : spec.unpowered();
        boolean sprint = base.canSprint();
        double carry = base.carryBonusKg();
        double carryScale = 1;
        double recoil = 1 - base.recoilComp();
        double sway = 1 - base.swayComp();
        boolean life = base.lifeSupport();
        boolean shoulder = base.shoulder();
        boolean trigger = false;
        for (int i = 0; i < spec.parts().size(); i++) {
            PartState s = states[i];
            if (s == PartState.OK) {
                continue;
            }
            boolean crit = s.atLeast(PartState.CRITICAL);
            for (ExoFeature f : spec.parts().get(i).binds()) {
                switch (f) {
                    case SPRINT -> sprint &= !crit;
                    case CARRY -> carryScale = Math.min(carryScale, s == PartState.DESTROYED ? 0 : crit ? 0.5 : 0.75);
                    case RECOIL -> recoil = crit ? PENALTY : 1 - base.recoilComp() * DAMAGED_COMP_SCALE;
                    case SWAY -> sway = crit ? PENALTY : 1 - base.swayComp() * DAMAGED_COMP_SCALE;
                    case TRIGGER -> trigger |= crit;
                    case LIFE_SUPPORT -> life &= !crit;
                    case SHOULDER -> shoulder &= !crit;
                    case REACTOR -> {
                    }
                }
            }
        }
        java.util.Map<String, Integer> groups = new java.util.HashMap<>();
        final double scale = carryScale;
        base.groupLimits().forEach((k, v) -> groups.put(k, (int) Math.floor(v * scale)));
        return new ExoEffect(sprint, carry * carryScale, groups, recoil, sway, life, shoulder, trigger);
    }
}
