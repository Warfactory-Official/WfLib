package com.wf.wflib.recon.api;

import net.minecraft.world.entity.Entity;
import org.jetbrains.annotations.Nullable;

/** How an entity's signature is worked out, for the entity source only. */
public interface SignatureProvider {

    /**
     * @return this entity's signature, or null to mean "not mine, try the next".
     */
    @Nullable
    Signature of(Entity entity);
}
