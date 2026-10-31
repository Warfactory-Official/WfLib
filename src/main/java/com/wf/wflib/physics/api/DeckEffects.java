package com.wf.wflib.physics.api;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.gameevent.GameEvent;

/**
 * Fills in surface properties and footstep sounds that hull air does not provide.
 * Motion measured in the vehicle frame: a rider standing still on a moving ship has not taken a step.
 * Step detection runs with clip; emission runs on tick thread only.
 */
public final class DeckEffects {

    /** Vanilla fires footsteps every 1.67 blocks. */
    private static final double STEP_SCALE = 0.6;
    private static final double STEP_INTERVAL = 1.0;

    /** Vanilla's fall sound distance, from causeFallDamage. */
    private static final float FALL_SOUND_DISTANCE = 3.0F;

    private DeckEffects() {}

    static void walked(Entity entity, DeckAttachment attachment, double localDx, double localDz) {
        if (localDx != 0 || localDz != 0) {
            attachment.addWalk(Math.sqrt(localDx * localDx + localDz * localDz) * STEP_SCALE);
        }
        // Pre-landing value: vanilla clears fallDistance in checkFallDamage, which runs after
        // collide returns; this call is inside collide.
        if (entity.fallDistance > FALL_SOUND_DISTANCE) {
            attachment.queueFallSound();
        }
    }

    static void emit(Entity entity, DeckBody vehicle, DeckAttachment attachment) {
        // Approximates vanilla's protected MovementEmission gate. Items, boats, projectiles have no
        // feet; everything that walks is a LivingEntity.
        if (!(entity instanceof LivingEntity) || entity.isSilent()) {
            attachment.clearPendingSounds();
            return;
        }
        SoundType surface = vehicle.deckSoundType();
        if (attachment.takeFallSound()) {
            entity.playSound(surface.getFallSound(),
                    surface.getVolume() * 0.5F, surface.getPitch() * 0.75F);
        }
        if (attachment.takeStep(STEP_INTERVAL)) {
            entity.playSound(surface.getStepSound(), surface.getVolume() * 0.15F, surface.getPitch());
            // Server-only no-op on client; restores the vibration a hull was swallowing.
            entity.gameEvent(GameEvent.STEP);
        }
    }

}
