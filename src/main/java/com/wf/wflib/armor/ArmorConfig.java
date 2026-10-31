package com.wf.wflib.armor;

import net.neoforged.fml.event.config.ModConfigEvent;
import net.neoforged.neoforge.common.ModConfigSpec;
import com.wf.wflib.armor.api.ArmorProfiles;

/** Armour tunables; immutable snapshot swapped on load. */
public final class ArmorConfig {

    public static final ModConfigSpec SPEC;

    private static final ModConfigSpec.BooleanValue MANAGE_VANILLA_VALUE;
    private static final ModConfigSpec.BooleanValue MOB_ARMOR_VALUE;
    private static final ModConfigSpec.IntValue EXPOSURE_INTERVAL_VALUE;
    private static final ModConfigSpec.IntValue PERSIST_INTERVAL_VALUE;
    private static final ModConfigSpec.DoubleValue FIRE_TIER_VALUE;
    private static final ModConfigSpec.DoubleValue LAVA_TIER_VALUE;
    private static final ModConfigSpec.DoubleValue ACUTE_FRACTION_VALUE;

    /**
     * @param manageVanillaArmor     derive a profile from vanilla defense/toughness for unregistered armour (e.g. GregTech, NTM); off restricts to authored pieces
     * @param mobArmor               whole-entity resolution also covers mobs; off = players only
     * @param exposureIntervalTicks  ticks between exposure charges; balance lever, hazard tier is points/interval, so doubling halves every environmental wear rate
     * @param persistIntervalTicks   ticks between flushing accrued exposure to stacks; a component write on a worn stack is an equipment-sync packet, integer accrual means coarsening loses nothing
     * @param fireTier               fire damage, condition points per exposure interval; same units as a suit's THERMAL tier
     * @param lavaTier               lava, as {@code fireTier}
     * @param acuteFraction          flat share of max condition lost on first contact with a hazard; percentage so cheap and powered armour pay the same proportion
     */
    public record Values(boolean manageVanillaArmor, boolean mobArmor, int exposureIntervalTicks,
                         int persistIntervalTicks, double fireTier, double lavaTier, double acuteFraction) {
    }

    private static volatile Values values = new Values(true, true, 5, 100, 60.0D, 400.0D, 0.04D);

    static {
        ModConfigSpec.Builder builder = new ModConfigSpec.Builder();
        builder.comment("Warfactory armour: one DT/DR resolver for every hit, per protection type.",
                "On/off: wflib-systems.toml, key 'armor'.").push("armor");
        MANAGE_VANILLA_VALUE = builder
                .comment("Give armour with no authored profile one derived from its own vanilla defense",
                        "and toughness, and strip those attributes so it is not counted twice.",
                        "Off restricts this system to authored pieces.")
                .define("manageVanillaArmor", true);
        MOB_ARMOR_VALUE = builder
                .comment("Resolve armour for mobs as well as players.")
                .define("mobArmor", true);
        EXPOSURE_INTERVAL_VALUE = builder
                .comment("Ticks between charges for standing in fire, gas or a field. Five ticks is a",
                        "quarter of a second, about human reaction time.",
                        "A balance lever as well as a performance one: a hazard's tier is points per",
                        "interval, so doubling this halves every environmental wear rate in the game.")
                .defineInRange("exposureIntervalTicks", 5, 1, 1200);
        PERSIST_INTERVAL_VALUE = builder
                .comment("Ticks between writing accrued exposure onto the armour stacks.")
                .defineInRange("persistIntervalTicks", 100, 1, 12000);
        FIRE_TIER_VALUE = builder
                .comment("What being on fire deals, in condition points per exposure interval.")
                .defineInRange("fireTier", 60.0D, 0.0D, 1.0E7D);
        LAVA_TIER_VALUE = builder
                .comment("What standing in lava deals, in condition points per exposure interval.")
                .defineInRange("lavaTier", 400.0D, 0.0D, 1.0E7D);
        ACUTE_FRACTION_VALUE = builder
                .comment("Share of maximum condition that first contact with a hazard costs outright.")
                .defineInRange("acuteFraction", 0.04D, 0.0D, 1.0D);
        builder.pop();
        SPEC = builder.build();
    }

    private ArmorConfig() {
    }

    public static Values get() {
        return values;
    }

    /** Test seam. */
    public static void override(Values next) {
        set(next);
    }

    public static void onLoad(ModConfigEvent event) {
        if (event.getConfig().getSpec() != SPEC) {
            return;
        }
        set(new Values(MANAGE_VANILLA_VALUE.get(), MOB_ARMOR_VALUE.get(), EXPOSURE_INTERVAL_VALUE.get(),
                PERSIST_INTERVAL_VALUE.get(), FIRE_TIER_VALUE.get(), LAVA_TIER_VALUE.get(),
                ACUTE_FRACTION_VALUE.get()));
    }

    private static void set(Values next) {
        if (next.manageVanillaArmor() != values.manageVanillaArmor()) {
            ArmorProfiles.clearDerived();
        }
        values = next;
    }
}
