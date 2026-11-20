package com.wf.wflib.contract.intel;

import java.util.Random;

/** Analysing recovered data: how good the intel is decides how exactly it points at the target. */
public final class Intel {
    private Intel() {
    }

    /** {@code jitter}: how far, in blocks, the marked point may be from the real one. */
    public enum Quality {
        POOR(50, 200), FAIR(35, 60), GOOD(15, 0);

        public final int weight, jitter;

        Quality(int weight, int jitter) {
            this.weight = weight;
            this.jitter = jitter;
        }
    }

    public static Quality roll(Random random) {
        int total = 0;
        for (Quality q : Quality.values()) total += q.weight;
        int pick = random.nextInt(total);
        for (Quality q : Quality.values()) {
            if (pick < q.weight) return q;
            pick -= q.weight;
        }
        return Quality.POOR;
    }

    /** A point within the quality's jitter of the real one, as an offset {@code [dx, dz]} (uniform over the disc). */
    public static int[] offset(Quality q, Random random) {
        if (q.jitter == 0) return new int[]{0, 0};
        double angle = random.nextDouble() * Math.PI * 2, dist = Math.sqrt(random.nextDouble()) * q.jitter;
        return new int[]{(int) Math.round(Math.cos(angle) * dist), (int) Math.round(Math.sin(angle) * dist)};
    }

    public static Quality byName(String name) {
        for (Quality q : Quality.values()) if (q.name().equalsIgnoreCase(name)) return q;
        return Quality.POOR;
    }
}
