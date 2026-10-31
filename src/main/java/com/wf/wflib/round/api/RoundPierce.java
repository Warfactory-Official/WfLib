package com.wf.wflib.round.api;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.Vec3;

/**
 * {@link RoundPierceEvent} for clients: {@code block} entered at {@code entry} through {@code face}; left at
 * {@code point} ({@code exited}) or stopped there. Exit => a re-sync spawn follows in the same packet.
 */
public record RoundPierce(long key, long block, double x, double y, double z, Direction face, double px, double py,
                          double pz, boolean exited) {

    public BlockPos blockPos() {
        return BlockPos.of(this.block);
    }

    public Vec3 entry() {
        return new Vec3(this.x, this.y, this.z);
    }

    public Vec3 point() {
        return new Vec3(this.px, this.py, this.pz);
    }
}
