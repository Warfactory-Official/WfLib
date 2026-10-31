package com.wf.wflib.artifact.api;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.Item;
import net.neoforged.neoforge.registries.DeferredHolder;

import java.util.LinkedHashMap;
import java.util.Map;
import com.wf.wflib.core.api.WFLib;

/** Holders registered by {@code ArtifactSystem}: artefacts, detectors, bolt, and the entities that carry them. */
public final class ArtifactContent {
    /** STALKER's artefacts, worn in Curios {@code artifact} slots. See {@link Artifacts}. */
    public static final Map<String, DeferredHolder<Item, Item>> ARTIFACTS = new LinkedHashMap<>();

    static {
        for (Artifact a : Artifact.ALL) {
            ARTIFACTS.put(a.id(), item(a.id()));
        }
    }

    public static final DeferredHolder<Item, Item> BATTERY = ARTIFACTS.get("battery");
    public static final DeferredHolder<Item, Item> SOUL = ARTIFACTS.get("soul");

    public static final DeferredHolder<Item, Item> DETECTOR_ECHO = item("detector_echo");
    public static final DeferredHolder<Item, Item> DETECTOR_BEAR = item("detector_bear");
    public static final DeferredHolder<Item, Item> DETECTOR_VELES = item("detector_veles");
    public static final DeferredHolder<Item, Item> BOLT_ITEM = item("bolt");

    public static final DeferredHolder<EntityType<?>, EntityType<ZoneArtifact>> ZONE_ARTIFACT =
            DeferredHolder.create(Registries.ENTITY_TYPE, id("zone_artifact"));
    public static final DeferredHolder<EntityType<?>, EntityType<BoltEntity>> BOLT =
            DeferredHolder.create(Registries.ENTITY_TYPE, id("bolt"));

    private ArtifactContent() {
    }

    private static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(WFLib.MODID, path);
    }

    private static DeferredHolder<Item, Item> item(String path) {
        return DeferredHolder.create(Registries.ITEM, id(path));
    }
}
