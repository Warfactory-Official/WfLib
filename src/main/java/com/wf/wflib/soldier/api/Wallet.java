package com.wf.wflib.soldier.api;

import net.minecraft.server.level.ServerPlayer;

/** A currency a mod provides (an economy mod's balance): hiring and wages can be priced in it. Register with {@link Recruiting#wallet(Wallet)}. */
public interface Wallet {
    /** What the amounts are called in a menu. */
    String unit();

    boolean has(ServerPlayer player, int amount);

    /** An amount as the economy writes it: "50 credits", "$50". */
    default String format(long amount) {
        return amount + " " + unit();
    }

    /** The player's balance for the PDA to show; negative when the economy cannot say. */
    default long balance(ServerPlayer player) {
        return -1;
    }

    /** Takes {@code amount}; false (and nothing taken) when the player does not have it. */
    boolean take(ServerPlayer player, int amount);
}
