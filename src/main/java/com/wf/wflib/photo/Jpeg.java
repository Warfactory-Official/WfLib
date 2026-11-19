package com.wf.wflib.photo;

import org.jetbrains.annotations.Nullable;

/** Header-only JPEG check: the server never decodes an upload, it only reads the frame size and enforces limits. */
public final class Jpeg {
    private Jpeg() {
    }

    /** {@code {width, height}} from the first frame header, or null when the bytes are not a well-formed JPEG head. */
    @Nullable
    public static int[] size(byte[] b) {
        if (b.length < 4 || (b[0] & 0xFF) != 0xFF || (b[1] & 0xFF) != 0xD8) {
            return null;
        }
        int i = 2;
        while (i + 3 < b.length) {
            if ((b[i] & 0xFF) != 0xFF) {
                return null;
            }
            int marker = b[i + 1] & 0xFF;
            if (marker == 0xFF) { // fill byte
                i++;
                continue;
            }
            if (marker == 0xD8 || marker == 0x01 || marker >= 0xD0 && marker <= 0xD7) { // no length
                i += 2;
                continue;
            }
            if (marker == 0xD9 || marker == 0xDA) { // end / scan before any frame header
                return null;
            }
            int length = (b[i + 2] & 0xFF) << 8 | b[i + 3] & 0xFF;
            if (length < 2) {
                return null;
            }
            boolean frame = marker >= 0xC0 && marker <= 0xCF && marker != 0xC4 && marker != 0xC8 && marker != 0xCC;
            if (frame) {
                if (length < 8 || i + 9 >= b.length) {
                    return null;
                }
                int height = (b[i + 5] & 0xFF) << 8 | b[i + 6] & 0xFF;
                int width = (b[i + 7] & 0xFF) << 8 | b[i + 8] & 0xFF;
                return width < 1 || height < 1 ? null : new int[]{width, height};
            }
            i += 2 + length;
        }
        return null;
    }
}
