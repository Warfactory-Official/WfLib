package com.wf.wflib.drone.client;

import com.wf.wflib.drone.api.DroneTask;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;

class DroneCommandTest {
    private static final Vec3 SELF = new Vec3(100.5, 64, -20.5);
    private static final Vec3 PICKED = new Vec3(7, 70, 7);
    private static final Vec3 BASE = new Vec3(300.5, 80, 400.5);

    private static final DroneCommand.Places PLACES = new DroneCommand.Places() {
        @Override
        public Vec3 self() {
            return SELF;
        }

        @Nullable
        @Override
        public Vec3 look() {
            return null;
        }

        @Nullable
        @Override
        public Vec3 marker(String name) {
            return name.equals("forward base") || name.equals("#1") ? BASE : null;
        }

        @Override
        public Vec3 picked() {
            return PICKED;
        }
    };

    private static DroneCommand.Order parse(String line) {
        return DroneCommand.parse(line, PLACES);
    }

    @Test
    void coordinatesAreBlockCentredAndYDefaultsToYours() {
        assertEquals(new DroneCommand.Order("moveto", new Vec3(592.5, 64, 5277.5), 0), parse("goto 592 5277"));
        assertEquals(new DroneCommand.Order("strike", new Vec3(10.5, 90.5, -3.5), 0), parse("STRIKE 10 90 -4"));
    }

    @Test
    void tildeIsRelativeToYou() {
        assertEquals(new DroneCommand.Order("moveto", new Vec3(150.5, 64, -20.5), 0), parse("moveto ~50 ~"));
    }

    @Test
    void radiusMarkersAndPickedTarget() {
        assertEquals(new DroneCommand.Order("loiter", BASE, 20), parse("orbit Forward Base r20"));
        assertEquals(new DroneCommand.Order("hold", BASE, 0), parse("hold #1"));
        assertEquals(new DroneCommand.Order("deliver", PICKED, 0), parse("deliver"));
    }

    @Test
    void controlsAndAltitude() {
        assertEquals("stop", parse("hold").verb());
        assertEquals("rtb", parse("home").verb());
        assertEquals(new DroneCommand.Order("alt", SELF, 60), parse("alt 60"));
        assertEquals(new DroneCommand.Order("climb", SELF, -10), parse("alt -10"));
    }

    @Test
    void fillerWordsAreSkipped() {
        assertEquals(new DroneCommand.Order("moveto", new Vec3(1000.5, 64, 5800.5), 0), parse("moveto at 1000 5800"));
        assertEquals(new DroneCommand.Order("moveto", BASE, 0), parse("go to forward base"));
    }

    @Test
    void chainsBecomeOneProgram() {
        List<DroneCommand.Order> orders = DroneCommand.parseAll("moveto 0 0 then moveto 10 0, hold 10 10", PLACES);
        assertEquals(List.of("moveto", "moveto", "hold"), orders.stream().map(DroneCommand.Order::verb).toList());
        assertInstanceOf(DroneTask.MoveTo.class, DroneCommand.task(orders.get(0)));
        assertEquals(0.0, ((DroneTask.Loiter) DroneCommand.task(orders.get(2))).radius());
        assertEquals(20.0, ((DroneTask.Loiter) DroneCommand.task(parse("loiter 1 1 r20"))).radius());
        assertInstanceOf(DroneTask.Exfil.class, DroneCommand.task(parse("rtb")));
        assertThrows(IllegalArgumentException.class, () -> DroneCommand.task(parse("land")));
    }

    @Test
    void badLinesSayWhy() {
        assertEquals("unknown 'fly' (help)", assertThrows(IllegalArgumentException.class, () -> parse("fly 1 2")).getMessage());
        assertEquals("no marker 'nowhere'", assertThrows(IllegalArgumentException.class, () -> parse("moveto nowhere")).getMessage());
        assertEquals("nothing under the crosshair", assertThrows(IllegalArgumentException.class, () -> parse("strike look")).getMessage());
        assertThrows(IllegalArgumentException.class, () -> parse("land 1 2"));
        assertThrows(IllegalArgumentException.class, () -> parse("alt high"));
    }
}
