package com.wf.wflib.contract.difficulty;

import java.util.Locale;

/** How the world's difficulty tier climbs with the in-game day. Pure, so every curve is testable. */
public final class Progression {
    public enum Mode {
        /** Always {@code start}. */
        FLAT,
        /** {@code start + perDay * day}. */
        LINEAR,
        /** One whole step of {@code perDay * stepDays} every {@code stepDays} days. */
        STEPPED,
        /** {@code start * (1 + perDay) ^ day}: slow at first, then steep (start 0 counts as 0.5). */
        EXPONENTIAL;

        public static Mode of(String name) {
            for (Mode m : values()) if (m.name().equalsIgnoreCase(name)) return m;
            throw new IllegalArgumentException("mode is one of flat, linear, stepped, exponential");
        }

        public String id() {
            return name().toLowerCase(Locale.ROOT);
        }
    }

    /** @param max the tier never exceeds this; @param stepDays only for STEPPED */
    public record Curve(Mode mode, double start, double perDay, double max, int stepDays) {
        public Curve {
            if (!(max > 0 && max <= 10)) throw new IllegalArgumentException("max tier is above 0 and at most 10");
            if (!(start >= 0 && start <= max)) throw new IllegalArgumentException("start tier is between 0 and the max");
            if (!(perDay >= 0 && perDay <= 10)) throw new IllegalArgumentException("rate is between 0 and 10");
            if (stepDays < 1 || stepDays > 365) throw new IllegalArgumentException("step is 1 to 365 days");
        }

        /** World tier on in-game {@code day} (0 = the first day), 0..max. */
        public double at(long day) {
            double d = Math.max(0, day);
            double t = switch (mode) {
                case FLAT -> start;
                case LINEAR -> start + perDay * d;
                case STEPPED -> start + Math.floor(d / stepDays) * perDay * stepDays;
                case EXPONENTIAL -> Math.max(start, 0.5) * Math.pow(1 + perDay, d);
            };
            return Math.min(max, Math.max(0, t));
        }
    }

    private Progression() {
    }

    /**
     * One player's tier: the world's, plus {@code weight} of their standing (rank index 0..4 scaled onto 0..max), so a veteran meets more
     * on day one than a newcomer does. Never above the curve's max.
     */
    public static double playerTier(Curve curve, long day, int rankIndex, double weight) {
        double standing = Math.max(0, Math.min(4, rankIndex)) / 4.0 * curve.max();
        return Math.min(curve.max(), curve.at(day) + weight * standing);
    }
}
