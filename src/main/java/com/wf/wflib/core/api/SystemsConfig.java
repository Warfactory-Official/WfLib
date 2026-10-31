package com.wf.wflib.core.api;

import net.neoforged.fml.ModContainer;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.neoforge.common.ModConfigSpec;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;

/** {@code <modid>-systems.toml}: one boolean per non-mandatory system id. STARTUP configs load inside {@code registerConfig}. */
final class SystemsConfig {
    private SystemsConfig() {}

    static String file(String modId) {
        return modId + "-systems.toml";
    }

    static Predicate<String> register(String modId, ModContainer container, List<? extends WFSystem> systems) {
        ModConfigSpec.Builder builder = new ModConfigSpec.Builder();
        builder.comment("System on/off. A system another one needs is turned on anyway, with a loading warning.")
                .push("systems");
        Map<String, ModConfigSpec.BooleanValue> values = new LinkedHashMap<>();
        for (WFSystem system : systems) {
            if (!system.mandatory()) {
                values.put(system.id(), builder.define(system.id(), system.defaultEnabled()));
            }
        }
        builder.pop();
        container.registerConfig(ModConfig.Type.STARTUP, builder.build(), file(modId));
        return id -> !values.containsKey(id) || values.get(id).get();
    }
}
