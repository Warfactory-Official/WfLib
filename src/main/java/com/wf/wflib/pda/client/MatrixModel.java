package com.wf.wflib.pda.client;

import java.util.Random;

/**
 * cmatrix rain, no rendering: one falling streak per column, a bright head and a trail that fades out. Pure so it can be
 * tested; {@link MatrixRain} draws it.
 */
final class MatrixModel {
    /** Glyphs a column cycles through: what the default font has of cmatrix's. */
    static final String GLYPHS = "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz:.=*+-<>|/\\\"'";
    private static final float MIN_SPEED = 6, MAX_SPEED = 18;

    private final Random random;
    private int rows;
    private float[] head = new float[0];
    private float[] speed = new float[0];
    private int[] length = new int[0];
    private long[] seed = new long[0];
    private float time;

    MatrixModel(long seed) {
        random = new Random(seed);
    }

    int columns() {
        return head.length;
    }

    int rows() {
        return rows;
    }

    /** Streaks spread over the pane, head starts staggered above it. */
    void resize(int columns, int rows) {
        if (columns == head.length && rows == this.rows) {
            return;
        }
        this.rows = rows;
        head = new float[columns];
        speed = new float[columns];
        length = new int[columns];
        seed = new long[columns];
        for (int c = 0; c < columns; c++) {
            respawn(c, true);
        }
    }

    private void respawn(int c, boolean first) {
        speed[c] = MIN_SPEED + random.nextFloat() * (MAX_SPEED - MIN_SPEED);
        length[c] = Math.max(4, rows / 3 + random.nextInt(Math.max(1, rows * 2 / 3)));
        head[c] = first ? -random.nextFloat() * rows * 1.5F : -random.nextFloat() * rows * 0.5F;
        seed[c] = random.nextLong();
    }

    void step(float seconds) {
        time += seconds;
        for (int c = 0; c < head.length; c++) {
            head[c] += speed[c] * seconds;
            if (head[c] - length[c] > rows) {
                respawn(c, false);
            }
        }
    }

    /** 0 = nothing; 1 = the head; between = trail, fading to 0 at its tail. */
    float brightness(int column, int row) {
        float behind = head[column] - row;
        if (behind < 0 || behind >= length[column]) {
            return 0;
        }
        if (behind < 1) {
            return 1;
        }
        float fade = 1 - behind / length[column];
        return fade * fade;
    }

    boolean isHead(int column, int row) {
        float behind = head[column] - row;
        return behind >= 0 && behind < 1;
    }

    /** Stable per cell, flickering: cells near the head change faster, as cmatrix's. */
    char glyph(int column, int row) {
        float behind = head[column] - row;
        float rate = behind < 4 ? 14 : 3;
        long epoch = (long) (time * rate);
        long h = seed[column] ^ (row * 0x9E3779B97F4A7C15L) ^ (epoch * 0xC2B2AE3D27D4EB4FL);
        h ^= h >>> 33;
        h *= 0xFF51AFD7ED558CCDL;
        h ^= h >>> 33;
        return GLYPHS.charAt((int) Long.remainderUnsigned(h, GLYPHS.length()));
    }
}
