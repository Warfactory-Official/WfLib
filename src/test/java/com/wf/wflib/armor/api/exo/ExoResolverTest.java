package com.wf.wflib.armor.api.exo;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ExoResolverTest {
    private static final ExoStats ON = new ExoStats(true, 30, java.util.Map.of("grenades", 4), 0.6, 0.4, true, true);
    private static final ExoStats OFF = new ExoStats(false, 0, java.util.Map.of(), 0, 0, false, false);
    private static final ExoSpec SPEC = new ExoSpec(ON, OFF, 2, List.of(
            new ExoPart("arms", 100, Set.of(ExoFeature.RECOIL, ExoFeature.SWAY, ExoFeature.TRIGGER, ExoFeature.SHOULDER), Set.of("left_arm", "right_arm")),
            new ExoPart("legs", 100, Set.of(ExoFeature.SPRINT, ExoFeature.CARRY), Set.of("left_leg", "right_leg")),
            new ExoPart("core", 100, Set.of(ExoFeature.REACTOR, ExoFeature.LIFE_SUPPORT), Set.of("chest"))), true, false, Set.of());

    private static PartState[] ok() {
        return new PartState[]{PartState.OK, PartState.OK, PartState.OK};
    }

    @Test
    void poweredPristine() {
        ExoEffect e = ExoResolver.effective(SPEC, true, ok());
        assertTrue(e.canSprint());
        assertEquals(30, e.carryBonusKg());
        assertEquals(0.4, e.recoilFactor(), 1e-9);
        assertFalse(e.triggerLocked());
    }

    @Test
    void unpoweredUsesUnpoweredBlock() {
        ExoEffect e = ExoResolver.effective(SPEC, false, ok());
        assertFalse(e.canSprint());
        assertEquals(1, e.recoilFactor(), 1e-9);
        assertFalse(e.shoulder());
    }

    @Test
    void criticalArmsPenaliseAndLockTrigger() {
        PartState[] s = ok();
        s[0] = PartState.CRITICAL;
        ExoEffect e = ExoResolver.effective(SPEC, true, s);
        assertEquals(ExoResolver.PENALTY, e.recoilFactor(), 1e-9);
        assertTrue(e.triggerLocked());
        assertFalse(e.shoulder());
    }

    @Test
    void compensationNeverPassesFloor() {
        ExoEffect piles = ExoResolver.effective(SPEC, true, ok()).combine(ExoResolver.effective(SPEC, true, ok()))
                .combine(ExoResolver.effective(SPEC, true, ok()));
        assertTrue(piles.recoilFactor() < 0.1);
        assertEquals(0.1, piles.recoilMul(0.1), 1e-9);
        assertTrue(new ExoEffect(true, 0, java.util.Map.of(), 0, 0, false, false, false).recoilMul(0.05) > 0);
    }

    @Test
    void destroyedReactorForcesUnpowered() {
        PartState[] s = ok();
        s[2] = PartState.DESTROYED;
        assertFalse(ExoResolver.effective(SPEC, true, s).canSprint());
        assertEquals(1, ExoResolver.effective(SPEC, true, s).recoilFactor(), 1e-9);
    }

    @Test
    void criticalReactorDoublesDraw() {
        PartState[] s = ok();
        s[2] = PartState.CRITICAL;
        assertEquals(2, ExoResolver.drawMultiplier(SPEC, s));
        assertEquals(1, ExoResolver.drawMultiplier(SPEC, ok()));
    }

    @Test
    void stateThresholds() {
        assertEquals(PartState.OK, PartState.of(100, 100));
        assertEquals(PartState.DAMAGED, PartState.of(59, 100));
        assertEquals(PartState.CRITICAL, PartState.of(24, 100));
        assertEquals(PartState.DESTROYED, PartState.of(0, 100));
    }

    @Test
    void combineCarriesAndGates() {
        ExoEffect a = ExoResolver.effective(SPEC, true, ok());
        PartState[] s = ok();
        s[1] = PartState.CRITICAL;
        ExoEffect b = ExoResolver.effective(SPEC, true, s);
        ExoEffect c = a.combine(b);
        assertFalse(c.canSprint());
        assertEquals(30 + 15, c.carryBonusKg(), 1e-9);
        assertEquals(4 + 2, c.groupBonus("grenades"));
    }
}
