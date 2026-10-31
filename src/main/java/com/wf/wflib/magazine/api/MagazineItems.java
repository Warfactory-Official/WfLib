package com.wf.wflib.magazine.api;

import com.wf.wflib.core.api.WFLibContent;
import com.wf.wflib.magazine.MagazineCatalogue;
import com.wf.wflib.ammo.api.Ammo;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.registries.DeferredHolder;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.function.Supplier;
import com.wf.wflib.core.api.WFLib;

/**
 * Magazine components + catalogue vehicle magazines and launch pods; item registered iff requested
 * ({@link WFLibContent.Kind#MAGAZINE}), else its holder unbound. Registered by {@code MagazineSystem}.
 */
public final class MagazineItems {
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<MagContents>> CONTENTS =
            DeferredHolder.create(Registries.DATA_COMPONENT_TYPE, rl("mag_contents"));
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<PodTubes>> POD_TUBES =
            DeferredHolder.create(Registries.DATA_COMPONENT_TYPE, rl("pod_tubes"));

    /** {@code calibre} != null => can of it; else pod of {@code missiles}. */
    public record Def(ResourceLocation id, @Nullable ResourceLocation calibre, List<ResourceLocation> missiles,
                      Supplier<Item> factory) {
    }

    private static volatile List<ResourceLocation> live = List.of();

    public static final DeferredHolder<Item, MagazineItem> CAN_7_62X51 = item("ammo_can_7_62x51");
    public static final DeferredHolder<Item, MagazineItem> CAN_12_7X99 = item("ammo_can_12_7x99");
    public static final DeferredHolder<Item, MagazineItem> CAN_12_7X108 = item("ammo_can_12_7x108");
    public static final DeferredHolder<Item, MagazineItem> CAN_40X53 = item("ammo_can_40x53");
    public static final DeferredHolder<Item, MagazineItem> DRUM_20X102 = item("drum_20x102");
    public static final DeferredHolder<Item, PodItem> POD_GMLRS = item("pod_gmlrs");
    public static final DeferredHolder<Item, PodItem> POD_ATACMS = item("pod_atacms");

    private MagazineItems() {
    }

    private static ResourceLocation rl(String path) {
        return ResourceLocation.fromNamespaceAndPath(WFLib.MODID, path);
    }

    private static <T extends Item> DeferredHolder<Item, T> item(String path) {
        return DeferredHolder.create(Registries.ITEM, rl(path));
    }

    public static boolean defined(ResourceLocation id) {
        return MagazineCatalogue.defined(id);
    }

    public static Def def(ResourceLocation id) {
        return MagazineCatalogue.def(id);
    }

    public static Collection<ResourceLocation> catalogue() {
        return MagazineCatalogue.ids();
    }

    /** Registered ids, catalogue order. */
    public static List<ResourceLocation> live() {
        return live;
    }

    /** {@link WFLibContent#freeze} only. */
    public static void freeze(Set<ResourceLocation> requested) {
        live = MagazineCatalogue.ids().stream().filter(requested::contains).toList();
    }

    /** Each empty, magazines full of the calibre's default variant, pods full of their first tagged round. */
    public static List<ItemStack> creativeStacks() {
        List<ItemStack> out = new ArrayList<>();
        for (ResourceLocation id : live) {
            Item item = BuiltInRegistries.ITEM.get(id);
            ItemStack empty = new ItemStack(item);
            out.add(empty);
            if (item instanceof MagazineItem mag) {
                out.add(MagFill.full(empty, Ammo.defaultVariant(mag.calibre()).id()));
            } else if (item instanceof PodItem pod) {
                BuiltInRegistries.ITEM.getTag(pod.tag()).flatMap(t -> t.stream().findFirst()).ifPresent(round -> {
                    ItemStack full = empty.copy();
                    full.set(POD_TUBES.get(), new PodTubes(Collections.nCopies(pod.tubes().size(),
                            new ItemStack(round))));
                    out.add(full);
                });
            }
        }
        return out;
    }
}
