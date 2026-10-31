package com.wf.wflib.physics.api;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

/**
 * Something a body can be mounted on: anything stating a pivot + rotation each tick via {@link #deckSnapshot()}
 * (hull, flatcar, lift). Riders on its deck: {@link DeckBody}.
 */
public interface DeckCarrier {

    DeckSnapshot deckSnapshot();

    /** Origin to snapshot pivot; mount offsets are stated from the origin. */
    Vec3 carrierCenterOffset();

    Level level();

    boolean isRemoved();

    /** Null while present. Unloaded => its mounts wait rather than drop. */
    @Nullable
    Entity.RemovalReason getRemovalReason();

    /** Chunk of this position is where a waiting mount looks for the carrier. */
    Vec3 position();

    int getId();

    /** Survives reload; a mount records and resolves the carrier by it. */
    UUID getUUID();

    // Server-reported pose, not interpolated: a mounted body's offset is derived from these on
    // both entities, since interpolated poses bake in different lags on the two.

    double lerpTargetX();

    double lerpTargetY();

    double lerpTargetZ();

    float lerpTargetXRot();

    float lerpTargetYRot();

    float lerpTargetZRot();
}
