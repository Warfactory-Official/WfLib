package com.wf.wflib.client.xray;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class XrScreenFilesTest {
    private static final Path ANIMS = Path.of("src/main/resources/assets/wflib/xray/anims");

    private static List<Path> files(String ext) throws IOException {
        try (Stream<Path> s = Files.walk(ANIMS)) {
            return s.filter(p -> p.toString().endsWith(ext)).toList();
        }
    }

    @Test
    void everyShippedEffectParsesAndRuns() throws IOException {
        List<Path> ppe = files(".ppe");
        assertFalse(ppe.isEmpty());
        for (Path p : ppe) {
            XrPostEffect e = XrPostEffect.parse(Files.readAllBytes(p));
            assertTrue(e.length() >= 0.0F, p + " runs backwards");
            XrPostEffect.Pp pp = e.sample(e.length() * 0.5F, new XrPostEffect.Pp());
            for (float v : new float[] {pp.gray, pp.blur, pp.dualH, pp.dualV, pp.noise, pp.grain, pp.fps, pp.base[0],
                    pp.add[0], pp.grayColor[0]}) {
                assertTrue(Float.isFinite(v), p + " gave " + v);
            }
        }
    }

    @Test
    void everyShippedCameraMotionParses() throws IOException {
        List<Path> anm = files(".anm");
        assertFalse(anm.isEmpty());
        for (Path p : anm) {
            XrCameraMotion m = XrCameraMotion.parse(Files.readAllBytes(p));
            assertTrue(m.length() > 0.0F, p + " has no length");
            float[] o = m.sample(m.length() * 0.3F, new float[6]);
            for (float v : o) {
                assertTrue(Float.isFinite(v) && Math.abs(v) < 10.0F, p + " gave " + v);
            }
        }
    }

    @Test
    void knownEffectsDoWhatTheyAreFor() throws IOException {
        XrPostEffect alcohol = XrPostEffect.parse(Files.readAllBytes(ANIMS.resolve("alcohol.ppe")));
        float dual = 0.0F;
        for (float t = 0.0F; t < alcohol.length(); t += 0.05F) {
            XrPostEffect.Pp pp = alcohol.sample(t, new XrPostEffect.Pp());
            dual = Math.max(dual, Math.abs(pp.dualH) + Math.abs(pp.dualV));
        }
        assertTrue(dual > 0.001F, "alcohol never doubles the view");
        XrPostEffect black = XrPostEffect.parse(Files.readAllBytes(ANIMS.resolve("black_infinite.ppe")));
        XrPostEffect.Pp pp = black.sample(black.length() * 0.5F, new XrPostEffect.Pp());
        // (image * base + add) * 2 with add -1: black.
        assertTrue(pp.add[0] <= -0.99F, "black_infinite is not black: add " + pp.add[0]);
    }

    @Test
    void linearAndSteppedKeysAndBehaviours() {
        ByteBuffer b = ByteBuffer.allocate(64).order(ByteOrder.LITTLE_ENDIAN);
        b.put((byte) XrEnvelope.CONSTANT).put((byte) XrEnvelope.REPEAT).putShort((short) 3);
        b.putFloat(0.0F).putFloat(0.0F).put((byte) XrEnvelope.STEP);
        key(b, 2.0F, 1.0F, XrEnvelope.LINE);
        key(b, 4.0F, 2.0F, XrEnvelope.LINE);
        b.flip();
        XrEnvelope e = XrEnvelope.read(b);
        assertEquals(0.0F, e.evaluate(-1.0F));
        assertEquals(1.0F, e.evaluate(0.5F), 1e-5F);
        assertEquals(3.0F, e.evaluate(1.5F), 1e-5F);
        assertEquals(e.evaluate(0.5F), e.evaluate(2.5F), 1e-5F);
        assertEquals(2.0F, e.length());
    }

    private static void key(ByteBuffer b, float value, float time, int shape) {
        b.putFloat(value).putFloat(time).put((byte) shape);
        for (int i = 0; i < 7; i++) {
            b.putShort((short) 32767);
        }
    }
}
