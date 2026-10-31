package com.wf.wflib.industry;

import com.mojang.logging.LogUtils;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap;
import net.minecraft.core.registries.BuiltInRegistries;
import com.wf.wflib.core.api.WFRegistry;
import org.slf4j.Logger;

import java.util.List;

/** What a block is worth as a provocation. */
public final class IndustryValues {

    private static final Logger LOGGER = LogUtils.getLogger();

    /** Default whitelist. */
    public static final List<String> DEFAULTS = List.of(
            // Dirty: combustion and fuel burning.
            "gtceu:combustion_generator=30",
            "gtceu:advanced_combustion_generator=45",
            "gtceu:gas_turbine=25",
            "gtceu:advanced_gas_turbine=40",
            "gtceu:steam_turbine=15",
            "gtceu:large_combustion_engine=120",
            "gtceu:extreme_combustion_engine=200",
            "gtceu:large_gas_turbine=100",
            "gtceu:large_steam_turbine=60",
            "gtceu:coal_boiler=20",
            "gtceu:lava_boiler=20",
            // Dirty: chemistry and radioactives.
            "gtceu:large_chemical_reactor=60",
            "gtceu:distillation_tower=50",
            "gtceu:cracker=50",
            "gtceu:oil_cracking_unit=50",
            "gtceu:nuclear_reactor=250",
            "gtceu:fusion_reactor=400",
            // Clean production: present, but cheap.
            "gtceu:electric_blast_furnace=10",
            "gtceu:large_electric_compressor=6",
            "gtceu:processing_array=6",
            "gtceu:steam_oven=4",
            "minecraft:furnace=1",
            "minecraft:blast_furnace=2");

    /** Code-declared rows; config rows override. */
    public static final WFRegistry<Integer> CODE_ROWS = WFRegistry.create("industry", "industry values");

    private static volatile Object2IntOpenHashMap<ResourceLocation> table = new Object2IntOpenHashMap<>();
    private static volatile List<? extends String> configured = DEFAULTS;

    private IndustryValues() {
    }

    /** Rebuild the table from the code rows and the given config rows. Called on config load. */
    static void rebake(List<? extends String> entries) {
        configured = entries;
        rebuild();
    }

    /** Rebuild after the code rows are final. */
    static void rebuild() {
        Object2IntOpenHashMap<ResourceLocation> next = new Object2IntOpenHashMap<>();
        next.defaultReturnValue(0);
        CODE_ROWS.entries().forEach(e -> next.put(e.getKey(), e.getValue().intValue()));
        for (String entry : configured) {
            parse(entry, next);
        }
        table = next;
        LOGGER.debug("[wflib] industry whitelist baked with {} entries", next.size());
    }

    private static void parse(String entry, Object2IntOpenHashMap<ResourceLocation> into) {
        String text = entry.trim();
        if (text.isEmpty()) {
            return;
        }
        int split = text.indexOf('=');
        String rawId = split < 0 ? text : text.substring(0, split).trim();
        int value = 1;

        if (split >= 0) {
            try {
                value = Integer.parseInt(text.substring(split + 1).trim());
            } catch (NumberFormatException e) {
                LOGGER.warn("[wflib] industry whitelist entry '{}' has an unreadable value, using 1", entry);
            }
        }

        ResourceLocation id = ResourceLocation.tryParse(rawId);
        if (id == null) {
            LOGGER.warn("[wflib] industry whitelist entry '{}' is not a valid block id, skipped", entry);
            return;
        }
        if (value > 0) {
            into.put(id, value);
        }
    }

    /**
     * @return what this block contributes, or 0 if it provokes nothing.
     */
    public static int valueOf(BlockState state) {
        return valueOf(state.getBlock());
    }

    public static int valueOf(Block block) {
        ResourceLocation id = BuiltInRegistries.BLOCK.getKey(block);
        return id == null ? 0 : table.getInt(id);
    }

    public static int size() {
        return table.size();
    }
}
