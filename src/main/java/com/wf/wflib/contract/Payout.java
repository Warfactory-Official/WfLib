package com.wf.wflib.contract;

/** Splitting a contract's money across the party. */
public final class Payout {
    private Payout() {
    }

    /**
     * The shares of {@code money * teamBonus} for {@code members} people: even, with the remainder going one unit each to
     * the first members, so the shares add up exactly.
     *
     * @param teamBonus 1.0 = the party earns what a soloist would, split between them
     */
    public static int[] split(int money, int members, double teamBonus) {
        if (members < 1) throw new IllegalArgumentException("at least one member");
        long total = Math.round(Math.max(0, money) * Math.max(0, teamBonus));
        int[] out = new int[members];
        long each = total / members, extra = total % members;
        for (int i = 0; i < members; i++) out[i] = (int) Math.min(Integer.MAX_VALUE, each + (i < extra ? 1 : 0));
        return out;
    }
}
