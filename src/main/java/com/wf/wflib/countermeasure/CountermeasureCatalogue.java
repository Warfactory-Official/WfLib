package com.wf.wflib.countermeasure;

import com.wf.wflib.countermeasure.api.CountermeasureItem;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Supplier;
import com.wf.wflib.core.api.WFLib;

/** Catalogue countermeasure items; registered iff requested. */
public final class CountermeasureCatalogue {
    private static final Map<ResourceLocation, Supplier<Item>> DEFS = new LinkedHashMap<>();

    static {
        item("flare_cartridge", () -> new CountermeasureItem(CountermeasureItem.Kind.FLARE,
                new Item.Properties().stacksTo(64)));
        item("smoke_grenade", () -> new CountermeasureItem(CountermeasureItem.Kind.SMOKE,
                new Item.Properties().stacksTo(16)));
        item("aps_grenade", () -> new CountermeasureItem(CountermeasureItem.Kind.APS,
                new Item.Properties().stacksTo(16)));
        item("decoy_launcher", () -> new DecoyLauncherItem(new Item.Properties().stacksTo(1)));
    }

    private CountermeasureCatalogue() {
    }

    private static void item(String path, Supplier<Item> factory) {
        DEFS.put(ResourceLocation.fromNamespaceAndPath(WFLib.MODID, path), factory);
    }

    public static Collection<ResourceLocation> ids() {
        return Collections.unmodifiableSet(DEFS.keySet());
    }

    public static boolean defined(ResourceLocation id) {
        return DEFS.containsKey(id);
    }

    static Supplier<Item> factory(ResourceLocation id) {
        return DEFS.get(id);
    }
}
