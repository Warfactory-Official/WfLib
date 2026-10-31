package com.wf.wflib.anomaly.api;

import com.wf.wflib.anomaly.AnomalyBlockEntity;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.neoforged.neoforge.registries.DeferredHolder;

import java.util.List;
import com.wf.wflib.core.api.WFLib;

/** Holders registered by {@code AnomalySystem}: STALKER's anomalies, one block and item each. */
public final class AnomalyContent {
    /** Vortex: the first, hence the BE id. */
    public static final DeferredHolder<Block, Block> GRAVITY_ANOMALY = block("gravity_anomaly");
    public static final DeferredHolder<Block, Block> WHIRLIGIG = block("whirligig");
    public static final DeferredHolder<Block, Block> SPRINGBOARD = block("springboard");
    public static final DeferredHolder<Block, Block> ELECTRO = block("electro");
    public static final DeferredHolder<Block, Block> BURNER = block("burner");
    public static final DeferredHolder<Block, Block> ACID = block("acid");
    public static final DeferredHolder<Block, Block> STEAM = block("steam");
    public static final DeferredHolder<Block, Block> FLASH = block("flash");
    public static final DeferredHolder<Block, Block> GHOST = block("ghost");
    public static final DeferredHolder<Block, Block> NET = block("net");
    public static final DeferredHolder<Block, Block> BALL_LIGHTNING = block("ball_lightning");
    public static final DeferredHolder<Block, Block> LIQUID_GOLD = block("liquid_gold");
    public static final DeferredHolder<Block, Block> GREEN_DRAGON = block("green_dragon");
    public static final DeferredHolder<Block, Block> REBOUNDER = block("rebounder");
    public static final DeferredHolder<Block, Block> MEPHISTOPHELES = block("mephistopheles");
    public static final DeferredHolder<Block, Block> BURNT_FUZZ = block("burnt_fuzz");
    public static final DeferredHolder<Block, Block> GRABBER = block("grabber");
    public static final DeferredHolder<Block, Block> COMET = block("comet");
    public static final DeferredHolder<Block, Block> RADIATION_FIELD = block("radiation_field");
    public static final DeferredHolder<Block, Block> THERMAL_FIELD = block("thermal_field");
    public static final DeferredHolder<Block, Block> ACID_FIELD = block("acid_field");
    public static final DeferredHolder<Block, Block> NO_GRAVITY = block("no_gravity");
    public static final DeferredHolder<Block, Block> STEAM_VENT = block("steam_vent");
    public static final DeferredHolder<Block, Block> PSY_FIELD = block("psy_field");
    public static final DeferredHolder<Block, Block> RADAR = block("radar");
    public static final DeferredHolder<Block, Block> DARKNESS = block("darkness");

    /** All anomaly blocks, creative-tab order. */
    public static final List<DeferredHolder<Block, Block>> ANOMALIES = List.of(GRAVITY_ANOMALY, WHIRLIGIG, SPRINGBOARD, ELECTRO, BURNER, ACID, STEAM, FLASH, GHOST, NET, BALL_LIGHTNING, LIQUID_GOLD, GREEN_DRAGON, REBOUNDER, MEPHISTOPHELES, BURNT_FUZZ, GRABBER, COMET, RADIATION_FIELD, THERMAL_FIELD, ACID_FIELD, NO_GRAVITY, STEAM_VENT, PSY_FIELD, RADAR, DARKNESS);
    /** Their items, same order. */
    public static final List<DeferredHolder<Item, Item>> ANOMALY_ITEMS = ANOMALIES.stream()
            .map(h -> DeferredHolder.<Item, Item>create(Registries.ITEM, h.getId())).toList();

    /** Every anomaly block; the id is the first one's (saved worlds name it). */
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<AnomalyBlockEntity>> ANOMALY_BE =
            DeferredHolder.create(Registries.BLOCK_ENTITY_TYPE, id("gravity_anomaly"));

    private AnomalyContent() {
    }

    private static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(WFLib.MODID, path);
    }

    private static DeferredHolder<Block, Block> block(String path) {
        return DeferredHolder.create(Registries.BLOCK, id(path));
    }
}
