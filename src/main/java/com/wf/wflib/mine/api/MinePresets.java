package com.wf.wflib.mine.api;

import com.wf.wflib.core.api.WFLibContent;
import com.wf.wflib.core.api.WFRegistry;
import com.wf.wflib.mine.MineCatalogue;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.List;
import java.util.Set;
import com.wf.wflib.core.api.WFLib;

/**
 * Deploy-ready {@link MinePreset}s. Every catalogue definition is present (carrier data: a placed mine resolves its
 * preset without an item); a catalogue preset has an item iff requested via {@link WFLibContent}, a preset a
 * dependent registers always has one. Registration: before the first {@code RegisterEvent}.
 */
public final class MinePresets {

    static final WFRegistry<MinePreset> PRESETS = WFRegistry.create("mine", "mine presets");

    private static final ResourceLocation DEFAULT_ID = rl("ap");
    private static volatile List<ResourceLocation> live = List.of();

    private MinePresets() {
    }

    public static ResourceLocation rl(String path) {
        return ResourceLocation.fromNamespaceAndPath(WFLib.MODID, path);
    }

    public static ResourceLocation parse(String id) {
        if (id == null || id.isEmpty()) {
            return DEFAULT_ID;
        }
        ResourceLocation parsed = id.indexOf(':') >= 0 ? ResourceLocation.tryParse(id) : rl(id);
        return parsed != null ? parsed : DEFAULT_ID;
    }

    /** Dup id or late registration throws. */
    public static void register(MinePreset preset) {
        PRESETS.register(preset.id(), preset);
    }

    @Nullable
    public static MinePreset get(ResourceLocation id) {
        return PRESETS.get(id);
    }

    public static boolean exists(ResourceLocation id) {
        return PRESETS.containsKey(id);
    }

    public static Collection<MinePreset> all() {
        return PRESETS.values();
    }

    public static ResourceLocation defaultId() {
        return DEFAULT_ID;
    }

    /** @return the presets that can only be sown, never laid by hand, in registration order. */
    public static Collection<MinePreset> sown() {
        return PRESETS.values().stream().filter(MinePreset::sownOnly).toList();
    }

    /**
     * @return what a minelayer loads when nobody said which mine: the first sown-only preset there is,
     *      falling back to the plain default if a pack has removed all of them.
     */
    public static ResourceLocation defaultSownId() {
        return sown().stream().findFirst().map(MinePreset::id).orElse(DEFAULT_ID);
    }

    /** @return one of the item a preset was laid from, or empty if that preset has no item. */
    public static ItemStack stackFor(@Nullable ResourceLocation presetId) {
        if (presetId == null || !live.contains(presetId)) {
            return ItemStack.EMPTY;
        }
        return BuiltInRegistries.ITEM.getOptional(itemId(presetId)).map(ItemStack::new).orElse(ItemStack.EMPTY);
    }

    public static ResourceLocation itemId(ResourceLocation preset) {
        return ResourceLocation.fromNamespaceAndPath(preset.getNamespace(), "mine_" + preset.getPath());
    }

    public static boolean defined(ResourceLocation id) {
        return MineCatalogue.defined(id);
    }

    public static Collection<ResourceLocation> catalogue() {
        return MineCatalogue.ids();
    }

    /** Ids that have an item, registration order. */
    public static List<ResourceLocation> live() {
        return live;
    }

    /** {@link WFLibContent#freeze} only. */
    public static void freeze(Set<ResourceLocation> requested) {
        live = PRESETS.ids().stream()
                .filter(id -> !MineCatalogue.defined(id) || requested.contains(id))
                .toList();
    }
}
