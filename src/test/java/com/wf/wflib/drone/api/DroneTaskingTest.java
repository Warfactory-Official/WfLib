package com.wf.wflib.drone.api;

import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

class DroneTaskingTest {
    private static final Vec3 A = new Vec3(1, 64, 1);
    private static final Vec3 B = new Vec3(9, 64, 9);

    @Test
    void trailingWaypointHoldsInsteadOfFlyingHome() {
        DroneProgram program = DroneTasking.holdAtEnd(List.of(new DroneTask.MoveTo(A), new DroneTask.MoveTo(B)));
        assertInstanceOf(DroneTask.MoveTo.class, program.tasks().get(0));
        DroneTask.Loiter last = assertInstanceOf(DroneTask.Loiter.class, program.tasks().get(1));
        assertEquals(B, last.at());
        assertEquals(true, last.stationary());
    }

    @Test
    void otherEndingsAreKept() {
        assertInstanceOf(DroneTask.Strike.class,
                DroneTasking.holdAtEnd(List.of(new DroneTask.MoveTo(A), new DroneTask.Strike(B))).tasks().get(1));
        assertInstanceOf(DroneTask.Exfil.class, DroneTasking.holdAtEnd(List.of(new DroneTask.Exfil())).tasks().get(0));
    }
}
