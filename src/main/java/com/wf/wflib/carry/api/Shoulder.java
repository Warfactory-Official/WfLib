package com.wf.wflib.carry.api;

import com.wf.wflib.core.api.Hook;
import com.wf.wflib.core.api.OrderedRegistry;
import com.wf.wflib.core.api.WFLib;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemContainerContents;
import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.NeoForgeRegistries;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.function.Predicate;

/**
 * A carried item that becomes a real hand-held gun while its carrier may shoulder it (an exoskeleton with a powered
 * shoulder rig). The owner of the carried item registers a {@link Weapon}; the gun is another mod's item, held as a
 * synthetic stack inside the carried stack ({@link #GUN}), returned as the carrier's main-hand item while
 * {@link #gun} is non-empty. Leaving it needs {@link Carry#drop} or storage; the hotbar slot is locked.
 */
public final class Shoulder {
    public interface Weapon {
        boolean matches(ItemStack carried);

        /** Item of the gun to hold, or null when it is not installed. */
        @Nullable
        ResourceLocation gun(ItemStack carried);
    }

    public static final OrderedRegistry<Weapon> WEAPONS = OrderedRegistry.create("carry", "shoulderable weapons");
    /** Set by the exoskeleton system: may this player shoulder right now. */
    public static final Hook<Predicate<Player>> PERMIT = Hook.of("carry.shoulder_permit", p -> false);

    /** Live synthetic gun of a shouldering carrier (mutated in place by the gun's own code), synced with the carried stack. */
    public static final DeferredHolder<AttachmentType<?>, AttachmentType<ItemStack>> HELD = DeferredHolder.create(
            NeoForgeRegistries.Keys.ATTACHMENT_TYPES, ResourceLocation.fromNamespaceAndPath(WFLib.MODID, "shouldered_gun"));
    /** The gun's state, kept on the carried stack while nobody shoulders it (drop, storage, pickup). */
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<ItemContainerContents>> STATE =
            DeferredHolder.create(Registries.DATA_COMPONENT_TYPE,
                    ResourceLocation.fromNamespaceAndPath(WFLib.MODID, "shouldered_gun"));

    private Shoulder() {
    }

    @Nullable
    public static Weapon weapon(ItemStack carried) {
        if (carried.isEmpty()) {
            return null;
        }
        for (Weapon w : WEAPONS.values()) {
            if (w.matches(carried)) {
                return w;
            }
        }
        return null;
    }

    /** Whether {@code carrier} is currently holding the gun. */
    public static boolean shouldering(Player carrier) {
        return !gun(carrier).isEmpty();
    }

    /** The gun in hand, or {@link ItemStack#EMPTY}. */
    public static ItemStack gun(Player carrier) {
        if (!Carry.carrying(carrier) || !PERMIT.get().test(carrier)) {
            return ItemStack.EMPTY;
        }
        return carrier.getData(HELD);
    }

    /** Takes the gun out of {@code HELD} into the carried stack, before that stack leaves the carrier. */
    public static void stash(Player carrier) {
        ItemStack gun = carrier.getData(HELD);
        ItemStack kit = Carry.carried(carrier);
        if (!gun.isEmpty() && !kit.isEmpty()) {
            kit.set(STATE.get(), ItemContainerContents.fromItems(List.of(gun.copy())));
        }
        carrier.setData(HELD, ItemStack.EMPTY);
    }

    /** Puts the carried kit's gun in hand when a weapon is registered and installed; true if {@code HELD} changed. */
    public static boolean arm(Player carrier) {
        ItemStack kit = Carry.carried(carrier);
        if (!carrier.getData(HELD).isEmpty()) {
            return false;
        }
        ItemContainerContents saved = kit.get(STATE.get());
        if (saved != null && !saved.copyOne().isEmpty()) {
            carrier.setData(HELD, saved.copyOne());
            return true;
        }
        Weapon w = weapon(kit);
        ResourceLocation id = w == null ? null : w.gun(kit);
        if (id == null) {
            return false;
        }
        Item item = BuiltInRegistries.ITEM.get(id);
        if (item == net.minecraft.world.item.Items.AIR) {
            return false;
        }
        carrier.setData(HELD, new ItemStack(item));
        return true;
    }
}
