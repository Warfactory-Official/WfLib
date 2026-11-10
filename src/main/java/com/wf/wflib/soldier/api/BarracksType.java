package com.wf.wflib.soldier.api;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

import java.util.List;

/**
 * Something in the world that is a barracks: using it opens the hire menu. For a plain block use {@link Recruiting#barracks(String,
 * java.util.function.Predicate, Cost, int)}; a multiblock (or a block entity with its own stock) implements this itself: {@link
 * #matches} says whether the clicked position is part of a working barracks, {@link #offers} what it has for hire.
 */
public interface BarracksType {
    String id();

    /** Whether the block at {@code pos} (the one a player clicked) is a barracks of this type. */
    boolean matches(ServerLevel level, BlockPos pos);

    List<Offer> offers(ServerPlayer player, ServerLevel level, BlockPos pos);
}
