package com.wf.wflib.rail.align;

import com.wf.wflib.rail.api.AlignPoint;
import net.minecraft.world.level.border.WorldBorder;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RouteGeometryValidTest {

    private static final WorldBorder BORDER = new WorldBorder();

    private static boolean valid(AlignPoint... points) {
        return AlignmentEdits.valid(BORDER, List.of(points));
    }

    @Test
    void ordinaryRoutePasses() {
        assertTrue(valid(new AlignPoint(0, 0, 0), new AlignPoint(500, 300, 200), new AlignPoint(1000, 0, 0)));
    }

    @Test
    void nonFiniteRejected() {
        assertFalse(valid(new AlignPoint(Double.NaN, 0, 0), new AlignPoint(100, 0, 0)));
        assertFalse(valid(new AlignPoint(0, 0, 0), new AlignPoint(Double.POSITIVE_INFINITY, 0, 0)));
        assertFalse(valid(new AlignPoint(0, 0, 0), new AlignPoint(100, 0, Double.NaN), new AlignPoint(200, 50, 0)));
        assertFalse(valid(new AlignPoint(0, 0, 0), new AlignPoint(100, 0, Double.POSITIVE_INFINITY), new AlignPoint(200, 50, 0)));
    }

    @Test
    void outsideBorderRejected() {
        assertFalse(valid(new AlignPoint(0, 0, 0), new AlignPoint(6.0E7, 0, 0)));
    }

    @Test
    void overlongRejected() {
        assertTrue(valid(new AlignPoint(0, 0, 0), new AlignPoint(AlignmentEdits.MAX_LENGTH, 0, 0)));
        assertFalse(valid(new AlignPoint(0, 0, 0), new AlignPoint(AlignmentEdits.MAX_LENGTH + 1, 0, 0)));
    }
}
