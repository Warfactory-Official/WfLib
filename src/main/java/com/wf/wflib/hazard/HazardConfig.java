package com.wf.wflib.hazard;

import net.neoforged.fml.event.config.ModConfigEvent;
import net.neoforged.neoforge.common.ModConfigSpec;

import java.util.function.UnaryOperator;

/** Item hazard tunables; immutable snapshot swapped on load. */
public final class HazardConfig {

    public static final ModConfigSpec SPEC;

    private static final ModConfigSpec.IntValue HAZARD_RATE_VALUE;
    private static final ModConfigSpec.IntValue ITEM_HAZARD_DROP_TICKRATE_VALUE;
    private static final ModConfigSpec.BooleanValue DISABLE_ASBESTOS_VALUE;
    private static final ModConfigSpec.BooleanValue DISABLE_COAL_VALUE;
    private static final ModConfigSpec.BooleanValue DISABLE_HOT_VALUE;
    private static final ModConfigSpec.BooleanValue DISABLE_COLD_VALUE;
    private static final ModConfigSpec.BooleanValue DISABLE_EXPLOSIVE_VALUE;
    private static final ModConfigSpec.BooleanValue DISABLE_HYDRO_VALUE;
    private static final ModConfigSpec.BooleanValue DISABLE_BLINDING_VALUE;
    private static final ModConfigSpec.BooleanValue DISABLE_TOXIC_VALUE;
    private static final ModConfigSpec.BooleanValue ENABLE_528_VALUE;

    /**
     * @param hazardRate             ticks between item hazard applications
     * @param itemHazardDropTickrate ticks between dropped-item hazard updates
     * @param enable528              reacher divides dose by 49 instead of sqrt
     */
    public record Values(int hazardRate, int itemHazardDropTickrate, boolean disableAsbestos, boolean disableCoal,
                         boolean disableHot, boolean disableCold, boolean disableExplosive, boolean disableHydro,
                         boolean disableBlinding, boolean disableToxic, boolean enable528) {
    }

    private static volatile Values values =
            new Values(5, 2, false, false, false, false, false, false, false, false, false);

    static {
        ModConfigSpec.Builder b = new ModConfigSpec.Builder();
        b.comment("On/off: wflib-systems.toml, key 'hazard'.");
        HAZARD_RATE_VALUE = b.comment("Ticks between item hazard applications.")
                .defineInRange("hazardRate", 5, 1, Integer.MAX_VALUE);
        ITEM_HAZARD_DROP_TICKRATE_VALUE = b.comment("Ticks between dropped-item hazard updates.")
                .defineInRange("itemHazardDropTickrate", 2, 1, Integer.MAX_VALUE);
        DISABLE_ASBESTOS_VALUE = b.define("disableAsbestos", false);
        DISABLE_COAL_VALUE = b.define("disableCoaldust", false);
        DISABLE_HOT_VALUE = b.define("disableHot", false);
        DISABLE_COLD_VALUE = b.define("disableCold", false);
        DISABLE_EXPLOSIVE_VALUE = b.define("disableExplosive", false);
        DISABLE_HYDRO_VALUE = b.define("disableHydroactive", false);
        DISABLE_BLINDING_VALUE = b.define("disableBlinding", false);
        DISABLE_TOXIC_VALUE = b.define("disableToxic", false);
        ENABLE_528_VALUE = b.comment("Reacher divides dose by 49 instead of sqrt.").define("enable528", false);
        SPEC = b.build();
    }

    private HazardConfig() {
    }

    public static Values get() {
        return values;
    }

    /** Test seam. */
    public static void override(UnaryOperator<Values> change) {
        values = change.apply(values);
    }

    public static void onLoad(ModConfigEvent event) {
        if (event.getConfig().getSpec() != SPEC) return;
        values = new Values(HAZARD_RATE_VALUE.get(), ITEM_HAZARD_DROP_TICKRATE_VALUE.get(),
                DISABLE_ASBESTOS_VALUE.get(), DISABLE_COAL_VALUE.get(), DISABLE_HOT_VALUE.get(),
                DISABLE_COLD_VALUE.get(), DISABLE_EXPLOSIVE_VALUE.get(), DISABLE_HYDRO_VALUE.get(),
                DISABLE_BLINDING_VALUE.get(), DISABLE_TOXIC_VALUE.get(), ENABLE_528_VALUE.get());
    }
}
