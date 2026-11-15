package com.wf.wflib.round.api;

import com.wf.wflib.damage.api.Threat;
import java.util.List;
import net.minecraft.core.RegistryAccess;
import net.minecraft.resources.ResourceLocation;

/** What a round does on its trigger, beyond its own damage and warhead. Server thread. */
@FunctionalInterface
public interface ImpactEffect {

    void apply(ImpactContext ctx);

    /** {@link ImpactTrigger#HIT} only, before the strike event: the threat the target resolves. */
    default Threat threat(ImpactContext ctx, Threat threat) {
        return threat;
    }

    /** Server start, per bound preset: missing registry/warhead refs => {@link IllegalStateException}. */
    default void check(RegistryAccess registries) {
    }

    /** Warheads it detonates: catalogue ones go live with the bound preset. */
    default List<ResourceLocation> warheads() {
        return List.of();
    }
}
