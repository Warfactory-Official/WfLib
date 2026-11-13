package com.wf.wflib.mist.spray;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluid;
import org.jetbrains.annotations.Nullable;

/** What a falling liquid curtain does to the block it reaches. Server-side; runs once per column per layer. */
public interface SprayEffect {

    /**
     * @param at    replaced position, null = none
     * @param to    new state at {@code at}; claim-protected edits are dropped and stop the column
     * @param stop  column ends here
     */
    record Touch(@Nullable BlockPos at, @Nullable BlockState to, boolean stop) {
        public static final Touch PASS = new Touch(null, null, false);
        public static final Touch STOP = new Touch(null, null, true);

        public static Touch edit(BlockPos at, BlockState to, boolean stop) {
            return new Touch(at.immutable(), to, stop);
        }
    }

    Touch touch(ServerLevel level, BlockPos pos, BlockState state, Fluid fluid);

    /** Air / non-colliding dry blocks fall through; anything else ends the column. */
    static Touch plain(ServerLevel level, BlockPos pos, BlockState state) {
        if (state.isAir() || state.getFluidState().isEmpty() && state.getCollisionShape(level, pos).isEmpty()) {
            return Touch.PASS;
        }
        return Touch.STOP;
    }
}
