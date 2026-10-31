package com.wf.wflib.colony.api;

import com.wf.wflib.colony.glyphid.EntityGlyphidBomb;
import com.wf.wflib.colony.glyphid.caste.EntityGlyphidBehemoth;
import com.wf.wflib.colony.glyphid.caste.EntityGlyphidBlaster;
import com.wf.wflib.colony.glyphid.caste.EntityGlyphidBombardier;
import com.wf.wflib.colony.glyphid.caste.EntityGlyphidBrawler;
import com.wf.wflib.colony.glyphid.caste.EntityGlyphidBrenda;
import com.wf.wflib.colony.glyphid.caste.EntityGlyphidDigger;
import com.wf.wflib.colony.glyphid.caste.EntityGlyphidNuclear;
import com.wf.wflib.colony.glyphid.caste.EntityGlyphidScout;
import com.wf.wflib.colony.debug.EntityDebugDummy;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.neoforged.neoforge.registries.DeferredHolder;
import com.wf.wflib.colony.GlyphidSpawnerBlockEntity;
import com.wf.wflib.core.api.WFLib;

/** Holders registered by {@code ColonySystem}: the glyphid castes, their ordnance, nest blocks. */
public final class ColonyContent {
    public static final DeferredHolder<EntityType<?>, EntityType<EntityGlyphid>> GLYPHID = entity("glyphid");
    public static final DeferredHolder<EntityType<?>, EntityType<EntityGlyphidScout>> GLYPHID_SCOUT = entity("glyphid_scout");
    public static final DeferredHolder<EntityType<?>, EntityType<EntityGlyphidBombardier>> GLYPHID_BOMBARDIER = entity("glyphid_bombardier");
    public static final DeferredHolder<EntityType<?>, EntityType<EntityGlyphidBlaster>> GLYPHID_BLASTER = entity("glyphid_blaster");
    public static final DeferredHolder<EntityType<?>, EntityType<EntityGlyphidBrawler>> GLYPHID_BRAWLER = entity("glyphid_brawler");
    public static final DeferredHolder<EntityType<?>, EntityType<EntityGlyphidDigger>> GLYPHID_DIGGER = entity("glyphid_digger");
    public static final DeferredHolder<EntityType<?>, EntityType<EntityGlyphidBehemoth>> GLYPHID_BEHEMOTH = entity("glyphid_behemoth");
    public static final DeferredHolder<EntityType<?>, EntityType<EntityGlyphidNuclear>> GLYPHID_NUCLEAR = entity("glyphid_nuclear");
    public static final DeferredHolder<EntityType<?>, EntityType<EntityGlyphidBrenda>> GLYPHID_BRENDA = entity("glyphid_brenda");
    public static final DeferredHolder<EntityType<?>, EntityType<EntityGlyphidBomb>> GLYPHID_BOMB = entity("glyphid_bomb");
    public static final DeferredHolder<EntityType<?>, EntityType<GlyphidWaypoint>> GLYPHID_WAYPOINT = entity("glyphid_waypoint");
    public static final DeferredHolder<EntityType<?>, EntityType<EntityDebugDummy>> DEBUG_DUMMY = entity("debug_dummy");

    public static final DeferredHolder<Block, Block> GLYPHID_NEST = block("glyphid_nest");
    public static final DeferredHolder<Item, Item> GLYPHID_NEST_ITEM = item("glyphid_nest");
    public static final DeferredHolder<Block, Block> GLYPHID_NEST_REINFORCED = block("glyphid_nest_reinforced");
    public static final DeferredHolder<Item, Item> GLYPHID_NEST_REINFORCED_ITEM = item("glyphid_nest_reinforced");
    public static final DeferredHolder<Block, Block> GLYPHID_SPAWNER = block("glyphid_spawner");
    public static final DeferredHolder<Item, Item> GLYPHID_SPAWNER_ITEM = item("glyphid_spawner");
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<GlyphidSpawnerBlockEntity>> GLYPHID_SPAWNER_BE =
            DeferredHolder.create(Registries.BLOCK_ENTITY_TYPE, id("glyphid_spawner"));

    private ColonyContent() {
    }

    private static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(WFLib.MODID, path);
    }

    private static <T extends net.minecraft.world.entity.Entity> DeferredHolder<EntityType<?>, EntityType<T>> entity(String path) {
        return DeferredHolder.create(Registries.ENTITY_TYPE, id(path));
    }

    private static DeferredHolder<Block, Block> block(String path) {
        return DeferredHolder.create(Registries.BLOCK, id(path));
    }

    private static DeferredHolder<Item, Item> item(String path) {
        return DeferredHolder.create(Registries.ITEM, id(path));
    }
}
