package com.wf.wflib.armor.api.exo;

import java.util.HashMap;
import java.util.Map;

/** Resolved, possibly multi-piece, effect on a wearer. Factors multiply; floors applied by {@link #recoilMul}. */
public record ExoEffect(boolean canSprint, double carryBonusKg, Map<String, Integer> groupLimits, double recoilFactor,
                        double swayFactor, boolean lifeSupport, boolean shoulder, boolean triggerLocked) {
    public static final ExoEffect NONE = new ExoEffect(true, 0, Map.of(), 1, 1, false, false, false);

    public ExoEffect {
        groupLimits = Map.copyOf(groupLimits);
    }

    public ExoEffect combine(ExoEffect o) {
        Map<String, Integer> groups = new HashMap<>(groupLimits);
        o.groupLimits.forEach((k, v) -> groups.merge(k, v, Integer::sum));
        return new ExoEffect(canSprint && o.canSprint, carryBonusKg + o.carryBonusKg, groups,
                recoilFactor * o.recoilFactor, swayFactor * o.swayFactor, lifeSupport || o.lifeSupport,
                shoulder || o.shoulder, triggerLocked || o.triggerLocked);
    }

    public int groupBonus(String group) {
        return groupLimits.getOrDefault(group, 0);
    }

    /** Applied recoil multiplier: compensation approaches but never passes {@code floor}; penalties unbounded. */
    public double recoilMul(double floor) {
        return Math.max(floor, recoilFactor);
    }

    public double swayMul(double floor) {
        return Math.max(floor, swayFactor);
    }
}
