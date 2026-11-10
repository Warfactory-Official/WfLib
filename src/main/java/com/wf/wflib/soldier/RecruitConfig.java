package com.wf.wflib.soldier;

import com.wf.wflib.soldier.api.Cost;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.common.ModConfigSpec;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/** {@code wflib-recruiting.toml} (per world): which ways of getting soldiers are on, what they cost, and the wage soldiers draw. */
public final class RecruitConfig {
    static final ModConfigSpec SPEC;
    static final List<String> DEFAULT_SOURCES = List.of("rescue", "pda", "barracks");
    private static final ModConfigSpec.ConfigValue<List<? extends String>> SOURCES;
    private static final ModConfigSpec.IntValue CALLIN_DELAY, CALLIN_COUNT, CALLIN_CURRENCY, GARRISON_COUNT, GARRISON_CURRENCY, WAGE_COUNT, WAGE_CURRENCY;
    private static final ModConfigSpec.ConfigValue<String> CALLIN_ITEM, GARRISON_ITEM, WAGE_ITEM;
    private static final ModConfigSpec.BooleanValue WAGES, DEFECTION;
    private static final ModConfigSpec.DoubleValue DEFECT_CHANCE;

    static {
        var b = new ModConfigSpec.Builder();
        SOURCES = b.comment("Ways of getting soldiers that are switched on, any mix. WFLib's own:",
                        "  rescue    sneak-use an unowned friendly soldier with an empty hand",
                        "  pda       call reinforcements from the PDA; they walk to you after a delay",
                        "  barracks  use a barracks (blocks and multiblocks other mods or /soldiers barracks add make into one)",
                        "  garrison  sneak-use a territory garrison's soldier and pay its price (it is replaced by the territory)",
                        "Other mods' sources are listed here by their own ids.")
                .defineListAllowEmpty("sources", DEFAULT_SOURCES, () -> "rescue", o -> o instanceof String);
        b.push("callin");
        CALLIN_DELAY = b.comment("Seconds until a PDA hire arrives.").defineInRange("delaySeconds", 45, 0, 3600);
        CALLIN_ITEM = b.comment("Item paid per hire (empty: none).").define("costItem", "");
        CALLIN_COUNT = b.defineInRange("costItemCount", 1, 1, 1000);
        CALLIN_CURRENCY = b.comment("Currency (from a mod's wallet) paid per hire; 0 for none.").defineInRange("costCurrency", 0, 0, 1_000_000);
        b.pop().push("garrison");
        GARRISON_ITEM = b.comment("Item paid to hire a garrison soldier (empty: none).").define("costItem", "");
        GARRISON_COUNT = b.defineInRange("costItemCount", 1, 1, 1000);
        GARRISON_CURRENCY = b.defineInRange("costCurrency", 0, 0, 1_000_000);
        b.pop().push("wages");
        WAGES = b.comment("Soldiers draw a wage once per in-game day, taken from whoever hired them. Off by default.").define("enabled", false);
        WAGE_ITEM = b.comment("Item paid per soldier per day (empty: none).").define("item", "minecraft:emerald");
        WAGE_COUNT = b.defineInRange("itemCount", 1, 1, 1000);
        WAGE_CURRENCY = b.comment("Currency paid per soldier per day; 0 for none.").defineInRange("currency", 0, 0, 1_000_000);
        DEFECTION = b.comment("A soldier whose wage goes unpaid may turn hostile instead of just leaving.").define("defection", true);
        DEFECT_CHANCE = b.comment("Percent chance that an unpaid soldier turns hostile rather than leaves.").defineInRange("defectionChancePercent", 2.0, 0.0, 100.0);
        b.pop();
        SPEC = b.build();
    }

    private RecruitConfig() {}

    /** A config value, or its default while the config is not loaded (unit tests, very early startup). */
    private static <T> T get(Supplier<T> value, T fallback) {
        try {
            return value.get();
        } catch (IllegalStateException e) {
            return fallback;
        }
    }

    public static boolean enabled(String source) {
        return get(() -> new ArrayList<String>(SOURCES.get()), DEFAULT_SOURCES).contains(source);
    }

    public static int callinDelay() {
        return get(CALLIN_DELAY::get, 45);
    }

    public static Cost callinCost() {
        return cost(get(CALLIN_ITEM::get, ""), get(CALLIN_COUNT::get, 1), get(CALLIN_CURRENCY::get, 0));
    }

    public static Cost garrisonCost() {
        return cost(get(GARRISON_ITEM::get, ""), get(GARRISON_COUNT::get, 1), get(GARRISON_CURRENCY::get, 0));
    }

    public static boolean wages() {
        return get(WAGES::get, false);
    }

    public static Cost wage() {
        return cost(get(WAGE_ITEM::get, "minecraft:emerald"), get(WAGE_COUNT::get, 1), get(WAGE_CURRENCY::get, 0));
    }

    public static boolean defection() {
        return get(DEFECTION::get, true);
    }

    public static double defectionPercent() {
        return get(DEFECT_CHANCE::get, 2.0);
    }

    private static Cost cost(String item, int count, int currency) {
        List<Cost> parts = new ArrayList<>();
        ResourceLocation id = item.isBlank() ? null : ResourceLocation.tryParse(item);
        if (id != null && BuiltInRegistries.ITEM.containsKey(id)) parts.add(Cost.items(BuiltInRegistries.ITEM.get(id), count));
        parts.add(Cost.currency(currency));
        return Cost.all(parts);
    }
}
