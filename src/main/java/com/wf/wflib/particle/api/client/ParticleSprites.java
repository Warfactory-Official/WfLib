package com.wf.wflib.particle.api.client;

import net.minecraft.client.particle.SpriteSet;

import java.util.concurrent.atomic.AtomicReference;

/** Null until the matching particle's Provider is constructed. */
public final class ParticleSprites {
    public static final AtomicReference<SpriteSet> EXPLOSION_SMALL = new AtomicReference<>();
    public static final AtomicReference<SpriteSet> ROCKET_FLAME = new AtomicReference<>();
    public static final AtomicReference<SpriteSet> ASH = new AtomicReference<>();
    public static final AtomicReference<SpriteSet> MIST = new AtomicReference<>();

    private ParticleSprites() {
    }
}
