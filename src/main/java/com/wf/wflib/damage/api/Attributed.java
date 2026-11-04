package com.wf.wflib.damage.api;

import org.jetbrains.annotations.Nullable;

import java.util.UUID;

/** A {@link net.minecraft.world.damagesource.DamageSource} that names the faction responsible; read via {@link Attribution}. */
public interface Attributed {

    @Nullable
    UUID attributedFaction();
}
