package com.wf.wflib.corpse;

import com.wf.wflib.backpack.api.BackpackItem;
import com.wf.wflib.backpack.api.Backpacks;
import com.wf.wflib.carry.api.Carry;
import com.wf.wflib.carry.api.Shoulder;
import com.wf.wflib.corpse.api.CorpseSlot;
import com.wf.wflib.corpse.api.Corpses;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.neoforged.fml.ModList;

import java.util.function.BiConsumer;

/** Built-in kinds: vanilla inventory (index key), worn backpack, carried kit, Curios ({@code id/index}). */
final class CorpseSlots {
    private CorpseSlots() {
    }

    static final CorpseSlot INVENTORY = new CorpseSlot() {
        @Override
        public ResourceLocation id() {
            return Corpses.INVENTORY;
        }

        @Override
        public void each(Player player, BiConsumer<String, ItemStack> out) {
            Inventory inv = player.getInventory();
            for (int i = 0; i < inv.getContainerSize(); i++) {
                if (!inv.getItem(i).isEmpty()) {
                    out.accept(Integer.toString(i), inv.getItem(i));
                }
            }
        }

        @Override
        public ItemStack take(Player player, String key) {
            return player.getInventory().removeItemNoUpdate(Integer.parseInt(key));
        }

        @Override
        public boolean put(ServerPlayer player, String key, ItemStack stack) {
            int i = Integer.parseInt(key);
            if (!player.getInventory().getItem(i).isEmpty()) {
                return false;
            }
            player.getInventory().setItem(i, stack);
            return true;
        }
    };

    static final CorpseSlot BACKPACK = new CorpseSlot() {
        @Override
        public ResourceLocation id() {
            return Corpses.BACKPACK;
        }

        @Override
        public void each(Player player, BiConsumer<String, ItemStack> out) {
            if (!Backpacks.worn(player).isEmpty()) {
                out.accept("", Backpacks.worn(player));
            }
        }

        @Override
        public ItemStack take(Player player, String key) {
            ItemStack worn = Backpacks.worn(player);
            player.setData(Backpacks.WORN, ItemStack.EMPTY);
            return worn;
        }

        @Override
        public boolean put(ServerPlayer player, String key, ItemStack stack) {
            if (!Backpacks.worn(player).isEmpty() || !(stack.getItem() instanceof BackpackItem)) {
                return false;
            }
            Backpacks.setWorn(player, stack);
            return true;
        }
    };

    /** The kit carries the shouldered gun ({@link Shoulder#stash}). */
    static final CorpseSlot CARRY = new CorpseSlot() {
        @Override
        public ResourceLocation id() {
            return Corpses.CARRY;
        }

        @Override
        public void each(Player player, BiConsumer<String, ItemStack> out) {
            if (!Carry.carried(player).isEmpty()) {
                out.accept("", Carry.carried(player));
            }
        }

        @Override
        public ItemStack take(Player player, String key) {
            Shoulder.stash(player);
            ItemStack kit = Carry.carried(player);
            player.setData(Carry.CARRIED, ItemStack.EMPTY);
            return kit;
        }

        @Override
        public boolean put(ServerPlayer player, String key, ItemStack stack) {
            if (!Carry.carried(player).isEmpty() || Carry.body(player) >= 0 || player.isCreative() || player.isPassenger()) {
                return false;
            }
            Carry.set(player, stack);
            return true;
        }
    };

    /** Only {@link Loaded} links Curios. */
    static final CorpseSlot CURIOS = new CorpseSlot() {
        @Override
        public ResourceLocation id() {
            return Corpses.CURIOS;
        }

        @Override
        public void each(Player player, BiConsumer<String, ItemStack> out) {
            Loaded.each(player, out);
        }

        @Override
        public ItemStack take(Player player, String key) {
            return Loaded.take(player, key);
        }

        @Override
        public boolean put(ServerPlayer player, String key, ItemStack stack) {
            return Loaded.put(player, key, stack);
        }
    };

    /** Save loaded from disk, never ticked: Curios stacks failing {@code isItemValid} sit in its invalid list (lost on write-back) => into the inventory first. */
    static void settle(Player player) {
        if (ModList.get().isLoaded("curios")) {
            Loaded.settle(player);
        }
    }

    private static final class Loaded {
        static void settle(Player player) {
            top.theillusivec4.curios.api.CuriosApi.getCuriosInventory(player).ifPresent(inv -> inv.handleInvalidStacks());
        }

        static void each(Player player, BiConsumer<String, ItemStack> out) {
            top.theillusivec4.curios.api.CuriosApi.getCuriosInventory(player).ifPresent(inv ->
                    inv.getCurios().forEach((id, handler) -> {
                        var stacks = handler.getStacks();
                        for (int i = 0; i < stacks.getSlots(); i++) {
                            if (!stacks.getStackInSlot(i).isEmpty()) {
                                out.accept(id + "/" + i, stacks.getStackInSlot(i));
                            }
                        }
                    }));
        }

        static ItemStack take(Player player, String key) {
            int cut = key.lastIndexOf('/');
            int i = Integer.parseInt(key.substring(cut + 1));
            return top.theillusivec4.curios.api.CuriosApi.getCuriosInventory(player)
                    .flatMap(inv -> inv.getStacksHandler(key.substring(0, cut)))
                    .map(h -> {
                        ItemStack s = h.getStacks().getStackInSlot(i);
                        h.getStacks().setStackInSlot(i, ItemStack.EMPTY);
                        return s;
                    }).orElse(ItemStack.EMPTY);
        }

        static boolean put(ServerPlayer player, String key, ItemStack stack) {
            int cut = key.lastIndexOf('/');
            int i = Integer.parseInt(key.substring(cut + 1));
            return top.theillusivec4.curios.api.CuriosApi.getCuriosInventory(player)
                    .flatMap(inv -> inv.getStacksHandler(key.substring(0, cut)))
                    .filter(h -> i < h.getStacks().getSlots() && h.getStacks().getStackInSlot(i).isEmpty())
                    .map(h -> {
                        h.getStacks().setStackInSlot(i, stack);
                        return true;
                    }).orElse(false);
        }
    }
}
