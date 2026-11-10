package com.wf.wflib.soldier.api;

import net.minecraft.server.level.ServerPlayer;

/** A currency a mod provides (an economy mod's balance): hiring and wages can be priced in it. Register with {@link Recruiting#wallet(Wallet)}. */
public interface Wallet {
    /** What the amounts are called in a menu. */
    String unit();

    boolean has(ServerPlayer player, int amount);

    /** Takes {@code amount}; false (and nothing taken) when the player does not have it. */
    boolean take(ServerPlayer player, int amount);
}
