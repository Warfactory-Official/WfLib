package com.wf.wflib.warhead;

import com.wf.wflib.entity.mist.GasCloud;
import com.wf.wflib.fluid.WFFluids;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;

/** Riot-control round: tear-gas cloud, radius = blast size blocks (unset => {@link #RADIUS}). */
public final class TearGasWarhead {
    public static final ResourceLocation ID = WarheadRegistry.rl("tear_gas");
    static final int RADIUS = 3;
    /** Ticks; CS grenade burn ~20-40 s. */
    static final int DURATION = 500;

    private TearGasWarhead() {
    }

    public static void detonate(WarheadCarrier source, Vec3 pos) {
        if (source.level().isClientSide) {
            return;
        }
        int radius = source.blastSize() > 0 ? Math.round(source.blastSize()) : RADIUS;
        GasCloud.spawn(source.level(), WFFluids.TEAR_GAS.get(), pos, radius, GasCloud.DEFAULT_MAX_CELLS, DURATION);
    }
}
