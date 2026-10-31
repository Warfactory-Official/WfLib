package com.wf.wflib.round.api;

import net.minecraft.world.phys.Vec3;

/**
 * Round skipped off {@code surface} at {@code (x, y, z)}, outward normal {@code n*}; {@code block} = struck block
 * (BLOCK), water block (WATER), else 0. A re-sync spawn follows in the same packet.
 */
public record RoundRicochet(long key, double x, double y, double z, float nx, float ny, float nz,
                            RoundSurface surface, long block) {

    public Vec3 position() {
        return new Vec3(this.x, this.y, this.z);
    }

    public Vec3 normal() {
        return new Vec3(this.nx, this.ny, this.nz);
    }
}
