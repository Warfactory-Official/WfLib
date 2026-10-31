package com.wf.wflib.armor.api.exo;

import com.wf.wflib.armor.api.ArmorPieceItem;
import com.wf.wflib.armor.api.ArmorPreset;
import com.wf.wflib.core.api.MenuSlots;
import com.wf.wflib.core.api.WFSystems;
import com.wf.wflib.nvg.api.NvgComponents;
import com.wf.wflib.nvg.api.NvgItem;
import com.wf.wflib.nvg.api.NvgWorn;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemContainerContents;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.WeakHashMap;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Slots of the exo attachment popup: three virtual slots of the {@code InventoryMenu} ({@link MenuSlots} group),
 * bound to the garment at menu index {@link #target}. Active only while a popup is open for a garment that offers the kind.
 */
public final class ExoPopup {
    public static final ResourceLocation GROUP = ArmorPreset.rl("exo_popup");
    public static final TagKey<Item> BELT_TAG = TagKey.create(Registries.ITEM, ArmorPreset.rl("artifact_belt"));
    public static final int BATTERY = 0, BELT = 1, NVG = 2, KINDS = 3;
    /** Inventory menu indices a popup may target: armour 5..8, inventory 9..35, hotbar 36..44, offhand 45. */
    public static final int FIRST_TARGET = 5, END_TARGET = 46;

    private static final Map<UUID, Integer> SERVER = new ConcurrentHashMap<>();
    private static final Map<UUID, Integer> CLIENT = new ConcurrentHashMap<>();
    private static final Map<Player, Box> BOXES = new WeakHashMap<>();

    private ExoPopup() {
    }

    public static void register() {
        MenuSlots.register(GROUP, 10, KINDS, () -> true, (owner, i) -> new ExoSlot(box(owner), i),
                (player, source, stack) -> accepts(player, stack) ? KINDS : 0);
    }

    private static Map<UUID, Integer> targets(Player p) {
        return p.level().isClientSide ? CLIENT : SERVER;
    }

    public static int target(Player p) {
        return targets(p).getOrDefault(p.getUUID(), -1);
    }

    /** @return false when {@code index} is not a garment that offers anything */
    public static boolean open(Player p, int index) {
        if (index < FIRST_TARGET || index >= END_TARGET || index >= p.inventoryMenu.slots.size()
                || ExoProfiles.of(p.inventoryMenu.getSlot(index).getItem()) == null
                || !offersAny(p.inventoryMenu.getSlot(index).getItem())) {
            return false;
        }
        targets(p).put(p.getUUID(), index);
        return true;
    }

    public static void close(Player p) {
        targets(p).remove(p.getUUID());
    }

    public static ItemStack garment(Player p) {
        int t = target(p);
        return t < 0 || t >= p.inventoryMenu.slots.size() ? ItemStack.EMPTY : p.inventoryMenu.getSlot(t).getItem();
    }

    public static boolean offersAny(ItemStack garment) {
        for (int k = 0; k < KINDS; k++) {
            if (offers(garment, k)) {
                return true;
            }
        }
        return false;
    }

    public static boolean offers(ItemStack garment, int kind) {
        ExoSpec spec = ExoProfiles.of(garment);
        if (spec == null) {
            return false;
        }
        return switch (kind) {
            case BATTERY -> spec.battery();
            case BELT -> spec.belt();
            case NVG -> WFSystems.enabled("nvg") && garment.getItem() instanceof ArmorPieceItem a && a.preset().nvgMount();
            default -> false;
        };
    }

    public static boolean active(Player p, int kind) {
        ItemStack g = garment(p);
        return !g.isEmpty() && offers(g, kind);
    }

    public static boolean fits(int kind, ItemStack stack) {
        return switch (kind) {
            case BATTERY -> ArmorBattery.accepts(stack);
            case BELT -> stack.is(BELT_TAG);
            case NVG -> stack.getItem() instanceof NvgItem;
            default -> false;
        };
    }

    private static boolean accepts(Player p, ItemStack stack) {
        for (int k = 0; k < KINDS; k++) {
            if (active(p, k) && fits(k, stack)) {
                return true;
            }
        }
        return false;
    }

    private static synchronized Box box(Player owner) {
        return BOXES.computeIfAbsent(owner, Box::new);
    }

    static ItemStack read(ItemStack garment, int kind) {
        return switch (kind) {
            case BATTERY -> ExoStacks.battery(garment);
            case BELT -> ExoStacks.belt(garment);
            case NVG -> NvgWorn.mounted(garment).copy();
            default -> ItemStack.EMPTY;
        };
    }

    static void write(ItemStack garment, int kind, ItemStack stack) {
        switch (kind) {
            case BATTERY -> ExoStacks.setBattery(garment, stack);
            case BELT -> ExoStacks.setBelt(garment, stack);
            case NVG -> {
                if (stack.isEmpty()) {
                    garment.remove(NvgComponents.MOUNT.get());
                } else {
                    garment.set(NvgComponents.MOUNT.get(), ItemContainerContents.fromItems(List.of(stack)));
                }
            }
            default -> {
            }
        }
    }

    /** The three slots as a container over the target garment's components. */
    private static final class Box implements Container {
        private final Player owner;

        Box(Player owner) {
            this.owner = owner;
        }

        private ItemStack garment(int kind) {
            return active(owner, kind) ? ExoPopup.garment(owner) : ItemStack.EMPTY;
        }

        @Override
        public int getContainerSize() {
            return KINDS;
        }

        @Override
        public boolean isEmpty() {
            for (int i = 0; i < KINDS; i++) {
                if (!getItem(i).isEmpty()) {
                    return false;
                }
            }
            return true;
        }

        @Override
        public ItemStack getItem(int slot) {
            ItemStack g = garment(slot);
            return g.isEmpty() ? ItemStack.EMPTY : read(g, slot);
        }

        @Override
        public ItemStack removeItem(int slot, int amount) {
            return removeItemNoUpdate(slot);
        }

        @Override
        public ItemStack removeItemNoUpdate(int slot) {
            ItemStack g = garment(slot);
            if (g.isEmpty()) {
                return ItemStack.EMPTY;
            }
            ItemStack held = read(g, slot);
            if (!held.isEmpty()) {
                write(g, slot, ItemStack.EMPTY);
            }
            return held;
        }

        @Override
        public void setItem(int slot, ItemStack stack) {
            ItemStack g = garment(slot);
            if (!g.isEmpty()) {
                write(g, slot, stack);
            }
        }

        @Override
        public int getMaxStackSize() {
            return 1;
        }

        @Override
        public void setChanged() {
        }

        @Override
        public boolean stillValid(Player player) {
            return true;
        }

        @Override
        public boolean canPlaceItem(int slot, ItemStack stack) {
            return active(owner, slot) && fits(slot, stack);
        }

        @Override
        public void clearContent() {
        }
    }

    public static final class ExoSlot extends Slot {
        private final Box box;

        ExoSlot(Box box, int kind) {
            super(box, kind, -10000, -10000);
            this.box = box;
        }

        @Override
        public boolean isActive() {
            return active(box.owner, getContainerSlot());
        }

        @Override
        public boolean mayPlace(ItemStack stack) {
            return isActive() && fits(getContainerSlot(), stack);
        }

        @Override
        public boolean mayPickup(Player player) {
            return isActive();
        }

        @Override
        public int getMaxStackSize() {
            return 1;
        }

        @Override
        public int getMaxStackSize(ItemStack stack) {
            return 1;
        }

        public int kind() {
            return getContainerSlot();
        }
    }
}
