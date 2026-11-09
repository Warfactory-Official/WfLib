package com.wf.wflib.corpse.api;

import com.mojang.datafixers.util.Pair;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.inventory.SimpleContainerData;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

/**
 * Take-only corpse view. Slots: {@link #ARMOR} head..feet, {@link #OFFHAND}, {@link #PACK}, {@link #KIT}, {@link #MAIN}
 * 27 + hotbar 9 (dead player's layout), {@link #EXTRA} window of {@link #EXTRA_SIZE} over the other entries, then the
 * viewer's 27 + 9. Buttons: {@link #TRANSFER}, {@link #UP}, {@link #DOWN}. Data: extra count, extra scroll row.
 */
public class CorpseMenu extends AbstractContainerMenu {
    public static final int ARMOR = 0, OFFHAND = 4, PACK = 5, KIT = 6, MAIN = 7, HOTBAR = MAIN + 27, EXTRA = HOTBAR + 9;
    public static final int EXTRA_COLS = 9, EXTRA_ROWS = 2, EXTRA_SIZE = EXTRA_COLS * EXTRA_ROWS;
    public static final int INV = EXTRA + EXTRA_SIZE;
    public static final int TRANSFER = 0, UP = 1, DOWN = 2;
    private static final ResourceLocation[] ARMOR_ICONS = {InventoryMenu.EMPTY_ARMOR_SLOT_HELMET,
            InventoryMenu.EMPTY_ARMOR_SLOT_CHESTPLATE, InventoryMenu.EMPTY_ARMOR_SLOT_LEGGINGS,
            InventoryMenu.EMPTY_ARMOR_SLOT_BOOTS};

    private final @Nullable CorpseEntity corpse;
    private final int corpseId;
    private final ContainerData data;

    public CorpseMenu(int id, Inventory viewer, CorpseEntity corpse) {
        this(id, viewer, corpse, corpse.getId(), new SimpleContainerData(2));
    }

    public static CorpseMenu client(int id, Inventory viewer, RegistryFriendlyByteBuf buf) {
        return new CorpseMenu(id, viewer, null, buf.readVarInt(), new SimpleContainerData(2));
    }

    private CorpseMenu(int id, Inventory viewer, @Nullable CorpseEntity corpse, int corpseId, ContainerData data) {
        super(Corpses.MENU.get(), id);
        this.corpse = corpse;
        this.corpseId = corpseId;
        this.data = data;
        Container view = corpse == null ? new SimpleContainer(INV) : new View(corpse, data);
        for (int i = 0; i < INV; i++) {
            int index = i;
            addSlot(new Slot(view, i, 0, 0) {
                @Override
                public boolean mayPlace(ItemStack stack) {
                    return false;
                }

                @Override
                public @Nullable Pair<ResourceLocation, ResourceLocation> getNoItemIcon() {
                    if (index < OFFHAND) {
                        return Pair.of(InventoryMenu.BLOCK_ATLAS, ARMOR_ICONS[index]);
                    }
                    return index == OFFHAND ? Pair.of(InventoryMenu.BLOCK_ATLAS, InventoryMenu.EMPTY_ARMOR_SLOT_SHIELD)
                            : null;
                }
            });
        }
        for (int r = 0; r < 3; r++) {
            for (int c = 0; c < 9; c++) {
                addSlot(new Slot(viewer, 9 + r * 9 + c, 0, 0));
            }
        }
        for (int c = 0; c < 9; c++) {
            addSlot(new Slot(viewer, c, 0, 0));
        }
        addDataSlots(data);
    }

    /** Slot of {@code e} among the fixed positions, else -1. */
    static int position(CorpseEntity.Entry e) {
        if (Corpses.BACKPACK.equals(e.kind)) {
            return PACK;
        }
        if (Corpses.CARRY.equals(e.kind)) {
            return KIT;
        }
        if (!Corpses.INVENTORY.equals(e.kind)) {
            return -1;
        }
        int key = Integer.parseInt(e.key);
        if (key < 9) {
            return HOTBAR + key;
        }
        if (key < 36) {
            return MAIN + key - 9;
        }
        return key < 40 ? ARMOR + 39 - key : OFFHAND;
    }

