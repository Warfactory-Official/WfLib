package com.wf.wflib.demolition.api;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;
import com.wf.wflib.demolition.DemolitionTags;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Function;

/** A block that can be set off by a {@link DetonatorItem}. */
public interface IDetonatable {

    /**
     * Another mod's explosive blocks: each answers for a state it knows how to set off, else null. Only blocks tagged
     * {@code wflib:explosive} are asked, so a mod opts a block in by data and by an adapter here.
     */
    List<Function<BlockState, IDetonatable>> ADAPTERS = new CopyOnWriteArrayList<>();

    /** The detonatable behind {@code state}: the block itself, or an adapter's answer; null when it is none or not tagged. */
    @Nullable
    static IDetonatable of(BlockState state) {
        if (!state.is(DemolitionTags.EXPLOSIVE)) return null;
        if (state.getBlock() instanceof IDetonatable d) return d;
        for (Function<BlockState, IDetonatable> adapter : ADAPTERS) {
            IDetonatable d = adapter.apply(state);
            if (d != null) return d;
        }
        return null;
    }

    /**
     * Set this block off.
     *
     * @param detonator the player holding the detonator, or {@code null} if fired without one.
     */
    void detonate(ServerLevel level, BlockPos pos, BlockState state, @Nullable Player detonator);

    /**
     * A recognised explosive: tagged {@code wflib:explosive} (data-driven membership) and actually able to
     * be detonated in code (implements this interface).
     */
    static boolean isExplosive(BlockState state) {
        return of(state) != null;
    }

    /**
     * Detonate the block at {@code pos} if it's a recognised explosive (see {@link #isExplosive}).
     *
     * @return true if a charge was fired.
     */
    static boolean tryDetonate(ServerLevel level, BlockPos pos, @Nullable Player cause) {
        BlockState state = level.getBlockState(pos);
        IDetonatable detonatable = of(state);
        if (detonatable != null) {
            detonatable.detonate(level, pos, state, cause);
            return true;
        }
        return false;
    }
}
