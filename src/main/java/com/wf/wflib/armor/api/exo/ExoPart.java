package com.wf.wflib.armor.api.exo;

import java.util.EnumSet;
import java.util.Set;

/**
 * Separately damageable limb/module of a garment.
 *
 * @param limbs hit zones feeding this part: {@code head chest left_arm right_arm left_leg right_leg}; empty = fed by the piece's slot only
 */
public record ExoPart(String id, int maxHp, Set<ExoFeature> binds, Set<String> limbs) {
    public ExoPart {
        if (id.isEmpty() || maxHp <= 0) {
            throw new IllegalArgumentException("exo part " + id + " maxHp " + maxHp);
        }
        binds = binds.isEmpty() ? Set.of() : java.util.Collections.unmodifiableSet(EnumSet.copyOf(binds));
        limbs = Set.copyOf(limbs);
    }
}
