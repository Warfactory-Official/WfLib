package com.wf.wflib.smoking.client;

import com.wf.wflib.view.api.HandClip;
import com.wf.wflib.smoking.SmokingTimeline;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SmokingClipTest {

    private static HandClip clip() throws IOException {
        try (Reader in = new InputStreamReader(SmokingClipTest.class.getResourceAsStream(
                "/assets/wflib/smoking/cigarette.json"), StandardCharsets.UTF_8)) {
            return HandClip.parse(in);
        }
    }

    @Test
    void everyTrackHoldsOneRowPerFrame() throws IOException {
        HandClip c = clip();
        int frames = Math.round(c.duration * c.fps) + 1;
        for (float[][] track : new float[][][]{c.right, c.left, c.prop("cig")}) {
            assertEquals(frames, track.length, 1);
            assertEquals(7, track[0].length);
        }
        assertEquals(frames, c.prop("lighter").length, 1);
        assertEquals(8, c.prop("lighter")[0].length);
        assertEquals(c.right.length, c.cam.length);
        assertEquals(3, c.cam[0].length);
    }

    @Test
    void theClipFitsInsideTheUse() throws IOException {
        assertTrue(clip().duration * 20 <= SmokingTimeline.TICKS);
    }

    @Test
    void quaternionsAreUnit() throws IOException {
        for (float[] row : clip().right) {
            assertEquals(1.0F, (float) Math.sqrt(row[3] * row[3] + row[4] * row[4] + row[5] * row[5] + row[6] * row[6]), 1.0e-3F);
        }
    }

    @Test
    void theCigaretteIsInTheRightHandWhileSmokingAndAtTheMouth() throws IOException {
        HandClip c = clip();
        Vector3f hand = new Vector3f(), cig = new Vector3f();
        Quaternionf q = new Quaternionf();
        for (float t = 1.8F; t < 6.8F; t += 0.5F) {
            c.sample(c.right, t, hand, q);
            c.sample(c.prop("cig"), t, cig, q);
            assertTrue(hand.distance(cig) < 0.12F, t + " s: hand " + hand + " cigarette " + cig);
            assertTrue(cig.y > -0.15F && cig.y < 0.0F && cig.z < -0.12F && cig.z > -0.30F,
                    t + " s: cigarette not at the mouth " + cig);
        }
    }

    @Test
    void theButtFacesTheMouthUntilTheFlick() throws IOException {
        HandClip c = clip();
        Vector3f cig = new Vector3f(), butt = new Vector3f(), face = new Vector3f();
        Quaternionf q = new Quaternionf();
        for (float t = 0.5F; t < 8.0F; t += 0.25F) {
            c.sample(c.prop("cig"), t, cig, q);
            q.transform(butt.set(0.0F, 1.0F, 0.0F));
            face.set(0.0F, -0.08F, 0.0F).sub(cig).normalize();
            assertTrue(butt.dot(face) > Math.cos(Math.toRadians(10.0)), t + " s: butt " + butt + " face " + face);
        }
    }

    @Test
    void samplingClampsToTheClip() throws IOException {
        HandClip c = clip();
        Vector3f a = new Vector3f(), b = new Vector3f();
        Quaternionf q = new Quaternionf();
        c.sample(c.right, -5.0F, a, q);
        c.sample(c.right, 0.0F, b, q);
        assertEquals(0.0F, a.distance(b), 1.0e-6F);
        c.sample(c.right, 100.0F, a, q);
        c.sample(c.right, c.duration, b, q);
        assertEquals(0.0F, a.distance(b), 1.0e-6F);
    }
}
