package com.wf.wflib.core.features;

import com.wf.wflib.core.api.Features;
import com.wf.wflib.core.features.FeatureCatalogue.Feature;
import com.wf.wflib.core.features.FeatureCatalogue.Group;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.neoforge.common.ModConfigSpec;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * {@code wflib-features.toml}: a section per {@link Group} with its {@code enabled} master switch and one boolean per feature.
 * Read once at startup into {@link Features}; the screen edits the live values and saves, and the difference from what was read
 * is what needs a restart.
 */
public final class FeatureConfig {
    public static final String FILE = "wflib-features.toml";
    public static final String ENABLED = "enabled";

    private static Map<String, ModConfigSpec.BooleanValue> values = Map.of();
    private static ModConfigSpec spec;

    private FeatureConfig() {
    }

    /** Registers and loads the file (a STARTUP config loads here), then installs what it says into {@link Features}. */
    public static void register(ModContainer container) {
        ModConfigSpec.Builder b = new ModConfigSpec.Builder();
        Map<String, ModConfigSpec.BooleanValue> v = new LinkedHashMap<>();
        for (Group g : FeatureCatalogue.GROUPS) {
            b.comment(g.title() + ": " + g.blurb()).push(g.id());
            v.put(key(g.id(), ENABLED), b.comment("Master switch for this whole group. Off turns every feature below off; the choices below are kept.").define(ENABLED, true));
            for (Feature f : g.features()) {
                v.put(key(g.id(), f.id()), b.comment(f.title() + ": " + f.blurb()
                        + (f.system() != null ? " (needs a restart)" : f.needsSystem() != null ? " (needs " + f.needsSystem() + ")" : "")
                        + (f.apps().isEmpty() ? "" : " Hides the PDA app" + (f.apps().size() > 1 ? "s " : " ") + String.join(", ", f.apps()) + " when off.")).define(f.id(), f.defaultOn()));
            }
            b.pop();
        }
        values = v;
        spec = b.build();
        container.registerConfig(ModConfig.Type.STARTUP, spec, FILE);
        Features.install(read());
    }

    private static String key(String group, String id) {
        return group + "." + id;
    }

    /** What the file says right now (not what was read at startup). Keys: {@code <group>.enabled} and {@code <feature>}. */
    public static Map<String, Boolean> read() {
        Map<String, Boolean> out = new HashMap<>();
        for (Group g : FeatureCatalogue.GROUPS) {
            out.put(g.id() + "." + ENABLED, values.get(key(g.id(), ENABLED)).get());
            for (Feature f : g.features()) out.put(f.id(), values.get(key(g.id(), f.id())).get());
        }
        return out;
    }

    /** Sets one value and writes the file. {@code feature} null sets the group's master switch. */
    public static void set(Group group, Feature feature, boolean on) {
        values.get(key(group.id(), feature == null ? ENABLED : feature.id())).set(on);
    }

    public static void save() {
        if (spec != null && spec.isLoaded()) spec.save();
    }

    /** Back to the defaults of {@link FeatureCatalogue}. */
    public static void reset() {
        for (Group g : FeatureCatalogue.GROUPS) {
            set(g, null, true);
            for (Feature f : g.features()) set(g, f, f.defaultOn());
        }
    }
}