    static boolean fixed(CorpseEntity.Entry e) {
        return position(e) >= 0;
    }

    public int corpseId() {
        return corpseId;
    }

    public int extras() {
        return data.get(0);
    }

    public int scrollRow() {
        return data.get(1);
    }

    @Override
    public boolean clickMenuButton(Player player, int id) {
        if (corpse == null || !(player instanceof ServerPlayer sp)) {
            return false;
        }
        switch (id) {
            case TRANSFER -> corpse.transfer(sp);
            case UP -> data.set(1, Math.max(0, data.get(1) - 1));
            case DOWN -> data.set(1, Math.min(maxScroll(), data.get(1) + 1));
            default -> {
                return false;
            }
        }
        return true;
    }

    private int maxScroll() {
        return Math.max(0, (data.get(0) + EXTRA_COLS - 1) / EXTRA_COLS - EXTRA_ROWS);
    }

    @Override
    public void broadcastChanges() {
        if (corpse != null) {
            data.set(0, corpse.extras().size());
            data.set(1, Math.min(data.get(1), maxScroll()));
        }
        super.broadcastChanges();
    }

    @Override
    public boolean stillValid(Player viewer) {
        return corpse == null || corpse.isAlive() && corpse.level() == viewer.level()
                && corpse.distanceTo(viewer) <= CorpseEntity.REACH;
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        Slot slot = slots.get(index);
        if (index >= INV || !slot.hasItem()) {
            return ItemStack.EMPTY;
        }
        ItemStack stack = slot.getItem();
        ItemStack before = stack.copy();
        if (!moveItemStackTo(stack, INV, INV + 36, true)) {
            return ItemStack.EMPTY;
        }
        slot.setChanged();
        return before;
    }

    /** Positions -> live corpse entries; every write ends in {@link CorpseEntity#changed}. */
    private static final class View implements Container {
        private final CorpseEntity corpse;
        private final ContainerData data;

        View(CorpseEntity corpse, ContainerData data) {
            this.corpse = corpse;
            this.data = data;
        }

        private @Nullable CorpseEntity.Entry entry(int pos) {
            if (pos >= EXTRA) {
                int i = data.get(1) * EXTRA_COLS + pos - EXTRA;
                java.util.List<CorpseEntity.Entry> extras = corpse.extras();
                return i < extras.size() ? extras.get(i) : null;
            }
            for (CorpseEntity.Entry e : corpse.entries()) {
                if (position(e) == pos) {
                    return e;
                }
            }
            return null;
        }

        @Override
        public int getContainerSize() {
            return INV;
        }

        @Override
        public boolean isEmpty() {
            return corpse.entries().isEmpty();
        }

        @Override
        public ItemStack getItem(int pos) {
            CorpseEntity.Entry e = entry(pos);
            return e == null ? ItemStack.EMPTY : e.stack;
        }

        @Override
        public ItemStack removeItem(int pos, int count) {
            CorpseEntity.Entry e = entry(pos);
            if (e == null) {
                return ItemStack.EMPTY;
            }
            ItemStack out = e.stack.split(count);
            corpse.changed();
            return out;
        }

        @Override
        public ItemStack removeItemNoUpdate(int pos) {
            CorpseEntity.Entry e = entry(pos);
            if (e == null) {
                return ItemStack.EMPTY;
            }
            ItemStack out = e.stack;
            e.stack = ItemStack.EMPTY;
            corpse.changed();
            return out;
        }

        @Override
        public void setItem(int pos, ItemStack stack) {
            CorpseEntity.Entry e = entry(pos);
            if (e != null) {
                e.stack = stack;
                corpse.changed();
            } else if (!stack.isEmpty()) {
                throw new IllegalStateException("corpse view is take-only, position " + pos);
            }
        }

        @Override
        public void setChanged() {
            corpse.changed();
        }

        @Override
        public boolean stillValid(Player player) {
            return true;
        }

        @Override
        public void clearContent() {
            corpse.entries().clear();
            corpse.changed();
        }
    }
}
