package com.wf.wflib.recon.api;

import com.wf.wflib.core.api.OrderedRegistry;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.AABB;


/** The provider chain, and the fallback for anything nobody claimed. */
public final class SignatureProviders {

    /** Reference cross-section, in blocks of frontal area. */
    private static final double REFERENCE_AREA = 4.0;

    /** Highest priority first. */
    public static final OrderedRegistry<SignatureProvider> PROVIDERS =
            OrderedRegistry.create("recon", "recon.signatures");

    private SignatureProviders() {
    }

    /**
     * @return the first provider's answer, or a size-derived default.
     */
    public static Signature of(Entity entity) {
        for (SignatureProvider provider : PROVIDERS.values()) {
            Signature signature = provider.of(entity);
            if (signature != null) {
                return signature;
            }
        }
        return fallback(entity);
    }

    /**
     * Frontal area against the reference, so an unregistered mob is detectable at a sensible range instead of being
     * invisible.
     */
    public static Signature fallback(Entity entity) {
        AABB box = entity.getBoundingBox();
        double area = box.getXsize() * box.getYsize();
        return Signature.radar((float) Math.max(0.01, area / REFERENCE_AREA));
    }
}
