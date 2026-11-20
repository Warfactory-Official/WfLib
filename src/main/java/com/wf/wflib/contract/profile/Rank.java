package com.wf.wflib.contract.profile;

import java.util.Locale;
import java.util.Optional;

/** Operator ranks, by reputation. Thresholds are the shipped ones; every contract awards reputation, so every rank is reachable. */
public enum Rank {
    UNKNOWN(0), CONTRACTOR(100), PROVEN(300), TRUSTED(700), ELITE(1500);

    public final int threshold;

    Rank(int threshold) {
        this.threshold = threshold;
    }

    public String id() {
        return name().toLowerCase(Locale.ROOT);
    }

    /** The highest rank whose threshold the reputation reaches. */
    public static Rank of(int reputation) {
        Rank best = UNKNOWN;
        for (Rank r : values()) if (reputation >= r.threshold) best = r;
        return best;
    }

    public static Optional<Rank> byId(String id) {
        for (Rank r : values()) if (r.id().equals(id)) return Optional.of(r);
        return Optional.empty();
    }

    public boolean atLeast(Rank other) {
        return ordinal() >= other.ordinal();
    }
}
