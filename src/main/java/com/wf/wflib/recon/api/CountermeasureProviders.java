package com.wf.wflib.recon.api;

import com.wf.wflib.core.api.OrderedRegistry;
import net.minecraft.world.entity.Entity;


/** The countermeasure chain, and the empty answer for anything nobody claimed. */
public final class CountermeasureProviders {

    /** Highest priority first. */
    public static final OrderedRegistry<CountermeasureProvider> PROVIDERS =
            OrderedRegistry.create("recon", "recon.countermeasures");

    private CountermeasureProviders() {
    }

    public static int providerCount() {
        return PROVIDERS.values().size();
    }

    /**
     * @return what this entity carries, or {@link TargetSnapshot#NO_COUNTERMEASURES} if nobody claimed it.
     *      Never null, so callers can hand the result straight to a snapshot.
     */
    public static Countermeasure[] of(Entity entity) {
        for (CountermeasureProvider provider : PROVIDERS.values()) {
            Countermeasure[] carried = provider.of(entity);
            if (carried != null) {
                return carried;
            }
        }
        return TargetSnapshot.NO_COUNTERMEASURES;
    }
}
