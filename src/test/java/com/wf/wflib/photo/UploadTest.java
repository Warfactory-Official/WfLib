package com.wf.wflib.photo;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class UploadTest {
    private static byte[] fill(int n, int v) {
        byte[] b = new byte[n];
        java.util.Arrays.fill(b, (byte) v);
        return b;
    }

    @Test
    void assemblesInAnyOrder() {
        Upload u = Upload.start(7, 3);
        assertNotNull(u);
        assertNull(u.accept(2, 3, fill(5, 3)));
        assertNull(u.accept(0, 3, fill(PhotoLimits.UP_CHUNK, 1)));
        byte[] whole = u.accept(1, 3, fill(PhotoLimits.UP_CHUNK, 2));
        assertNotNull(whole);
        assertTrue(whole.length == 2 * PhotoLimits.UP_CHUNK + 5);
        assertArrayEquals(fill(5, 3), java.util.Arrays.copyOfRange(whole, whole.length - 5, whole.length));
        assertFalse(u.dead());
    }

    @Test
    void refusesBadFragmentCounts() {
        assertNull(Upload.start(1, 0));
        assertNull(Upload.start(1, PhotoLimits.MAX_CHUNKS + 1));
        assertNotNull(Upload.start(1, PhotoLimits.MAX_CHUNKS));
    }

    @Test
    void duplicateIndexKillsTheUpload() {
        Upload u = Upload.start(1, 2);
        u.accept(0, 2, fill(10, 1));
        assertNull(u.accept(0, 2, fill(10, 1)));
        assertTrue(u.dead());
        assertNull(u.accept(1, 2, fill(10, 1)));
    }

    @Test
    void outOfRangeIndexAndWrongTotalKill() {
        Upload a = Upload.start(1, 2);
        assertNull(a.accept(2, 2, fill(10, 1)));
        assertTrue(a.dead());
        Upload b = Upload.start(1, 2);
        assertNull(b.accept(0, 3, fill(10, 1)));
        assertTrue(b.dead());
        Upload c = Upload.start(1, 2);
        assertNull(c.accept(-1, 2, fill(10, 1)));
        assertTrue(c.dead());
    }

    @Test
    void oversizedFragmentOrTotalKills() {
        Upload a = Upload.start(1, 2);
        assertNull(a.accept(0, 2, fill(PhotoLimits.UP_CHUNK + 1, 1)));
        assertTrue(a.dead());
        Upload b = Upload.start(1, PhotoLimits.MAX_CHUNKS);
        int fed = 0;
        for (int i = 0; i < PhotoLimits.MAX_CHUNKS && !b.dead(); i++) {
            b.accept(i, PhotoLimits.MAX_CHUNKS, fill(PhotoLimits.UP_CHUNK, 1));
            fed += PhotoLimits.UP_CHUNK;
        }
        // MAX_CHUNKS full fragments exceed MAX_BYTES (the cap is not a multiple of the fragment size).
        assertTrue(fed > PhotoLimits.MAX_BYTES);
        assertTrue(b.dead());
    }

    @Test
    void emptyFragmentKills() {
        Upload u = Upload.start(1, 1);
        assertNull(u.accept(0, 1, new byte[0]));
        assertTrue(u.dead());
    }
}
