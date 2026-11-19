package com.wf.wflib.soldier.api;

/**
 * What an economy mod charges for soldiers, in whole units of its {@link Wallet}'s currency; 0 is free. Register with
 * {@link Recruiting#pricing(Pricing)}. It adds to the config's item prices, and it is the only thing that puts currency on hiring
 * and upkeep: without an economy mod the PDA shows no price at all (unless the config sets one).
 */
public interface Pricing {
    /** The price of hiring a soldier of this saved variant (any source that lists variants: the PDA, barracks). */
    int hire(String variant, SoldierRole role);

    /** The price of hiring an existing garrison soldier of this role. */
    default int garrison(SoldierRole role) {
        return hire("", role);
    }

    /** What one soldier of this role costs its owner each in-game day. */
    int upkeep(SoldierRole role);

    /** Whether wages are collected at all (the config can also turn them on). */
    default boolean upkeepOn() {
        return true;
    }
}
