package com.wf.wflib.nvg.api;

import com.wf.wflib.core.api.WFLibContent;
import com.wf.wflib.nvg.NvgCatalogue;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.SimpleCraftingRecipeSerializer;
import net.neoforged.neoforge.registries.DeferredHolder;

import java.util.Collection;
import java.util.List;
import java.util.Set;
import com.wf.wflib.core.api.WFLib;

/** Goggle catalogue (registered iff requested via {@link WFLibContent}) + dismount serializer; registered by {@code NvgSystem}. */
public final class NvgContent {
    public static final DeferredHolder<Item, NvgItem> NVG_GEN1 = item("nvg_gen1");
    public static final DeferredHolder<Item, NvgItem> NVG_PVS7 = item("nvg_pvs7");
    public static final DeferredHolder<Item, NvgItem> NVG_PVS14 = item("nvg_pvs14");
    public static final DeferredHolder<Item, NvgItem> NVG_PVS31 = item("nvg_pvs31");
    public static final DeferredHolder<Item, NvgItem> NVG_GPNVG18 = item("nvg_gpnvg18");

    public static final DeferredHolder<RecipeSerializer<?>, SimpleCraftingRecipeSerializer<NvgDismountRecipe>> DISMOUNT =
            DeferredHolder.create(Registries.RECIPE_SERIALIZER,
                    ResourceLocation.fromNamespaceAndPath(WFLib.MODID, "nvg_dismount"));

    private static volatile List<ResourceLocation> live = List.of();

    private NvgContent() {
    }

    public static boolean defined(ResourceLocation id) {
        return NvgCatalogue.defined(id);
    }

    public static Collection<ResourceLocation> catalogue() {
        return NvgCatalogue.ids();
    }

    /** Registered ids, catalogue order. */
    public static List<ResourceLocation> live() {
        return live;
    }

    /** {@link WFLibContent#freeze} only. */
    public static void freeze(Set<ResourceLocation> requested) {
        live = NvgCatalogue.ids().stream().filter(requested::contains).toList();
    }

    private static DeferredHolder<Item, NvgItem> item(String path) {
        return DeferredHolder.create(Registries.ITEM, ResourceLocation.fromNamespaceAndPath(WFLib.MODID, path));
    }
}
