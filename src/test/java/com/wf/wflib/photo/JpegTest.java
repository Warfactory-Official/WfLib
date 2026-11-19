package com.wf.wflib.photo;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.io.ByteArrayOutputStream;
import org.junit.jupiter.api.Test;

class JpegTest {
    /** SOI, an APPn and a DQT-like segment to skip, then a baseline frame header. */
    static byte[] jpeg(int width, int height, int frameMarker) {
        ByteArrayOutputStream o = new ByteArrayOutputStream();
        o.writeBytes(new byte[]{(byte) 0xFF, (byte) 0xD8});
        o.writeBytes(new byte[]{(byte) 0xFF, (byte) 0xE0, 0, 6, 'J', 'F', 'I', 'F'});
        o.writeBytes(new byte[]{(byte) 0xFF, (byte) 0xDB, 0, 4, 1, 2});
        o.writeBytes(new byte[]{(byte) 0xFF, (byte) frameMarker, 0, 11, 8, (byte) (height >> 8), (byte) height,
                (byte) (width >> 8), (byte) width, 1, 1, 0x11, 0});
        o.writeBytes(new byte[]{(byte) 0xFF, (byte) 0xDA, 0, 2});
        return o.toByteArray();
    }

    @Test
    void readsBaselineAndProgressiveSize() {
        assertArrayEquals(new int[]{960, 540}, Jpeg.size(jpeg(960, 540, 0xC0)));
        assertArrayEquals(new int[]{1280, 720}, Jpeg.size(jpeg(1280, 720, 0xC2)));
    }

    @Test
    void rejectsNonJpegAndTruncation() {
        assertNull(Jpeg.size(new byte[0]));
        assertNull(Jpeg.size(new byte[]{(byte) 0x89, 'P', 'N', 'G', 0, 0, 0, 0}));
        byte[] ok = jpeg(960, 540, 0xC0);
        assertNull(Jpeg.size(java.util.Arrays.copyOf(ok, 12)));
    }

    @Test
    void scanBeforeFrameIsRejected() {
        byte[] b = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xDA, 0, 2, 0, 0};
        assertNull(Jpeg.size(b));
    }

    @Test
    void zeroSizeIsRejected() {
        assertNull(Jpeg.size(jpeg(0, 540, 0xC0)));
    }

    @Test
    void defineHuffmanTableIsNotAFrame() {
        // DHT (C4) sits in the 0xC0..0xCF range but carries no size; a file with only that is not a photo.
        assertNull(Jpeg.size(jpeg(960, 540, 0xC4)));
    }
}
