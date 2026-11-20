package com.wf.wflib.continuum;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Turns Continuum's armour numbers into WFLib armour profile elements: the first draft of the table, to be tuned.
 *
 * <p>Continuum rates a worn <i>set</i>: a flat threshold and a resistance fraction per damage category ({@code PHYS}, {@code EXPL},
 * {@code FIRE}, {@code EN}), a catch-all {@code other}, and exact damage types; its radiation resistance is a sum whose dose multiplier is
 * {@code 10^-sum}. WFLib rates a <i>piece</i> per protection type and stacks the layers. So a set's numbers are split over its pieces by the
 * weights Continuum itself uses for radiation (helmet 0.2, chest 0.4, legs 0.3, boots 0.1, renormalised over the pieces the set has): the
 * thresholds add up to the set's, and the resistances combine to the set's if layers combine multiplicatively
 * ({@code 1 - (1-R)^weight} per piece), which is to be checked against WFLib's resolver.
 */
final class ArmourConverter {
    private static final double HELMET = 0.2, CHEST = 0.4, LEGS = 0.3, BOOTS = 0.1;

    /** WFLib protection types by Continuum category. {@code PHYS} is every kind of bodily harm a bullet, blade or blow does. */
    private static final Map<String, String[]> CATEGORIES = Map.of(
            "PHYS", new String[]{"kinetic", "sharp", "impact"},
            "EXPL", new String[]{"blast"},
            "FIRE", new String[]{"thermal"},
            "EN", new String[]{"electric"});
    private static final String[] ALL = {"kinetic", "sharp", "impact", "blast", "thermal", "electric"};

    private record Dtdr(double dt, double dr) {
    }

    private ArmourConverter() {
    }

    /** Target (item id or {@code #tag}) to profile element JSON. */
    static Map<String, JsonObject> convert(JsonObject sets, JsonObject radiation) {
        Map<String, Map<String, Dtdr>> rows = new LinkedHashMap<>();
        if (sets != null && sets.has("values")) {
            for (Map.Entry<String, JsonElement> e : sets.getAsJsonObject("values").entrySet()) {
                JsonObject set = e.getValue().getAsJsonObject();
                if (!set.has("resistance")) continue;
                Map<String, Dtdr> setRows = setRows(set.getAsJsonObject("resistance"));
                String[] pieces = {str(set, "helmet"), e.getKey(), str(set, "legs"), str(set, "boots")};
                double[] weights = {HELMET, CHEST, LEGS, BOOTS};
                double total = 0;
                for (int i = 0; i < 4; i++) if (pieces[i] != null) total += weights[i];
                for (int i = 0; i < 4; i++) {
                    if (pieces[i] == null) continue;
                    double share = weights[i] / total;
                    Map<String, Dtdr> mine = rows.computeIfAbsent(pieces[i], k -> new LinkedHashMap<>());
                    for (Map.Entry<String, Dtdr> r : setRows.entrySet()) {
                        Dtdr d = r.getValue();
                        mine.putIfAbsent(r.getKey(), new Dtdr(d.dt * share, 1.0D - Math.pow(1.0D - Math.min(d.dr, 0.999D), share)));
                    }
                }
            }
        }
        Map<String, Double> rad = new LinkedHashMap<>();
        if (radiation != null && radiation.has("values")) {
            for (Map.Entry<String, JsonElement> e : radiation.getAsJsonObject("values").entrySet()) rad.put(e.getKey(), e.getValue().getAsDouble());
        }
        Map<String, JsonObject> out = new LinkedHashMap<>();
        Map<String, Boolean> targets = new LinkedHashMap<>();
        rows.keySet().forEach(k -> targets.put(k, true));
        rad.keySet().forEach(k -> targets.put(k, true));
        for (String target : targets.keySet()) {
            JsonObject rowsJson = new JsonObject();
            Map<String, Dtdr> mine = rows.get(target);
            if (mine != null) {
                for (Map.Entry<String, Dtdr> r : mine.entrySet()) {
                    JsonObject row = new JsonObject();
                    row.addProperty("dt", round(r.getValue().dt));
                    row.addProperty("dr", round(r.getValue().dr));
                    rowsJson.add(r.getKey(), row);
                }
            }
            Double res = rad.get(target);
            if (res != null && res > 0) {
                JsonObject row = new JsonObject();
                row.addProperty("dr", round(1.0D - Math.pow(10.0D, -res)));
                rowsJson.add("radiation", row);
            }
            if (rowsJson.size() == 0) continue;
            JsonObject element = new JsonObject();
            element.addProperty("targets", target);
            element.add("rows", rowsJson);
            element.addProperty("priority", -10);
            out.put(GeneratedPack.fileName(target), element);
        }
        return out;
    }

    private static Map<String, Dtdr> setRows(JsonObject resistance) {
        Map<String, Dtdr> rows = new LinkedHashMap<>();
        Dtdr other = null;
        if (resistance.has("other")) other = dtdr(resistance.getAsJsonObject("other"));
        if (resistance.has("category")) {
            for (Map.Entry<String, JsonElement> c : resistance.getAsJsonObject("category").entrySet()) {
                String[] types = CATEGORIES.get(c.getKey().toUpperCase());
                if (types == null) continue;
                Dtdr d = dtdr(c.getValue().getAsJsonObject());
                for (String t : types) rows.put(t, d);
            }
        }
        if (other != null) for (String t : ALL) rows.putIfAbsent(t, other);
        return rows;
    }

    private static Dtdr dtdr(JsonObject o) {
        return new Dtdr(o.has("threshold") ? o.get("threshold").getAsDouble() : 0.0D, o.has("resistance") ? o.get("resistance").getAsDouble() : 0.0D);
    }

    private static String str(JsonObject o, String key) {
        return o.has(key) && !o.get(key).isJsonNull() ? o.get(key).getAsString() : null;
    }

    private static double round(double v) {
        return Math.round(v * 10000.0D) / 10000.0D;
    }
}
