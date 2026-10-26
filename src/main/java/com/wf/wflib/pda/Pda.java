package com.wf.wflib.pda;

import com.wf.wflib.item.ModItems;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

/** Who has a PDA: {@link #carries} = anywhere in the inventory (receivers), {@link #held} = in either hand. */
public final class Pda {
    private Pda() {
    }

    public static boolean is(ItemStack stack) {
        return stack.is(ModItems.PDA.get());
    }

    public static boolean carries(Player player) {
        return !carriedStack(player).isEmpty();
    }


    /** Actual carried stack, never a copy: hands first, then inventory order, then equipped Curios.
     * Multiple PDAs use this same deterministic choice on client and server. */
    public static ItemStack carriedStack(Player player) {
        ItemStack held = heldStack(player);
        if (!held.isEmpty()) {
            return held;
        }
        for (int slot = 0; slot < player.getInventory().getContainerSize(); slot++) {
            ItemStack stack = player.getInventory().getItem(slot);
            if (is(stack)) {
                return stack;
            }
        }
        return PdaCurios.stack(player);
    }

    public static boolean held(Player player) {
        return is(player.getMainHandItem()) || is(player.getOffhandItem());
    }

    /** Held stack, main hand first; {@link ItemStack#EMPTY} if none. */
    public static ItemStack heldStack(Player player) {
        ItemStack main = player.getMainHandItem();
        if (is(main)) {
            return main;
        }
        ItemStack off = player.getOffhandItem();
        return is(off) ? off : ItemStack.EMPTY;
    }
}
