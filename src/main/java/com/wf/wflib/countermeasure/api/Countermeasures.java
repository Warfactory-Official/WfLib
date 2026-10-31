package com.wf.wflib.countermeasure.api;

import com.wf.wflib.core.api.WFLibContent;
import com.wf.wflib.countermeasure.CountermeasureCatalogue;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.Item;
import net.neoforged.neoforge.registries.DeferredHolder;

import java.util.Collection;
import java.util.List;
import java.util.Set;
import com.wf.wflib.core.api.WFLib;

/** Countermeasure entity types + catalogue items (registered iff requested); registered by {@code CountermeasureSystem}. */
public final class Countermeasures {

    public static final DeferredHolder<EntityType<?>, EntityType<FlareEntity>> FLARE = entity("flare");
    public static final DeferredHolder<EntityType<?>, EntityType<SmokeGrenadeEntity>> SMOKE_GRENADE =
            entity("smoke_grenade");
    public static final DeferredHolder<EntityType<?>, EntityType<ApsGrenadeEntity>> APS_GRENADE =
            entity("aps_grenade");

    public static final DeferredHolder<Item, CountermeasureItem> FLARE_CARTRIDGE = item("flare_cartridge");
    public static final DeferredHolder<Item, CountermeasureItem> SMOKE_GRENADE_ITEM = item("smoke_grenade");
    public static final DeferredHolder<Item, CountermeasureItem> APS_GRENADE_ITEM = item("aps_grenade");
    public static final DeferredHolder<Item, Item> DECOY_LAUNCHER = item("decoy_launcher");

    private static volatile List<ResourceLocation> live = List.of();

    private Countermeasures() {
    }

    public static boolean defined(ResourceLocation id) {
        return CountermeasureCatalogue.defined(id);
    }

    public static Collection<ResourceLocation> catalogue() {
        return CountermeasureCatalogue.ids();
    }

    /** Registered ids, catalogue order. */
    public static List<ResourceLocation> live() {
        return live;
    }

    /** {@link WFLibContent#freeze} only. */
    public static void freeze(Set<ResourceLocation> requested) {
        live = CountermeasureCatalogue.ids().stream().filter(requested::contains).toList();
    }

    private static <T extends net.minecraft.world.entity.Entity> DeferredHolder<EntityType<?>, EntityType<T>> entity(
            String path) {
        return DeferredHolder.create(Registries.ENTITY_TYPE, ResourceLocation.fromNamespaceAndPath(WFLib.MODID, path));
    }

    private static <T extends Item> DeferredHolder<Item, T> item(String path) {
        return DeferredHolder.create(Registries.ITEM, ResourceLocation.fromNamespaceAndPath(WFLib.MODID, path));
    }
}
