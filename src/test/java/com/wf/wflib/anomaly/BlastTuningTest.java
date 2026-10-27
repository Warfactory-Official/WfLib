package com.wf.wflib.anomaly;

import net.minecraft.util.RandomSource;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BlastTuningTest {
    private static final BlastTuning SPRINGBOARD = (BlastTuning) AnomalyType.SPRINGBOARD.behaviour();

    @Test
    void hitsHardestInTheMiddleAndNothingOutside() {
        double r = SPRINGBOARD.radius();
        assertEquals((float) (SPRINGBOARD.maxPower() * MincerTuning.FULL_HIT), SPRINGBOARD.hit(0.0D, true), 1e-5);
        assertTrue(SPRINGBOARD.hit(0.5D * r, true) < SPRINGBOARD.hit(0.2D * r, true));
        assertEquals(0.0F, SPRINGBOARD.hit(r, true));
        assertTrue(SPRINGBOARD.trips(r));
        assertTrue(!SPRINGBOARD.trips(r + 0.01D));
    }

    @Test
    void throwsUpAndAwayHarderInTheMiddle() {
        RandomSource random = RandomSource.create(7L);
        double r = SPRINGBOARD.radius();
        assertEquals(Vec3.ZERO, SPRINGBOARD.throwAt(r + 0.1D, random));
        for (int i = 0; i < 2000; i++) {
            Vec3 middle = SPRINGBOARD.throwAt(0.0D, random);
            Vec3 rim = SPRINGBOARD.throwAt(0.95D * r, random);
            assertTrue(middle.y >= 0.0D && rim.y >= 0.0D, "never into the ground");
            assertEquals(SPRINGBOARD.launch(), middle.length(), 1e-9);
            assertTrue(rim.length() < middle.length());
            assertTrue(rim.length() >= SPRINGBOARD.launch() / 3.0D - 1e-9);
        }
    }

    @Test
    void electroBurnerAndAcidHurtWithoutThrowingAndSmallLivingThingsTripThem() {
        RandomSource random = RandomSource.create(3L);
        for (AnomalyType type : new AnomalyType[]{AnomalyType.ELECTRO, AnomalyType.BURNER, AnomalyType.ACID}) {
            BlastTuning blast = (BlastTuning) type.behaviour();
            assertEquals(Vec3.ZERO, blast.throwAt(0.0D, random), type.name());
            assertTrue(blast.hit(0.0D, true) > 0.0F, type.name());
            assertTrue(blast.smallTrips(), type.name() + ": a chicken sets it off");
        }
        assertTrue(!SPRINGBOARD.smallTrips());
    }

    @Test
    void burnerHitsAtOnceThenBurnsSevenSecondsAndSetsAlight() {
        BlastTuning burner = (BlastTuning) AnomalyType.BURNER.behaviour();
        assertTrue(burner.explosionTick() <= 2);
        assertEquals(140, burner.blowoutTicks());
        assertTrue(burner.igniteSeconds() > 0);
        assertTrue(AnomalyType.BURNER.sounds().endsWithBlowout(), "zhar_blow (12 s) stops with the 7 s flame");
    }

    @Test
    void acidSplashesAgainSoonAndElectroIsGoneAWhile() {
        BlastTuning acid = (BlastTuning) AnomalyType.ACID.behaviour();
        assertTrue(acid.blowoutTicks() + acid.cooldownTicks() <= 40, "re-splashes about every 1.7 s");
        BlastTuning electro = (BlastTuning) AnomalyType.ELECTRO.behaviour();
        assertTrue(electro.cooldownTicks() >= 60, "accamulate_time 3 s");
        assertEquals(0.0F, AnomalyType.ELECTRO.look().spentPresence(), "gone while recharging");
    }

    @Test
    void eachHitsWithTheDamageItsArmourResolves() {
        assertEquals(com.wf.wflib.damage.WFDamageTypes.ELECTRIC, AnomalyType.ELECTRO.damage());
        assertEquals(com.wf.wflib.damage.WFDamageTypes.FIRE, AnomalyType.BURNER.damage());
        assertEquals(com.wf.wflib.damage.WFDamageTypes.ACID, AnomalyType.ACID.damage());
        assertEquals(com.wf.wflib.damage.WFDamageTypes.GRAVITY_ANOMALY, AnomalyType.SPRINGBOARD.damage());
    }
}
