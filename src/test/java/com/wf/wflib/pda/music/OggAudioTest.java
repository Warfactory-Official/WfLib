package com.wf.wflib.pda.music;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.Arrays;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OggAudioTest {
    private static byte[] fixture(String name) throws IOException {
        try (var in = OggAudioTest.class.getResourceAsStream("/pda_music/" + name)) {
            return in.readAllBytes();
        }
    }

    @Test
    void vorbisProbe() throws IOException {
        OggAudio.Probe p = OggAudio.probe(fixture("tone.ogg"));
        assertEquals(OggAudio.Codec.VORBIS, p.codec());
        assertEquals(44_100, p.rate());
        assertEquals(2, p.channels());
        assertTrue(Math.abs(p.millis() - 2000) < 100, "millis " + p.millis());
        assertEquals("Test Tone", p.tags().title());
        assertEquals("Sine", p.tags().artist());
        assertEquals("Fixtures", p.tags().album());
        assertNotNull(p.tags().cover());
        assertEquals((byte) 0x89, p.tags().cover()[0]);
    }

    @Test
    void opusProbe() throws IOException {
        OggAudio.Probe p = OggAudio.probe(fixture("tone.opus"));
        assertEquals(OggAudio.Codec.OPUS, p.codec());
        assertEquals(48_000, p.rate());
        assertEquals(1, p.channels());
        assertTrue(Math.abs(p.millis() - 2000) < 100, "millis " + p.millis());
        assertEquals("Test Tone", p.tags().title());
        assertNotNull(p.tags().cover());
    }

    @Test
    void randomBytesRejected() {
        byte[] junk = new byte[4096];
        new Random(1).nextBytes(junk);
        assertThrows(IOException.class, () -> OggAudio.probe(junk));
        assertThrows(IOException.class, () -> OggAudio.probe(new byte[0]));
        assertThrows(IOException.class, () -> OggAudio.probe("MZ".repeat(2000).getBytes()));
    }

    @Test
    void everyCorruptionRejected() throws IOException {
        byte[] good = fixture("tone.opus");
        Random random = new Random(7);
        for (int i = 0; i < 300; i++) {
            byte[] bad = good.clone();
            bad[random.nextInt(bad.length)] ^= (byte) (1 + random.nextInt(255));
            assertThrows(IOException.class, () -> OggAudio.probe(bad));
        }
    }

    @Test
    void truncatedRejected() throws IOException {
        byte[] good = fixture("tone.ogg");
        assertThrows(IOException.class, () -> OggAudio.probe(Arrays.copyOf(good, good.length - 1)));
        assertThrows(IOException.class, () -> OggAudio.probe(Arrays.copyOf(good, good.length / 2)));
    }

    @Test
    void appendedDataRejected() throws IOException {
        byte[] good = fixture("tone.ogg");
        byte[] bad = Arrays.copyOf(good, good.length + 64);
        assertThrows(IOException.class, () -> OggAudio.probe(bad));
    }

    @Test
    void names() {
        assertTrue(OggAudio.validName("a.ogg"));
        assertTrue(OggAudio.validName("Album/01 Track.OPUS"));
        assertFalse(OggAudio.validName("../a.ogg"));
        assertFalse(OggAudio.validName("a/../b.ogg"));
        assertFalse(OggAudio.validName("/etc/a.ogg"));
        assertFalse(OggAudio.validName("a\\b.ogg"));
        assertFalse(OggAudio.validName("a.mp3"));
        assertFalse(OggAudio.validName(""));
        assertFalse(OggAudio.validName("a\0.ogg"));
        assertFalse(OggAudio.validName("x".repeat(OggAudio.MAX_NAME) + ".ogg"));
    }
}
