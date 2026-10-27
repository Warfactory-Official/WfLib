package com.wf.wflib.anomaly;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AnomalyTypesTest {
    private static final Path PARTICLES = Path.of("src/main/resources/assets/wflib/xray/particles");

    static List<AnomalyType> all() throws IllegalAccessException {
        List<AnomalyType> types = new ArrayList<>();
        for (Field f : AnomalyType.class.getFields()) {
            if (Modifier.isStatic(f.getModifiers()) && f.getType() == AnomalyType.class) {
                types.add((AnomalyType) f.get(null));
            }
        }
        return types;
    }

    @Test
    void everyParticleGroupIsShippedAndEveryBlowoutHasASound() throws IllegalAccessException {
        List<AnomalyType> types = all();
        assertEquals(22, types.size());
        for (AnomalyType type : types) {
            for (String group : new String[]{type.look().idleParticles(), type.look().blowoutParticles()}) {
                if (group != null) {
                    assertTrue(Files.exists(PARTICLES.resolve(group + ".pg")), type.name() + ": " + group);
                }
            }
            if (!(type.behaviour() instanceof FieldTuning)) {
                assertTrue(type.sounds().blowout() != null, type.name());
            }
        }
    }

    @Test
    void fieldsHurtLessTowardTheRimAndNotAtAllOutside() {
        FieldTuning thermal = (FieldTuning) AnomalyType.THERMAL_FIELD.behaviour();
        float middle = thermal.hit(0.0D, true);
        assertEquals(0.33F * MincerTuning.FULL_HIT * FieldTuning.PULSE_TICKS / 20.0F, middle, 1e-4);
        assertEquals(middle * (1.0F - 0.54F), thermal.hit(thermal.radius() - 1.0E-6D, true), 1e-3);
        assertEquals(0.0F, thermal.hit(thermal.radius(), true));
        assertTrue(!thermal.trips(0.0D), "a field never blows out");
        FieldTuning rad = (FieldTuning) AnomalyType.RADIATION_FIELD.behaviour();
        assertEquals(0.04D * FieldTuning.FULL_DOSE / 20.0D, rad.dose(0.0D), 1e-9);
        assertEquals(0.0F, ((FieldTuning) AnomalyType.NO_GRAVITY.behaviour()).hit(0.0D, true));
    }

    @Test
    void fuzzOnlyWakesForSomethingFastAndTheGrabberForAnything() {
        BlastTuning fuzz = (BlastTuning) AnomalyType.BURNT_FUZZ.behaviour();
        BlastTuning grabber = (BlastTuning) AnomalyType.GRABBER.behaviour();
        assertTrue(fuzz.hairs() && grabber.hairs());
        // Walking is 0.216 blocks/tick, sprinting 0.28.
        assertTrue(fuzz.minSpeed() > 0.216D && fuzz.minSpeed() < 0.28D);
        assertTrue(grabber.minSpeed() < 0.1D);
        assertTrue(!((BlastTuning) AnomalyType.ACID.behaviour()).hairs());
    }
}
