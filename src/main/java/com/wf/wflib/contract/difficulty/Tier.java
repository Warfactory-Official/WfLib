package com.wf.wflib.contract.difficulty;

/** What a tier (0 to 10, fractional) means for a contract: how many soldiers, how good, what it pays. */
public final class Tier {
    private Tier() {
    }

    /** Soldiers in a squad of {@code base}: 25% more per tier. */
    public static int squadSize(int base, double tier) {
        return Math.max(1, Math.min(60, (int) Math.round(base * (1 + 0.25 * tier))));
    }

    /** Equipment grade 0..3 of the soldiers met at {@code tier}: conscript, regular, veteran, elite. */
    public static int grade(double tier) {
        return (int) Math.max(0, Math.min(3, Math.floor(tier / 2.5)));
    }

    public static String gradeName(int grade) {
        return com.wf.wflib.soldier.api.RandomForces.gradeName(grade);
    }

    /** Pay scales with danger: +35% per tier. */
    public static int money(int base, double tier, double scale) {
        return (int) Math.min(10_000_000, Math.round(base * (1 + 0.35 * tier) * scale));
    }

    public static int rep(int base, double tier) {
        return (int) Math.min(1000, Math.round(base * (1 + 0.15 * tier)));
    }

    /** Reputation rank (0..4) a job of this tier asks for: tier 1.5 and up needs Contractor, 3 Proven, 5 Trusted, 7.5 Elite. */
    public static int gateRank(double tier) {
        return tier >= 7.5 ? 4 : tier >= 5 ? 3 : tier >= 3 ? 2 : tier >= 1.5 ? 1 : 0;
    }

    /** "Tier 3.4 (Veteran)". */
    public static String label(double tier) {
        return String.format(java.util.Locale.ROOT, "Tier %.1f (%s)", tier, gradeName(grade(tier)));
    }
}
