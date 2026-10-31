package com.wf.wflib.armor.api.exo;

import java.util.Map;

/**
 * Authored capability block.
 *
 * @param groupLimits       wfweight group id -> extra item count
 * @param recoilComp / swayComp reduction in [0, {@link #MAX_COMP}]; never reaches 0 effective, see {@link ExoResolver}
 */
public record ExoStats(boolean canSprint, double carryBonusKg, Map<String, Integer> groupLimits, double recoilComp,
                       double swayComp, boolean lifeSupport, boolean shoulder) {
    public static final double MAX_COMP = 0.95;
    public static final ExoStats NONE = new ExoStats(true, 0, Map.of(), 0, 0, false, false);

    public ExoStats {
        if (recoilComp < 0 || recoilComp > MAX_COMP || swayComp < 0 || swayComp > MAX_COMP) {
            throw new IllegalArgumentException("comp in [0," + MAX_COMP + "]: " + recoilComp + " " + swayComp);
        }
        groupLimits = Map.copyOf(groupLimits);
    }
}
