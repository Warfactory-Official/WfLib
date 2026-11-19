package com.wf.wflib.photo;

import org.jetbrains.annotations.Nullable;

/**
 * Reassembly of one fragmented upload on the server. Untrusted input: the fragment count, each fragment's size and the
 * running total are all bounded before anything is kept, and a repeated or out-of-range index kills the transfer.
 */
public final class Upload {
    private final long token;
    private final int total;
    private final byte[][] parts;
    private int received;
    private int bytes;
    private boolean dead;

    private Upload(long token, int total) {
        this.token = token;
        this.total = total;
        this.parts = new byte[total][];
    }

    public long token() {
        return token;
    }

    public boolean dead() {
        return dead;
    }

    /** @return a new upload for the first fragment, null if its declared fragment count is out of bounds. */
    @Nullable
    public static Upload start(long token, int total) {
        return total < 1 || total > PhotoLimits.MAX_CHUNKS ? null : new Upload(token, total);
    }

    /**
     * Takes one fragment.
     *
     * @return the whole file once every fragment is in; null while incomplete or after the upload died ({@link #dead()})
     */
    @Nullable
    public byte[] accept(int index, int declaredTotal, byte[] data) {
        if (dead) {
            return null;
        }
        if (declaredTotal != total || index < 0 || index >= total || parts[index] != null || data.length == 0
                || data.length > PhotoLimits.UP_CHUNK || bytes + data.length > PhotoLimits.MAX_BYTES) {
            dead = true;
            return null;
        }
        parts[index] = data;
        bytes += data.length;
        if (++received < total) {
            return null;
        }
        byte[] out = new byte[bytes];
        int at = 0;
        for (byte[] part : parts) {
            System.arraycopy(part, 0, out, at, part.length);
            at += part.length;
        }
        return out;
    }
}
