package com.wf.wflib.labs;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import net.neoforged.fml.ModList;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.fml.config.ModConfigs;
import net.neoforged.fml.event.config.ModConfigEvent;
import net.neoforged.neoforge.common.ModConfigSpec;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * In-memory overrides of loaded {@link ModConfigSpec} values for the duration of one run.
 * Never written to disk; owners refresh via the {@link ModConfigEvent.Reloading} they already handle.
 */
final class ConfigOverrides implements AutoCloseable {
    private record Change(ModConfig config, ModConfigSpec.ConfigValue<Object> value, List<String> path,
                          boolean present, Object original) {
    }

    private record Plan(ModConfig config, ModConfigSpec.ConfigValue<Object> value, Object coerced, String file,
                      String key, JsonElement json) {
    }

    private final List<Change> changes = new ArrayList<>();
    private final JsonObject effective = new JsonObject();

    private ConfigOverrides() {
    }

    /**
     * @param request {@code {"<file>": {"<dotted.key>": value}}}; null or empty = no-op
     * @throws IllegalArgumentException unknown file / key, wrong type or out of range; nothing is changed then
     */
    static ConfigOverrides apply(JsonObject request) {
        ConfigOverrides out = new ConfigOverrides();
        if (request == null || request.isEmpty()) {
            return out;
        }
        List<Plan> plan = new ArrayList<>();
        for (Map.Entry<String, JsonElement> file : request.entrySet()) {
            ModConfig config = ModConfigs.getFileMap().get(file.getKey());
            if (config == null) {
                throw new IllegalArgumentException("no loaded config file " + file.getKey() + "; have "
                        + ModConfigs.getFileMap().keySet().stream().sorted().toList());
            }
            if (!(config.getSpec() instanceof ModConfigSpec spec) || config.getLoadedConfig() == null) {
                throw new IllegalArgumentException(file.getKey() + " is not a loaded ModConfigSpec config");
            }
            if (!file.getValue().isJsonObject()) {
                throw new IllegalArgumentException(file.getKey() + ": want an object of dotted.key: value");
            }
            for (Map.Entry<String, JsonElement> kv : file.getValue().getAsJsonObject().entrySet()) {
                if (!(spec.getValues().get(kv.getKey()) instanceof ModConfigSpec.ConfigValue<?> raw)) {
                    throw new IllegalArgumentException(file.getKey() + ": no key " + kv.getKey());
                }
                @SuppressWarnings("unchecked")
                ModConfigSpec.ConfigValue<Object> value = (ModConfigSpec.ConfigValue<Object>) raw;
                Object coerced = coerce(file.getKey() + ":" + kv.getKey(), value.getRaw(), kv.getValue());
                if (!value.getSpec().test(coerced)) {
                    throw new IllegalArgumentException(file.getKey() + ": " + kv.getKey() + " = " + kv.getValue()
                            + " is out of range" + (value.getSpec().getRange() != null
                            ? " " + value.getSpec().getRange() : ""));
                }
                plan.add(new Plan(config, value, coerced, file.getKey(), kv.getKey(), kv.getValue()));
            }
        }
        try {
            for (Plan p : plan) {
                List<String> path = p.value().getPath();
                boolean present = p.config().getLoadedConfig().config().contains(path);
                out.changes.add(new Change(p.config(), p.value(), path, present, p.value().getRaw()));
                p.value().set(p.coerced());
                if (!out.effective.has(p.file())) {
                    out.effective.add(p.file(), new JsonObject());
                }
                out.effective.getAsJsonObject(p.file()).add(p.key(), p.json());
            }
            out.refresh();
        } catch (RuntimeException e) {
            out.close();
            throw e;
        }
        return out;
    }

    /** {@code {"<file>": {"<key>": value}}} as applied; empty when no-op. */
    JsonObject effective() {
        return effective;
    }

    private void refresh() {
        for (ModConfig config : changes.stream().map(Change::config).distinct().toList()) {
            ModList.get().getModContainerById(config.getModId()).orElseThrow()
                    .acceptEvent(new ModConfigEvent.Reloading(config));
        }
    }

    /** Restores every original value (absent keys are removed again), then refreshes the owners. */
    @Override
    public void close() {
        RuntimeException failure = null;
        for (int i = changes.size() - 1; i >= 0; i--) {
            Change c = changes.get(i);
            try {
                if (c.present()) {
                    c.value().set(c.original());
                } else {
                    c.config().getLoadedConfig().config().remove(c.path());
                    c.value().clearCache();
                }
            } catch (RuntimeException e) {
                if (failure == null) {
                    failure = e;
                } else {
                    failure.addSuppressed(e);
                }
            }
        }
        try {
            refresh();
        } catch (RuntimeException e) {
            if (failure == null) {
                failure = e;
            } else {
                failure.addSuppressed(e);
            }
        }
        changes.clear();
        if (failure != null) {
            throw failure;
        }
    }

    private static Object coerce(String what, Object current, JsonElement json) {
        if (!(json instanceof JsonPrimitive p)) {
            throw new IllegalArgumentException(what + ": want a scalar, got " + json);
        }
        if (current instanceof Double) {
            return number(what, p).doubleValue();
        }
        if (current instanceof Integer) {
            double d = number(what, p).doubleValue();
            if (d != Math.rint(d) || Math.abs(d) > Integer.MAX_VALUE) {
                throw new IllegalArgumentException(what + ": want an int, got " + json);
            }
            return (int) d;
        }
        if (current instanceof Long) {
            double d = number(what, p).doubleValue();
            if (d != Math.rint(d)) {
                throw new IllegalArgumentException(what + ": want a long, got " + json);
            }
            return (long) d;
        }
        if (current instanceof Boolean) {
            if (!p.isBoolean()) {
                throw new IllegalArgumentException(what + ": want a boolean, got " + json);
            }
            return p.getAsBoolean();
        }
        if (current instanceof String) {
            return p.getAsString();
        }
        if (current instanceof Enum<?> e) {
            for (Object constant : e.getDeclaringClass().getEnumConstants()) {
                if (((Enum<?>) constant).name().equalsIgnoreCase(p.getAsString())) {
                    return constant;
                }
            }
            throw new IllegalArgumentException(what + ": no constant " + json + " in " + e.getDeclaringClass().getSimpleName());
        }
        throw new IllegalArgumentException(what + ": unsupported value type " + (current == null ? "null" : current.getClass().getSimpleName()));
    }

    private static Number number(String what, JsonPrimitive p) {
        if (!p.isNumber()) {
            throw new IllegalArgumentException(what + ": want a number, got " + p);
        }
        return p.getAsNumber();
    }
}
