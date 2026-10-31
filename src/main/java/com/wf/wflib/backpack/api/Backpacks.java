package com.wf.wflib.backpack.api;

import com.wf.wflib.backpack.BackpackPackets;
import com.wf.wflib.core.api.Hook;
import net.minecraft.core.NonNullList;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemContainerContents;
import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.capabilities.EntityCapability;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

import java.util.Map;
import java.util.WeakHashMap;
import java.util.function.BooleanSupplier;
import java.util.function.ToDoubleFunction;
import com.wf.wflib.core.api.WFLib;

/**
 * Worn backpack = player attachment {@link #WORN}, no equipment slot; contents exposed as player capability
 * {@link #HANDLER}. Contents on the stack ({@link #CONTENTS}); edited through {@link BackpackContainer}: owner's
 * {@code InventoryMenu} (mixin, slots {@link #FIRST_SLOT}..) or another player's {@link BackpackMenu} (probe action
 * {@link #OPEN}, refused while {@link #LOCKED}). Sync: owner full stack, trackers without contents. Holders are
 * registered by {@code BackpackSystem}.
 */
public final class Backpacks {
    public static final int MAX_SLOTS = 64;
    /** {@code InventoryMenu}: 46 vanilla slots, then backpack slots. */
    public static final int FIRST_SLOT = 46;
    /** Open + stay open within this (feet to feet); probe reach is 8. */
    public static final double VIEW_RANGE = 4;
    public static final ResourceLocation OPEN = ResourceLocation.fromNamespaceAndPath(WFLib.MODID, "backpack_open");

    public static final DeferredHolder<AttachmentType<?>, AttachmentType<ItemStack>> WORN = DeferredHolder.create(
            NeoForgeRegistries.Keys.ATTACHMENT_TYPES, ResourceLocation.fromNamespaceAndPath(WFLib.MODID, "worn_backpack"));
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<ItemContainerContents>> CONTENTS =
            DeferredHolder.create(Registries.DATA_COMPONENT_TYPE,
                    ResourceLocation.fromNamespaceAndPath(WFLib.MODID, "backpack_contents"));
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<Boolean>> LOCKED =
            DeferredHolder.create(Registries.DATA_COMPONENT_TYPE,
                    ResourceLocation.fromNamespaceAndPath(WFLib.MODID, "backpack_locked"));
    /** Worn backpack's contents; null when none worn. */
    public static final EntityCapability<IItemHandler, Void> HANDLER = EntityCapability.createVoid(
            ResourceLocation.fromNamespaceAndPath(WFLib.MODID, "backpack"), IItemHandler.class);
    public static final DeferredHolder<MenuType<?>, MenuType<BackpackMenu>> MENU = DeferredHolder.create(
            Registries.MENU, ResourceLocation.fromNamespaceAndPath(WFLib.MODID, "backpack"));

    /** Unit mass of a stack (kg x count). */
    public static final Hook<ToDoubleFunction<ItemStack>> WEIGHER = Hook.of("backpack.weigher", s -> 0);
    /** Client: inventory panel shown => owner's backpack slots active. */
    public static final Hook<BooleanSupplier> PANEL_SHOWN = Hook.of("backpack.panel_shown", () -> false);

    private static final Map<Player, BackpackContainer> CONTAINERS = new WeakHashMap<>();

    private Backpacks() {
    }

    public static ItemStack worn(Player player) {
        return player.getData(WORN);
    }

    public static BackpackContainer container(Player player) {
        synchronized (CONTAINERS) {
            return CONTAINERS.computeIfAbsent(player, BackpackContainer::new);
        }
    }

    public static boolean locked(ItemStack backpack) {
        return backpack.getOrDefault(LOCKED.get(), false);
    }

    public static NonNullList<ItemStack> contents(ItemStack backpack) {
        NonNullList<ItemStack> items = NonNullList.withSize(MAX_SLOTS, ItemStack.EMPTY);
        ItemContainerContents c = backpack.get(CONTENTS.get());
        if (c != null) {
            c.copyInto(items);
        }
        return items;
    }

    public static int used(ItemStack backpack) {
        ItemContainerContents c = backpack.get(CONTENTS.get());
        return c == null ? 0 : (int) c.nonEmptyStream().count();
    }

    /** Contents mass, before {@link BackpackSpec#contentWeight}. */
    public static double loadKg(ItemStack backpack) {
        ItemContainerContents c = backpack.get(CONTENTS.get());
        return c == null ? 0 : c.nonEmptyStream().mapToDouble(WEIGHER.get()).sum();
    }

    public static void setWorn(ServerPlayer player, ItemStack stack) {
        player.setData(WORN, stack);
        player.containerMenu.broadcastChanges();
        sync(player);
    }

    /** Owner: full stack; trackers: contents stripped. */
    public static void sync(Player player) {
        if (!(player instanceof ServerPlayer sp)) {
            return;
        }
        ItemStack worn = worn(sp);
        PacketDistributor.sendToPlayer(sp, new BackpackPackets.Worn(sp.getId(), worn.copy()));
        PacketDistributor.sendToPlayersTrackingEntity(sp, new BackpackPackets.Worn(sp.getId(), shown(worn)));
    }

    /** What trackers see: the stack without its contents. */
    public static ItemStack shown(ItemStack worn) {
        ItemStack s = worn.copy();
        s.remove(CONTENTS.get());
        return s;
    }
}
