package com.wf.wflib.mist.spray;

import com.wf.wflib.mist.api.GasCloud;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

/** Releases any fluid into the world: gas as a {@link GasCloud} puff, liquid as a {@link SprayCurtainEntity}. */
public final class Sprays {

    /** Puff cell cap; a pod emits one every few ticks. */
    private static final int PUFF_MAX_CELLS = 512;
    public static final int PUFF_TICKS = 200;
    public static final int CURTAIN_SPEED = 3;

    private Sprays() {
    }

    /** Payload {@code gas}, {@code MistEffects}-bearing or lighter-than-air => gas. */
    public static boolean isGas(Fluid fluid) {
        return com.wf.wflib.mist.api.MistEffects.get(fluid) != null || fluid.getFluidType().isLighterThanAir();
    }

    /** @param radius footprint (blocks); puff flood-fill reach / curtain disc radius */
    public static void emit(ServerLevel level, Fluid fluid, Vec3 at, int radius, @Nullable UUID faction) {
        if (isGas(fluid)) {
            GasCloud.spawn(level, fluid, at, radius, PUFF_MAX_CELLS, PUFF_TICKS);
        } else {
            SprayCurtainEntity.spawn(level, fluid, at, radius, CURTAIN_SPEED, faction);
        }
    }
}
