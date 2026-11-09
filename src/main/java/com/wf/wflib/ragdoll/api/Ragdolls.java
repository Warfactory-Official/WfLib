package com.wf.wflib.ragdoll.api;

import com.wf.wflib.core.api.WFSystems;
import com.wf.wflib.ragdoll.RagdollServer;
import com.wf.wflib.ragdoll.RagdollStore;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/**
 * Server-simulated player ragdoll; bodies = the AHF rig slots, drawn on the jointed body. The player entity follows the
 * torso (client moves ignored) until released, then stands where the body lies.
 */
public final class Ragdolls {

    private Ragdolls() {
    }

    /** On: starts from the current rig pose (waits while riding). Off: stand-up blend. No-op with the system off. */
    public static void set(ServerPlayer player, boolean on) {
        if (WFSystems.enabled("ragdoll")) {
            RagdollServer.set(player, on);
        }
    }

    /** Body owned by physics (including the stand-up blend). */
    public static boolean active(Entity entity) {
        return RagdollStore.of(entity.level().isClientSide).track(entity.getId()) != null;
    }

    /**
     * Linear impulse, N s, on the body nearest {@code at}; before the ragdoll starts it is held a moment, so the hit
     * that knocks a player out also throws them.
     */
    public static void impulse(ServerPlayer player, Vec3 at, Vec3 impulse) {
        if (WFSystems.enabled("ragdoll")) {
            RagdollServer.impulse(player, at, impulse);
        }
    }

    /** Values in {@link #state}. */
    public static final int STATE = RagdollServer.STATE;

    /**
     * {@code corpse} (inert: never stands, no fall damage) takes {@code from}'s body: the running sim with its pose
     * when ragdolled, else one started from {@code from}'s rig with its held hits. Call before spawning {@code corpse};
     * it is placed on the body.
     */
    public static void handOff(LivingEntity from, LivingEntity corpse) {
        RagdollServer.handOff(from, corpse);
    }

    /** Inert body from a saved {@link #state}. */
    public static void restore(LivingEntity corpse, double[] state) {
        RagdollServer.restore(corpse, state);
    }

    /** {@link #STATE} values to persist; null when not simulating. */
    public static @Nullable double[] state(LivingEntity entity) {
        return RagdollServer.state(entity);
    }

    /**
     * Server: {@code body} lifted onto {@code carrier}'s shoulder and held there; null lets go (momentum kept). Refused
     * (false): not a running body, already carried, carrier ragdolled. Dropped by the ragdoll itself when the carrier
     * dies, leaves, ragdolls or slips away, and when the body stands up or ends: poll {@link #carrier}.
     */
    public static boolean carry(LivingEntity body, @Nullable LivingEntity carrier) {
        return WFSystems.enabled("ragdoll") && RagdollServer.carry(body, carrier);
    }

    /** Server. */
    public static @Nullable LivingEntity carrier(Entity body) {
        return WFSystems.enabled("ragdoll") ? RagdollServer.carrier(body) : null;
    }

    /** Explosion throw / round shove, as a ragdolled player gets. */
    public static void hit(LivingEntity entity, DamageSource source, float damage) {
        RagdollServer.react(entity, source, damage);
    }

    /** Blast: body thrown along {@code velocity} (m/s), arms flung away, legs dragged behind, randomised per limb. */
    public static void blast(ServerPlayer player, Vec3 velocity) {
        if (WFSystems.enabled("ragdoll")) {
            RagdollServer.blast(player, velocity);
        }
    }
}
