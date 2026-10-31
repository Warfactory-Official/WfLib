package com.wf.wflib.core.api;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.BooleanSupplier;

/**
 * Extra slots appended to the player's {@code InventoryMenu}. Features register a {@link Group} at startup; the single
 * wflib mixin appends all enabled groups after whatever the menu already holds, so indices are resolved per menu
 * ({@link #range}), never assumed. Foreign mixins adding slots before or after are tolerated; a slot whose index is not
 * contiguous => fail-fast.
 * <ul>
 * <li>Order = priority descending, then id. {@code enabled} and {@code capacity} MUST agree on client and server
 * (slot lists are index-synced).</li>
 * <li>Registration closes at the first menu construction.</li>
 * <li>Shift-click: slots of a group -> main inventory; inventory -> first group whose {@link QuickMove#intake} moves
 * the stack.</li>
 * </ul>
 */
public final class MenuSlots {
    public interface Factory {
        Slot create(Player owner, int index);
    }

    public interface QuickMove {
        QuickMove NONE = (player, source, stack) -> 0;

        /** Leading group slots a stack shift-clicked from {@code sourceIndex} may enter; 0 = none. */
        int intake(Player player, int sourceIndex, ItemStack stack);
    }

    public record Group(ResourceLocation id, int priority, int capacity, BooleanSupplier enabled, Factory factory,
                        QuickMove quickMove) {
    }

    private static final List<Group> REGISTERED = new ArrayList<>();
    private static volatile List<Group> frozen;

    private MenuSlots() {
    }

    public static synchronized Group register(ResourceLocation id, int priority, int capacity, BooleanSupplier enabled,
                                              Factory factory, QuickMove quickMove) {
        Objects.requireNonNull(factory, "factory");
        Objects.requireNonNull(enabled, "enabled");
        Objects.requireNonNull(quickMove, "quickMove");
        if (frozen != null) {
            throw new IllegalStateException("Menu slot registration closed, cannot register " + id);
        }
        new SlotLayout.Entry(id, priority, capacity);
        for (Group g : REGISTERED) {
            if (g.id().equals(id)) {
                throw new IllegalStateException("Menu slot group already registered: " + id);
            }
        }
        Group group = new Group(id, priority, capacity, enabled, factory, quickMove);
        REGISTERED.add(group);
        return group;
    }

    /** Enabled groups in placement order; closes registration. */
    public static List<Group> groups() {
        List<Group> f = frozen;
        if (f == null) {
            synchronized (MenuSlots.class) {
                f = frozen;
                if (f == null) {
                    f = REGISTERED.stream().filter(g -> g.enabled().getAsBoolean())
                            .sorted(java.util.Comparator.comparingInt(Group::priority).reversed()
                                    .thenComparing(g -> g.id().toString())).toList();
                    frozen = f;
                }
            }
        }
        return f;
    }

    public static boolean any() {
        return !groups().isEmpty();
    }

    @Nullable
    public static Group group(ResourceLocation id) {
        for (Group g : groups()) {
            if (g.id().equals(id)) {
                return g;
            }
        }
        return null;
    }

    public static List<SlotLayout.Placed> placed(Player player) {
        return player.inventoryMenu instanceof SlotGroupHost h ? h.wflib$placedGroups() : List.of();
    }

    /** Where {@code id} sits in this player's inventory menu; null when unregistered or disabled. */
    @Nullable
    public static SlotLayout.Placed range(Player player, ResourceLocation id) {
        for (SlotLayout.Placed p : placed(player)) {
            if (p.id().equals(id)) {
                return p;
            }
        }
        return null;
    }

    @Nullable
    public static SlotLayout.Placed groupAt(Player player, int index) {
        return SlotLayout.at(placed(player), index);
    }
}
