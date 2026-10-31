package com.wf.wflib.armor.api.exo;

import net.neoforged.fml.event.config.ModConfigEvent;
import net.neoforged.neoforge.common.ModConfigSpec;

/** Exoskeleton tunables; immutable snapshot swapped on load. */
public final class ExoConfig {
    public enum Unit { EU, FE }

    public static final ModConfigSpec SPEC;

    private static final ModConfigSpec.BooleanValue ENERGY;
    private static final ModConfigSpec.EnumValue<Unit> UNIT;
    private static final ModConfigSpec.DoubleValue EU_TO_FE;
    private static final ModConfigSpec.DoubleValue RECOIL_FLOOR;
    private static final ModConfigSpec.DoubleValue SWAY_FLOOR;
    private static final ModConfigSpec.DoubleValue ABSORBED_SHARE;
    private static final ModConfigSpec.DoubleValue DANGER;
    private static final ModConfigSpec.IntValue LIFE_COOLDOWN;
    private static final ModConfigSpec.DoubleValue LIFE_DRAW;
    private static final ModConfigSpec.IntValue REPAIR_HP;

    /**
     * @param energyEnabled   off => every exo piece is always powered, unpowered stats ignored, no draw
     * @param unit            unit of {@code drawPerSecond} and of {@link #lifeSupportDraw}
     * @param euToFe          GTCEu ratio, FE per EU
     * @param recoilFloor     lowest recoil multiplier compensation can reach; also swayFloor
     * @param partAbsorbedShare share of armour-absorbed damage that still reaches parts
     * @param lifeSupportDanger health fraction at or below which life support triggers
     * @param repairHp        part hp restored per repair item
     */
    public record Values(boolean energyEnabled, Unit unit, double euToFe, double recoilFloor, double swayFloor,
                         double partAbsorbedShare, double lifeSupportDanger, int lifeSupportCooldownTicks,
                         double lifeSupportDraw, int repairHp) {
        public double toFe(double amount) {
            return unit == Unit.EU ? amount * euToFe : amount;
        }
    }

    private static volatile Values values = new Values(true, Unit.EU, 4, 0.15, 0.15, 0.25, 0.35, 100, 50, 25);

    static {
        ModConfigSpec.Builder b = new ModConfigSpec.Builder();
        b.comment("Exoskeleton armour features (pack defs carry an 'exo' block).").push("exo");
        ENERGY = b.comment("Armour consumes energy; off = always powered, unpowered stats ignored.")
                .define("energyEnabled", true);
        UNIT = b.comment("Unit of every authored draw. GTCEu batteries read natively in EU.")
                .defineEnum("energyUnit", Unit.EU);
        EU_TO_FE = b.comment("FE per EU, for FE-only batteries.").defineInRange("euToFe", 4.0, 0.001, 1000.0);
        RECOIL_FLOOR = b.comment("Lowest recoil multiplier compensation can reach (never 0).")
                .defineInRange("recoilFloor", 0.15, 0.01, 1.0);
        SWAY_FLOOR = b.comment("Lowest sway multiplier compensation can reach (never 0).")
                .defineInRange("swayFloor", 0.15, 0.01, 1.0);
        ABSORBED_SHARE = b.comment("Share of armour-absorbed damage that still wears exo parts.")
                .defineInRange("partAbsorbedShare", 0.25, 0.0, 1.0);
        DANGER = b.comment("Life support triggers at or below this health fraction.")
                .defineInRange("lifeSupportDanger", 0.35, 0.01, 1.0);
        LIFE_COOLDOWN = b.comment("Ticks between life-support uses.").defineInRange("lifeSupportCooldownTicks", 100, 1, 12000);
        LIFE_DRAW = b.comment("Energy per life-support use, in the configured unit.")
                .defineInRange("lifeSupportDraw", 50.0, 0.0, 1.0E9);
        REPAIR_HP = b.comment("Part hit points restored per repair item.").defineInRange("repairHp", 25, 1, 100000);
        b.pop();
        SPEC = b.build();
    }

    private ExoConfig() {
    }

    public static Values get() {
        return values;
    }

    /** Test seam. */
    public static void override(Values next) {
        values = next;
    }

    public static void onLoad(ModConfigEvent event) {
        if (event.getConfig().getSpec() == SPEC) {
            values = new Values(ENERGY.get(), UNIT.get(), EU_TO_FE.get(), RECOIL_FLOOR.get(), SWAY_FLOOR.get(),
                    ABSORBED_SHARE.get(), DANGER.get(), LIFE_COOLDOWN.get(), LIFE_DRAW.get(), REPAIR_HP.get());
        }
    }
}
