package com.wf.wflib.barrel;

import com.wf.wflib.WFLib;
import com.wf.wflib.content.WFLibContent;
import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.RegisterEvent;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.function.LongSupplier;

/** Spare barrel component + item ({@link WFLibContent.Kind#BARREL}: registered iff requested). */
public final class Barrels {
    public static final ResourceLocation SPARE_ID = ResourceLocation.fromNamespaceAndPath(WFLib.MODID, "spare_barrel");

    private static final DeferredRegister<DataComponentType<?>> COMPONENTS =
            DeferredRegister.create(Registries.DATA_COMPONENT_TYPE, WFLib.MODID);

    public static final DeferredHolder<DataComponentType<?>, DataComponentType<SpareBarrel>> SPARE =
            COMPONENTS.register("spare_barrel", () -> DataComponentType.<SpareBarrel>builder()
                    .persistent(SpareBarrel.CODEC).networkSynchronized(SpareBarrel.STREAM_CODEC).cacheEncoding().build());

    public static final DeferredHolder<Item, SpareBarrelItem> SPARE_ITEM = DeferredHolder.create(Registries.ITEM, SPARE_ID);

    /** Client level game time, else -1 (tooltip cooling). */
    public static volatile LongSupplier clientClock = () -> -1;

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
        ItemStack s = new ItemStack(SPARE_ITEM.get());
        s.set(SPARE.get(), new SpareBarrel(type, state));
        return s;
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
     * Swap in place: {@code spare} (a stack still in its container) takes the barrel coming off; the spare's state is
     * returned for the gun. Spec = the type's.
     */
    public static BarrelState exchange(ItemStack spare, BarrelState removed, long now) {
        SpareBarrel b = spareOf(spare);
        if (b == null) {
            throw new IllegalArgumentException("not a spare barrel: " + spare);
        }
        BarrelSpec spec = BarrelTypes.spec(b.type());
        spare.set(SPARE.get(), new SpareBarrel(b.type(), removed.at(spec, now)));
        return b.state().at(spec, now);
    }

    /** Tooltip lines: temperature cooled to {@link #clientClock} (unknown => as stored) + wear. */
    public static void tooltip(BarrelSpec spec, BarrelState state, List<Component> tooltip) {
        long now = clientClock.getAsLong();
        BarrelState s = state.at(spec, now < 0 ? state.at() : now);
        tooltip.add(Component.translatable("tooltip.wflib.barrel.temp", Math.round(s.temp()))
                .withStyle(s.glow() > 0 ? ChatFormatting.RED : s.haze() > 0 ? ChatFormatting.GOLD : ChatFormatting.GRAY));
        tooltip.add(Component.translatable("tooltip.wflib.barrel.wear", Math.round(s.wear() * 100))
                .withStyle(ChatFormatting.DARK_GRAY));
    }

    /** One new spare per registered type. */
    public static List<ItemStack> creativeStacks() {
        List<ItemStack> out = new ArrayList<>();
        if (live) {
            BarrelTypes.all().forEach(t -> out.add(spare(t.id(), BarrelState.NEW)));
        }
        return out;
    }

    private static void onRegister(RegisterEvent event) {
        if (!event.getRegistryKey().equals(Registries.ITEM)) {
            return;
        }
        WFLibContent.freeze();
        if (live) {
            event.register(Registries.ITEM, helper -> helper.register(SPARE_ID, new SpareBarrelItem(new Item.Properties())));
        }
    }

    public static void register(IEventBus bus) {
        COMPONENTS.register(bus);
        bus.addListener(Barrels::onRegister);
    }
}
