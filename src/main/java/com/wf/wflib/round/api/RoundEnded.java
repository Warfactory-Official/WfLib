package com.wf.wflib.round.api;

import net.minecraft.world.phys.Vec3;

public record RoundEnded(long key, double x, double y, double z, RoundEnd reason) {

    public Vec3 position() {
        return new Vec3(this.x, this.y, this.z);
    }
}
