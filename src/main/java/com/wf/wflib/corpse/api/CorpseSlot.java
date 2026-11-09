package com.wf.wflib.corpse.api;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

import java.util.function.BiConsumer;

/** One kind of player slot a corpse remembers, so transfer puts a stack back where it was worn. */
public interface CorpseSlot {

    ResourceLocation id();

    /** Every non-empty stack: key, live stack. */
    void each(Player player, BiConsumer<String, ItemStack> out);

    /** Removes the stack, no sync: {@code player} may be an offline save. */
    ItemStack take(Player player, String key);

    /** {@code stack} into the free slot; true if placed. */
    boolean put(ServerPlayer player, String key, ItemStack stack);
}
