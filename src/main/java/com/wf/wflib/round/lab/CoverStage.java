package com.wf.wflib.round.lab;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.wf.wflib.labs.api.LabContext;
import com.wf.wflib.labs.api.LabCover;
import com.wf.wflib.labs.api.ScenarioStage;
import com.wf.wflib.labs.api.ScenarioState;

import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.function.Function;

/** Scenario layer {@code cover}: {@code block}, {@code thickness} m, {@code angle} deg (default 0) through {@link LabCover}. */
public final class CoverStage implements ScenarioStage {
    private static final Set<String> KEYS = Set.of("block", "thickness", "angle");

    private final Function<String, LabCover.Material> materials;

    public CoverStage() {
        this(LabCover::material);
    }

    public CoverStage(Function<String, LabCover.Material> materials) {
        this.materials = materials;
    }

    @Override
    public String id() {
        return "cover";
    }

    @Override
    public Result apply(LabContext ctx, ScenarioState in, JsonObject spec) {
        for (String k : spec.keySet()) {
            if (!KEYS.contains(k)) {
                throw new IllegalArgumentException("cover: unknown key " + k + "; have " + new java.util.TreeSet<>(KEYS));
            }
        }
        String block = string(spec, "block");
        double thickness = number(spec, "thickness", Double.NaN);
        double angle = number(spec, "angle", 0.0);
        if (!(thickness > 0.0)) {
            throw new IllegalArgumentException("cover: thickness must be > 0");
        }
        int samples = (int) ctx.paramNum("samples", 128);
        LabCover.Result r = LabCover.evaluate(in.round(), in.speedMs(), materials.apply(block), thickness, angle,
                samples, ctx.seed());
        Map<String, Object> detail = new TreeMap<>();
        detail.put("p_ricochet", r.pRicochet());
        detail.put("p_stop", r.pStop());
        detail.put("spent", r.spent());
        detail.put("depth", r.depth());
        detail.put("exit_pen", r.exitPen());
        detail.put("damage_frac", r.damageFrac());
        return new Result(r.exitSpeed(), r.pPerforate(), r.verdict().name(),
                block + " " + fmt(thickness) + " m @ " + fmt(angle) + " deg", detail);
    }

    private static String string(JsonObject spec, String key) {
        JsonElement e = spec.get(key);
        if (e == null || !e.isJsonPrimitive() || !e.getAsJsonPrimitive().isString()) {
            throw new IllegalArgumentException("cover: " + key + " must be a string");
        }
        return e.getAsString();
    }

    private static double number(JsonObject spec, String key, double fallback) {
        JsonElement e = spec.get(key);
        if (e == null) {
            if (Double.isNaN(fallback)) {
                throw new IllegalArgumentException("cover: missing " + key);
            }
            return fallback;
        }
        if (!e.isJsonPrimitive() || !e.getAsJsonPrimitive().isNumber()) {
            throw new IllegalArgumentException("cover: " + key + " must be a number");
        }
        return e.getAsDouble();
    }

    private static String fmt(double v) {
        return v == Math.rint(v) ? Long.toString((long) v) : Double.toString(v);
    }
}
