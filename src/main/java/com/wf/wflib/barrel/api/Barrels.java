package com.wf.wflib.barrel.api;

import com.wf.wflib.core.api.WFLibContent;
import com.wf.wflib.core.api.Hook;
import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.registries.DeferredHolder;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.LongSupplier;
import com.wf.wflib.core.api.WFLib;

/** Spare barrel component + item ({@link WFLibContent.Kind#BARREL}: registered iff requested). */
public final class Barrels {
    public static final ResourceLocation SPARE_ID = ResourceLocation.fromNamespaceAndPath(WFLib.MODID, "spare_barrel");

    public static final DeferredHolder<DataComponentType<?>, DataComponentType<SpareBarrel>> SPARE =
            DeferredHolder.create(Registries.DATA_COMPONENT_TYPE, SPARE_ID);

    public static final DeferredHolder<Item, SpareBarrelItem> SPARE_ITEM = DeferredHolder.create(Registries.ITEM, SPARE_ID);

    /** Client level game time, else -1 (tooltip cooling). */
    public static final Hook<LongSupplier> CLIENT_CLOCK = Hook.of("barrel client clock", () -> -1);

    private static volatile boolean live;

    private Barrels() {
    }

    /** {@link WFLibContent#freeze} only. */
    public static void freeze(boolean requested) {
        live = requested;
    }

    public static boolean live() {
        return live;
    }

    public static ItemStack spare(ResourceLocation type, BarrelState state) {
        return spare(new SpareBarrel(type, Optional.empty(), state));
    }

    public static ItemStack spare(SpareBarrel barrel) {
        ItemStack s = new ItemStack(SPARE_ITEM.get());
        s.set(SPARE.get(), barrel);
        return s;
    }

    /** Variant display name: lang {@code barrel.<type ns>.<type path>.<variant>}; empty = the type's name. */
    public static Component name(ResourceLocation type, Optional<String> variant) {
        return variant.<Component>map(v -> Component.translatable("barrel_type." + type.getNamespace() + "."
                + type.getPath().replace('/', '.') + "." + v)).orElseGet(() -> BarrelTypes.name(type));
    }

    @Nullable
    public static SpareBarrel spareOf(ItemStack stack) {
        return stack.get(SPARE.get());
    }

    /** Coolest (then least worn) spare of {@code type} at {@code now}, else null. Returns a stack of {@code stacks}. */
    @Nullable
    public static ItemStack coolest(Iterable<ItemStack> stacks, ResourceLocation type, long now) {
        BarrelSpec spec = BarrelTypes.spec(type);
        ItemStack best = null;
        BarrelState bestState = null;
        for (ItemStack s : stacks) {
            SpareBarrel b = spareOf(s);
            if (b == null || !b.type().equals(type)) {
                continue;
            }
            BarrelState c = b.state().at(spec, now);
            if (bestState == null || c.temp() < bestState.temp()
                    || c.temp() == bestState.temp() && c.wear() < bestState.wear()) {
                best = s;
                bestState = c;
            }
        }
        return best;
    }

    /**
     * Swap in place: {@code spare} (a stack still in its container) takes the barrel coming off ({@code removed},
     * its variant); the spare's barrel, cooled to now, is returned for the gun. Spec = the type's.
     */
    public static SpareBarrel exchange(ItemStack spare, Optional<String> removedVariant, BarrelState removed, long now) {
        SpareBarrel b = spareOf(spare);
        if (b == null) {
            throw new IllegalArgumentException("not a spare barrel: " + spare);
        }
        BarrelSpec spec = BarrelTypes.spec(b.type());
        spare.set(SPARE.get(), new SpareBarrel(b.type(), removedVariant, removed.at(spec, now)));
        return new SpareBarrel(b.type(), b.variant(), b.state().at(spec, now));
    }

    /** Tooltip lines: temperature cooled to {@link #CLIENT_CLOCK} (unknown => as stored) + wear. */
    public static void tooltip(BarrelSpec spec, BarrelState state, List<Component> tooltip) {
        long now = CLIENT_CLOCK.get().getAsLong();
        BarrelState s = state.at(spec, now < 0 ? state.at() : now);
        tooltip.add(Component.translatable("tooltip.wflib.barrel.temp", Math.round(s.temp()))
                .withStyle(s.glow() > 0 ? ChatFormatting.RED : s.haze() > 0 ? ChatFormatting.GOLD : ChatFormatting.GRAY));
        tooltip.add(Component.translatable("tooltip.wflib.barrel.wear", Math.round(s.wear() * 100))
                .withStyle(ChatFormatting.DARK_GRAY));
    }

    /** One new spare per registered type and variant. */
    public static List<ItemStack> creativeStacks() {
        List<ItemStack> out = new ArrayList<>();
        if (live) {
            for (BarrelTypes.Type t : BarrelTypes.all()) {
                out.add(spare(t.id(), BarrelState.NEW));
                t.spec().variants().keySet().stream().sorted().forEach(v ->
                        out.add(spare(new SpareBarrel(t.id(), Optional.of(v), BarrelState.NEW))));
            }
        }
        return out;
    }
}
