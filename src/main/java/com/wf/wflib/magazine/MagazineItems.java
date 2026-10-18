package com.wf.wflib.magazine;

import com.wf.wflib.WFLib;
import com.wf.wflib.ammo.Ammo;
import com.wf.wflib.content.WFLibContent;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.RegisterEvent;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;

import static com.wf.wflib.magazine.PodItem.at;

/**
 * Magazine components + catalogue vehicle magazines and launch pods; item registered iff requested
 * ({@link WFLibContent.Kind#MAGAZINE}), else its holder unbound.
 */
public final class MagazineItems {
    private static final DeferredRegister<DataComponentType<?>> COMPONENTS =
            DeferredRegister.create(Registries.DATA_COMPONENT_TYPE, WFLib.MODID);

    public static final DeferredHolder<DataComponentType<?>, DataComponentType<MagContents>> CONTENTS =
            COMPONENTS.register("mag_contents", () -> DataComponentType.<MagContents>builder()
                    .persistent(MagContents.CODEC).networkSynchronized(MagContents.STREAM_CODEC).cacheEncoding()
                    .build());
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<PodTubes>> POD_TUBES =
            COMPONENTS.register("pod_tubes", () -> DataComponentType.<PodTubes>builder()
                    .persistent(PodTubes.CODEC).networkSynchronized(PodTubes.STREAM_CODEC).cacheEncoding().build());

    /** {@code calibre} != null => can of it; else pod of {@code missiles}. */
    public record Def(ResourceLocation id, @Nullable ResourceLocation calibre, List<ResourceLocation> missiles,
                      Supplier<Item> factory) {
    }

    private static final Map<ResourceLocation, Def> CATALOGUE = new LinkedHashMap<>();
    private static volatile List<ResourceLocation> live = List.of();

    public static final DeferredHolder<Item, MagazineItem> CAN_7_62X51 = magazine("ammo_can_7_62x51", "7.62x51", 200);
    public static final DeferredHolder<Item, MagazineItem> CAN_12_7X99 = magazine("ammo_can_12_7x99", "12.7x99", 100);
    public static final DeferredHolder<Item, MagazineItem> CAN_40X53 = magazine("ammo_can_40x53", "40x53", 48);
    /** Phalanx Block 1B / M61 linkless drum. */
    public static final DeferredHolder<Item, MagazineItem> DRUM_20X102 = magazine("drum_20x102", "20x102", 1550);

    /** M142 stations, 2 x 3 cells. */
    public static final DeferredHolder<Item, PodItem> POD_GMLRS = pod("pod_gmlrs", List.of("gmlrs", "gmlrs_aw"),
            () -> new PodItem(List.of(
                    at(0.3169f, -0.1590f), at(0, -0.1590f), at(-0.3169f, -0.1590f),
                    at(0.3169f, 0.1590f), at(0, 0.1590f), at(-0.3169f, 0.1590f)),
                    4.28f, rl("missile/pod"), new Item.Properties()));
    public static final DeferredHolder<Item, PodItem> POD_ATACMS = pod("pod_atacms", List.of("atacms"),
            () -> new PodItem(List.of(at(0, 0)), 4.0f, rl("missile/pod_large"), new Item.Properties()));

    private MagazineItems() {
    }

    private static ResourceLocation rl(String path) {
        return ResourceLocation.fromNamespaceAndPath(WFLib.MODID, path);
    }

    private static DeferredHolder<Item, MagazineItem> magazine(String path, String calibre, int capacity) {
        CATALOGUE.put(rl(path), new Def(rl(path), rl(calibre), List.of(),
                () -> new MagazineItem(rl(calibre), capacity, new Item.Properties())));
        return DeferredHolder.create(Registries.ITEM, rl(path));
    }

    private static DeferredHolder<Item, PodItem> pod(String path, List<String> missiles, Supplier<Item> factory) {
        CATALOGUE.put(rl(path), new Def(rl(path), null, missiles.stream().map(MagazineItems::rl).toList(), factory));
        return DeferredHolder.create(Registries.ITEM, rl(path));
    }

    public static boolean defined(ResourceLocation id) {
        return CATALOGUE.containsKey(id);
    }

    public static Def def(ResourceLocation id) {
        return CATALOGUE.get(id);
    }

    public static Collection<ResourceLocation> catalogue() {
        return Collections.unmodifiableSet(CATALOGUE.keySet());
    }

    /** Registered ids, catalogue order. */
    public static List<ResourceLocation> live() {
        return live;
    }

    /** {@link WFLibContent#freeze} only. */
    public static void freeze(Set<ResourceLocation> requested) {
        live = CATALOGUE.keySet().stream().filter(requested::contains).toList();
    }

    private static void onRegister(RegisterEvent event) {
        if (!event.getRegistryKey().equals(Registries.ITEM)) {
            return;
        }
        WFLibContent.freeze();
        event.register(Registries.ITEM, helper -> live.forEach(id ->
                helper.register(id, CATALOGUE.get(id).factory().get())));
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

    public static void register(IEventBus bus) {
        COMPONENTS.register(bus);
        bus.addListener(MagazineItems::onRegister);
    }
}
