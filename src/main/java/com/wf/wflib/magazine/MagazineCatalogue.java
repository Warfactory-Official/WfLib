package com.wf.wflib.magazine;

import com.wf.wflib.magazine.api.MagazineItem;
import com.wf.wflib.magazine.api.MagazineItems.Def;
import com.wf.wflib.magazine.api.PodItem;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import org.joml.Vector2f;
import org.joml.Vector2fc;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;
import com.wf.wflib.core.api.WFLib;

/** Catalogue vehicle magazines and launch pods; item registered iff requested. */
public final class MagazineCatalogue {
    private static final Map<ResourceLocation, Def> DEFS = new LinkedHashMap<>();

    private MagazineCatalogue() {
    }

    static void define() {
        magazine("ammo_can_7_62x51", "7.62x51", 200);
        magazine("ammo_can_12_7x99", "12.7x99", 100);
        magazine("ammo_can_12_7x108", "12.7x108", 100);
        magazine("ammo_can_40x53", "40x53", 48);
        // Phalanx Block 1B / M61 linkless drum.
        magazine("drum_20x102", "20x102", 1550);
        // M142 stations, 2 x 3 cells.
        pod("pod_gmlrs", List.of("gmlrs", "gmlrs_aw"),
                () -> new PodItem(List.of(
                        at(0.3169f, -0.1590f), at(0, -0.1590f), at(-0.3169f, -0.1590f),
                        at(0.3169f, 0.1590f), at(0, 0.1590f), at(-0.3169f, 0.1590f)),
                        4.28f, rl("missile/pod"), new Item.Properties()));
        pod("pod_atacms", List.of("atacms"),
                () -> new PodItem(List.of(at(0, 0)), 4.0f, rl("missile/pod_large"), new Item.Properties()));
    }

    private static Vector2fc at(float x, float y) {
        return new Vector2f(x, y);
    }

    private static ResourceLocation rl(String path) {
        return ResourceLocation.fromNamespaceAndPath(WFLib.MODID, path);
    }

    private static void magazine(String path, String calibre, int capacity) {
        DEFS.put(rl(path), new Def(rl(path), rl(calibre), List.of(),
                () -> new MagazineItem(rl(calibre), capacity, new Item.Properties())));
    }

    private static void pod(String path, List<String> missiles, Supplier<Item> factory) {
        DEFS.put(rl(path), new Def(rl(path), null, missiles.stream().map(MagazineCatalogue::rl).toList(), factory));
    }

    public static boolean defined(ResourceLocation id) {
        return DEFS.containsKey(id);
    }

    public static Def def(ResourceLocation id) {
        return DEFS.get(id);
    }

    public static Collection<ResourceLocation> ids() {
        return Collections.unmodifiableSet(DEFS.keySet());
    }
}
