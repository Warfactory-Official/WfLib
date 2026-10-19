package com.wf.wflib.pda.client;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MatrixModelTest {
    private static MatrixModel pane() {
        MatrixModel m = new MatrixModel(7);
        m.resize(12, 20);
        return m;
    }

    @Test
    void aStreakIsBrightestAtItsHeadAndFadesBehindIt() {
        MatrixModel m = pane();
        // Fall until a column has its head well inside the pane.
        int column = -1;
        int row = -1;
        for (int i = 0; i < 400 && column < 0; i++) {
            m.step(0.05F);
            for (int c = 0; c < m.columns() && column < 0; c++) {
                for (int r = 6; r < m.rows(); r++) {
                    if (m.isHead(c, r)) {
                        column = c;
                        row = r;
                        break;
                    }
                }
            }
        }
        assertTrue(column >= 0, "no head came into view");
        assertEquals(1F, m.brightness(column, row));
        float near = m.brightness(column, row - 1);
        float far = m.brightness(column, row - 4);
        assertTrue(near > far && far > 0, "trail does not fade: " + near + " " + far);
        assertEquals(0F, m.brightness(column, row + 1), "nothing below the head");
    }

    @Test
    void everyStreakFallsPastTheBottomAndStartsAgain() {
        MatrixModel m = pane();
        int seen = 0;
        for (int i = 0; i < 2000; i++) {
            m.step(0.05F);
            for (int c = 0; c < m.columns(); c++) {
                if (m.isHead(c, m.rows() - 1)) {
                    seen++;
                }
            }
        }
        assertTrue(seen > m.columns(), "streaks do not recycle: " + seen);
    }

    @Test
    void glyphsComeFromTheSetAndAreStableWithinAFrame() {
        MatrixModel m = pane();
        m.step(1F);
        for (int c = 0; c < m.columns(); c++) {
            for (int r = 0; r < m.rows(); r++) {
                char g = m.glyph(c, r);
                assertTrue(MatrixModel.GLYPHS.indexOf(g) >= 0);
                assertEquals(g, m.glyph(c, r));
            }
        }
    }

    @Test
    void resizeKeepsStateWhenTheSizeIsUnchanged() {
        MatrixModel m = pane();
        m.step(0.5F);
        float before = m.brightness(3, 5);
        m.resize(12, 20);
        assertEquals(before, m.brightness(3, 5));
    }
}
