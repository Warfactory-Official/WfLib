package com.wf.wflib.contract.api;

import com.wf.wflib.contract.profile.Profile;
import com.wf.wflib.contract.profile.ProfileBook;
import com.wf.wflib.contract.profile.Rank;
import net.minecraft.server.MinecraftServer;

import java.util.UUID;

/**
 * An operator's standing, for other mods (shop gates, hiring gates): read it, nudge the reputation. Contracts and kills
 * are what normally move it. Server thread only; works for offline players.
 */
public final class Standing {
    private Standing() {
    }

    public static int rep(MinecraftServer server, UUID player) {
        return ProfileBook.get(server).of(player).rep;
    }

    /** The rank id: unknown, contractor, proven, trusted or elite. */
    public static String rank(MinecraftServer server, UUID player) {
        return ProfileBook.get(server).of(player).rank().id();
    }

    /** True when the player's rank is {@code rankId} or above; false for an id that is not a rank. */
    public static boolean atLeast(MinecraftServer server, UUID player, String rankId) {
        return Rank.byId(rankId).map(r -> ProfileBook.get(server).of(player).rank().atLeast(r)).orElse(false);
    }

    /** Adds (or, negative, removes) reputation, never below zero. Returns the new total. */
    public static int bumpRep(MinecraftServer server, UUID player, int delta) {
        ProfileBook book = ProfileBook.get(server);
        Profile p = book.of(player);
        p.rep = Math.max(0, p.rep + delta);
        book.setDirty();
        return p.rep;
    }

    /** Soldiers of {@code faction} this player has killed. */
    public static int kills(MinecraftServer server, UUID player, String faction) {
        return ProfileBook.get(server).of(player).kills.getOrDefault(faction, 0);
    }
}
