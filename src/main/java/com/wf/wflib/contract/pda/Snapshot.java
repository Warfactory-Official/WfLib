package com.wf.wflib.contract.pda;

import com.google.gson.Gson;
import java.util.List;
import org.jetbrains.annotations.Nullable;

/**
 * What the PDA app shows one operator, built on the server and sent as JSON (one payload, so the app can grow a field
 * without a new packet). Every number that is shown as money arrives already formatted, so the client needs no economy.
 */
public record Snapshot(String rank, int rep, int nextAt, String nextRank, int completed, int failed, int kills, String earnings,
                       int drives, List<Offer> offers, @Nullable Active active, List<Heat> heat, List<String> news,
                       String blackSite, String war, String notice, String tier) {
    private static final Gson GSON = new Gson();

    /** @param intel the intel quality when this is a lead the operator holds, else empty */
    public record Offer(String id, String title, String description, String money, int rep, int minutes, int minDist, int maxDist,
                        String buyIn, String gate, String employer, String intel, List<String> objectives) {}

    public record Active(String title, int x, int z, int secondsLeft, List<String> objectives) {}

    /** @param state WATCHED, HUNTED or CLOSED; {@code points} are what one bribe takes off, {@code bribe} its formatted price */
    public record Heat(String faction, int value, String state, int points, String bribe) {}

    public static Snapshot empty() {
        return new Snapshot("unknown", 0, 0, "", 0, 0, 0, "", 0, List.of(), null, List.of(), List.of(), "", "", "", "");
    }

    public String toJson() {
        return GSON.toJson(this);
    }

    public static Snapshot fromJson(String json) {
        return GSON.fromJson(json, Snapshot.class);
    }

    public Snapshot withNotice(String text) {
        return new Snapshot(rank, rep, nextAt, nextRank, completed, failed, kills, earnings, drives, offers, active, heat, news, blackSite, war, text, tier);
    }
}
