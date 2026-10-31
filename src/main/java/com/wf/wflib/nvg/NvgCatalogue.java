package com.wf.wflib.nvg;

import com.wf.wflib.nvg.api.NvgItem;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Supplier;
import com.wf.wflib.core.api.WFLib;

/** Catalogue goggles; registered iff requested. Optics {@code wflib:nvg/<path>.json}, model {@code wflib:models/nvg/<path>.glb}. */
public final class NvgCatalogue {
    private static final Map<ResourceLocation, Supplier<Item>> DEFS = new LinkedHashMap<>();

    static {
        for (String path : new String[] {"nvg_gen1", "nvg_pvs7", "nvg_pvs14", "nvg_pvs31", "nvg_gpnvg18"}) {
            DEFS.put(ResourceLocation.fromNamespaceAndPath(WFLib.MODID, path),
                    () -> new NvgItem(new Item.Properties().stacksTo(1)));
        }
    }

    private NvgCatalogue() {
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
