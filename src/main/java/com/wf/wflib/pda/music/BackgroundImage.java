package com.wf.wflib.pda.music;

import java.io.IOException;
import java.util.regex.Pattern;

/** Server-side PDA background gate: flat {@code <world>/pda_backgrounds/*.{png,jpg,jpeg}}, header-checked PNG / JPEG. */
public final class BackgroundImage {
    public static final int MAX_BYTES = 4 << 20;
    public static final int MAX_DIM = 2048;
    public static final int MAX_ENTRIES = 64;
    public static final int MAX_NAME = 64;
    private static final Pattern NAME = Pattern.compile("[A-Za-z0-9 _.()\\-]{1,56}\\.(?i:png|jpe?g)");
    private static final byte[] PNG = {(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A};
    private static final byte[] IEND = {0, 0, 0, 0, 'I', 'E', 'N', 'D', (byte) 0xAE, 0x42, 0x60, (byte) 0x82};

    private BackgroundImage() {
    }

    public static boolean validName(String name) {
        return !name.startsWith(".") && !name.contains("..") && NAME.matcher(name).matches();
    }

    /** @throws IOException not a PNG / JPEG within {@link #MAX_BYTES} and {@link #MAX_DIM}. */
    public static void check(byte[] b) throws IOException {
        if (b.length < 32 || b.length > MAX_BYTES) {
            throw new IOException("bad size");
        }
        int[] dim = isPng(b) ? png(b) : (b[0] & 0xFF) == 0xFF && (b[1] & 0xFF) == 0xD8 ? jpeg(b) : null;
        if (dim == null || dim[0] < 1 || dim[1] < 1 || dim[0] > MAX_DIM || dim[1] > MAX_DIM) {
            throw new IOException("not a PNG / JPEG image or too large");
        }
    }

    private static boolean isPng(byte[] b) {
        for (int i = 0; i < PNG.length; i++) {
            if (b[i] != PNG[i]) {
                return false;
            }
        }
        return true;
    }

    private static int be32(byte[] b, int at) {
        return (b[at] & 0xFF) << 24 | (b[at + 1] & 0xFF) << 16 | (b[at + 2] & 0xFF) << 8 | b[at + 3] & 0xFF;
    }

    private static int[] png(byte[] b) throws IOException {
        if (be32(b, 8) != 13 || be32(b, 12) != 0x49484452) {
            throw new IOException("no IHDR");
        }
        for (int i = 0; i < IEND.length; i++) {
            if (b[b.length - IEND.length + i] != IEND[i]) {
                throw new IOException("truncated PNG");
            }
        }
        return new int[]{be32(b, 16), be32(b, 20)};
    }

    private static int[] jpeg(byte[] b) throws IOException {
        if ((b[b.length - 2] & 0xFF) != 0xFF || (b[b.length - 1] & 0xFF) != 0xD9) {
            throw new IOException("truncated JPEG");
        }
        int i = 2;
        while (i + 4 <= b.length) {
            if ((b[i] & 0xFF) != 0xFF) {
                throw new IOException("bad JPEG marker");
            }
            int marker = b[i + 1] & 0xFF;
            if (marker == 0xFF) {
                i++;
                continue;
            }
            if (marker == 0xD8 || marker == 0x01 || marker >= 0xD0 && marker <= 0xD7) {
                i += 2;
                continue;
            }
            int len = (b[i + 2] & 0xFF) << 8 | b[i + 3] & 0xFF;
            if (len < 2) {
                throw new IOException("bad JPEG segment");
            }
            if (marker >= 0xC0 && marker <= 0xCF && marker != 0xC4 && marker != 0xC8 && marker != 0xCC) {
                if (i + 9 > b.length) {
                    throw new IOException("bad JPEG frame");
                }
                return new int[]{(b[i + 7] & 0xFF) << 8 | b[i + 8] & 0xFF, (b[i + 5] & 0xFF) << 8 | b[i + 6] & 0xFF};
            }
            if (marker == 0xDA) {
                break;
            }
            i += 2 + len;
        }
        throw new IOException("no JPEG frame");
    }
}
