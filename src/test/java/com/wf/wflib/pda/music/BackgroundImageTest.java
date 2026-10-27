package com.wf.wflib.pda.music;

import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BackgroundImageTest {
    private static byte[] encode(String format, int w, int h) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB), format, out);
        return out.toByteArray();
    }

    @Test
    void acceptsPngAndJpeg() throws IOException {
        byte[] png = encode("png", 64, 40);
        byte[] jpg = encode("jpg", 64, 40);
        assertDoesNotThrow(() -> BackgroundImage.check(png));
        assertDoesNotThrow(() -> BackgroundImage.check(jpg));
    }

    @Test
    void rejectsOversizeTruncatedAndForeign() throws IOException {
        byte[] png = encode("png", 3000, 8);
        assertThrows(IOException.class, () -> BackgroundImage.check(png));
        byte[] ok = encode("png", 64, 40);
        assertThrows(IOException.class, () -> BackgroundImage.check(Arrays.copyOf(ok, ok.length - 8)));
        byte[] jpg = encode("jpg", 64, 40);
        assertThrows(IOException.class, () -> BackgroundImage.check(Arrays.copyOf(jpg, jpg.length - 2)));
        byte[] junk = new byte[256];
        Arrays.fill(junk, (byte) 'A');
        assertThrows(IOException.class, () -> BackgroundImage.check(junk));
        assertThrows(IOException.class, () -> BackgroundImage.check("GIF89a".repeat(20).getBytes()));
    }

    @Test
    void names() {
        assertTrue(BackgroundImage.validName("forest night.PNG"));
        assertTrue(BackgroundImage.validName("a-b_(1).jpeg"));
        assertFalse(BackgroundImage.validName("../x.png"));
        assertFalse(BackgroundImage.validName("sub/x.png"));
        assertFalse(BackgroundImage.validName(".hidden.png"));
        assertFalse(BackgroundImage.validName("x.gif"));
        assertFalse(BackgroundImage.validName("x.png.exe"));
    }
}
