package com.wf.wflib.soldier.api;

import com.google.gson.Gson;
import com.google.gson.JsonObject;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Random;

/**
 * Random soldier names from S.T.A.L.K.E.R. Anomaly's name tables: stalker first names and nicknames for a side that is
 * friendly to players, the bandit tables for one that hunts them. A player name has 16 characters and a soldier's is
 * {@code <name>_<n>}, so a name is drawn that fits whole instead of being cut.
 */
public final class SoldierNames {
    /** Room left for {@code _<n>}, two digits. */
    private static final int STEM = 13;
    private static final JsonObject TABLES = load();

    private SoldierNames() {
    }

    /** A name stem like {@code Vasya_Ace}, at most 13 characters of letters, digits and one underscore. */
    public static String random(Random rng, boolean hostile) {
        JsonObject t = TABLES.getAsJsonObject(hostile ? "bandit" : "stalker");
        String first = pick(t.getAsJsonArray("first").asList().stream().map(e -> e.getAsString()).toList(), rng, STEM - 3);
        int room = STEM - first.length() - 1;
        List<String> fits = t.getAsJsonArray("last").asList().stream().map(e -> e.getAsString()).filter(s -> s.length() <= room).toList();
        return fits.isEmpty() ? first : first + "_" + pick(fits, rng, room);
    }

    private static String pick(List<String> all, Random rng, int max) {
        List<String> fits = all.stream().filter(s -> s.length() <= max).toList();
        return fits.get(rng.nextInt(fits.size()));
    }

    private static JsonObject load() {
        try (var in = SoldierNames.class.getResourceAsStream("/data/wflib/soldier_names.json")) {
            return new Gson().fromJson(new InputStreamReader(in, StandardCharsets.UTF_8), JsonObject.class);
        } catch (Exception e) {
            throw new IllegalStateException("soldier_names.json is missing from the WFLib jar", e);
        }
    }
}
