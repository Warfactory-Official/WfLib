package com.wf.wflib.labs;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Parsed scenario JSON with every array-valued launcher / range / layer value expanded into cases. */
record ScenarioPlan(String name, int samples, List<Case> cases) {
    static final int MAX_CASES = 200_000;
    private static final Set<String> KEYS = Set.of("name", "launcher", "range", "layers", "samples");

    /**
     * @param launcher null => every selected launcher
     * @param range    null => every context range
     * @param label    swept values, {@code layer1.thickness=0.5 range=100}; empty when nothing is swept
     * @param layers   scalar specs, {@code stage} included
     */
    record Case(int id, String label, String launcher, Double range, List<JsonObject> layers) {
    }

    private record Axis(String label, List<JsonElement> values) {
    }

    /** @param samples -1 when the scenario sets none */
    static ScenarioPlan parse(JsonObject root) {
        for (String k : root.keySet()) {
            if (!KEYS.contains(k)) {
                throw new IllegalArgumentException("unknown scenario key " + k + "; have " + new java.util.TreeSet<>(KEYS));
            }
        }
        String name = root.has("name") ? string(root.get("name"), "name") : "scenario";
        int samples = -1;
        if (root.has("samples")) {
            samples = (int) number(root.get("samples"), "samples");
            if (samples < 1) {
                throw new IllegalArgumentException("samples must be >= 1");
            }
        }
        if (!root.has("layers") || !root.get("layers").isJsonArray() || root.getAsJsonArray("layers").isEmpty()) {
            throw new IllegalArgumentException("layers must be a non-empty array");
        }
        JsonArray layers = root.getAsJsonArray("layers");
        List<Axis> axes = new ArrayList<>();
        Axis launcher = axis("launcher", root);
        if (launcher != null) {
            launcher.values.forEach(v -> string(v, "launcher"));
            axes.add(launcher);
        } else if (root.has("launcher")) {
            string(root.get("launcher"), "launcher");
        }
        for (int i = 0; i < layers.size(); i++) {
            if (!layers.get(i).isJsonObject()) {
                throw new IllegalArgumentException("layer" + (i + 1) + " must be an object");
            }
            JsonObject layer = layers.get(i).getAsJsonObject();
            if (!layer.has("stage") || !layer.get("stage").isJsonPrimitive()) {
                throw new IllegalArgumentException("layer" + (i + 1) + " needs a string \"stage\"");
            }
            for (Map.Entry<String, JsonElement> e : layer.entrySet()) {
                if (e.getKey().equals("stage")) {
                    continue;
                }
                Axis a = axis("layer" + (i + 1) + "." + e.getKey(), e.getValue());
                if (a != null) {
                    axes.add(a);
                }
            }
        }
        Axis range = axis("range", root);
        if (range != null) {
            range.values.forEach(v -> number(v, "range"));
            axes.add(range);
        } else if (root.has("range")) {
            number(root.get("range"), "range");
        }
        long total = 1;
        for (Axis a : axes) {
            total *= a.values.size();
            if (total > MAX_CASES) {
                throw new IllegalArgumentException("scenario expands to more than " + MAX_CASES
                        + " cases; narrow a sweep axis (" + axes.stream().map(x -> x.label + "[" + x.values.size() + "]")
                        .toList() + ")");
            }
        }
        List<Case> cases = new ArrayList<>((int) total);
        for (int c = 0; c < total; c++) {
            int rest = c;
            JsonElement[] pick = new JsonElement[axes.size()];
            for (int a = axes.size() - 1; a >= 0; a--) {
                int n = axes.get(a).values.size();
                pick[a] = axes.get(a).values.get(rest % n);
                rest /= n;
            }
            cases.add(build(c, root, layers, axes, pick));
        }
        return new ScenarioPlan(name, samples, List.copyOf(cases));
    }

    private static Case build(int id, JsonObject root, JsonArray layers, List<Axis> axes, JsonElement[] pick) {
        Map<String, JsonElement> chosen = new java.util.HashMap<>();
        StringBuilder label = new StringBuilder();
        for (int a = 0; a < axes.size(); a++) {
            chosen.put(axes.get(a).label, pick[a]);
            if (label.length() > 0) {
                label.append(' ');
            }
            label.append(axes.get(a).label).append('=').append(text(pick[a]));
        }
        List<JsonObject> specs = new ArrayList<>();
        for (int i = 0; i < layers.size(); i++) {
            JsonObject src = layers.get(i).getAsJsonObject();
            JsonObject spec = new JsonObject();
            for (Map.Entry<String, JsonElement> e : src.entrySet()) {
                JsonElement swept = chosen.get("layer" + (i + 1) + "." + e.getKey());
                spec.add(e.getKey(), swept != null ? swept : e.getValue());
            }
            specs.add(spec);
        }
        JsonElement l = chosen.containsKey("launcher") ? chosen.get("launcher") : root.get("launcher");
        JsonElement r = chosen.containsKey("range") ? chosen.get("range") : root.get("range");
        return new Case(id, label.toString(), l == null ? null : l.getAsString(),
                r == null ? null : r.getAsDouble(), List.copyOf(specs));
    }

    private static Axis axis(String label, JsonObject root) {
        return root.has(label) ? axis(label, root.get(label)) : null;
    }

    private static Axis axis(String label, JsonElement value) {
        if (!value.isJsonArray()) {
            return null;
        }
        JsonArray a = value.getAsJsonArray();
        if (a.isEmpty()) {
            throw new IllegalArgumentException("sweep " + label + " is an empty array");
        }
        List<JsonElement> values = new ArrayList<>();
        for (JsonElement v : a) {
            if (!v.isJsonPrimitive()) {
                throw new IllegalArgumentException("sweep " + label + " holds scalars only");
            }
            values.add(v);
        }
        return new Axis(label, List.copyOf(values));
    }

    private static String text(JsonElement e) {
        return e.getAsString();
    }

    private static String string(JsonElement e, String key) {
        if (!e.isJsonPrimitive() || !e.getAsJsonPrimitive().isString()) {
            throw new IllegalArgumentException(key + " must be a string");
        }
        return e.getAsString();
    }

    private static double number(JsonElement e, String key) {
        if (!e.isJsonPrimitive() || !e.getAsJsonPrimitive().isNumber()) {
            throw new IllegalArgumentException(key + " must be a number");
        }
        return e.getAsDouble();
    }
}
