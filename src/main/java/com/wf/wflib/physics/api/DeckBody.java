package com.wf.wflib.physics.api;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.phys.AABB;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * Stepped body whose deck carries entities ({@link VehicleParenting}) and other bodies ({@link CarrierDecks},
 * {@link CarrierLink}). Deck usable iff {@link #collides()} and {@link #mainPiece()} != null. Implementations are entities.
 */
public interface DeckBody extends DeckCarrier, PhysicsBody {

    default Entity entity() {
        return (Entity) this;
    }

    /** Entities parented to the deck; mutated in place by {@link VehicleParenting}. */
    List<Entity> deckRiders();

    boolean collides();

    SoundType deckSoundType();

    AABB getBoundingBox();

    /** Non-body entity pushed against the hull: resolve it out, or onto the deck. */
    void support(Entity entity);

    /** Carrier this body is mounted on; never its own deck's floor. */
    @Nullable
    DeckCarrier mountedOn();
}
