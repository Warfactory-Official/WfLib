package com.wf.wflib.contract;

import com.wf.wflib.soldier.api.Recruiting;
import com.wf.wflib.soldier.api.Wallet;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import java.util.UUID;

/** Contract money through whatever {@link Wallet} an economy mod registered; with none, everything is refused and rewards fall back to items. */
public final class Money {
    private Money() {
    }

    public static boolean available() {
        return Recruiting.wallet() != null;
    }

    public static String format(long amount) {
        Wallet w = Recruiting.wallet();
        return w == null ? amount + " credits" : w.format(amount);
    }

    public static boolean canAfford(ServerPlayer player, int amount) {
        Wallet w = Recruiting.wallet();
        return w != null && w.has(player, amount);
    }

    public static boolean charge(ServerPlayer player, int amount, String reason) {
        Wallet w = Recruiting.wallet();
        return w != null && amount > 0 && w.take(player, amount);
    }

    public static boolean pay(MinecraftServer server, UUID player, int amount, String reason) {
        Wallet w = Recruiting.wallet();
        return w != null && amount > 0 && w.give(server, player, amount, reason);
    }
}
