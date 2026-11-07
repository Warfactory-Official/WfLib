package com.wf.wflib.ragdoll.api;

import com.wf.wflib.core.api.WFSystems;
import com.wf.wflib.ragdoll.RagdollServer;
import com.wf.wflib.ragdoll.RagdollStore;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

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

    /** Blast: body thrown along {@code velocity} (m/s), arms flung away, legs dragged behind, randomised per limb. */
    public static void blast(ServerPlayer player, Vec3 velocity) {
        if (WFSystems.enabled("ragdoll")) {
            RagdollServer.blast(player, velocity);
        }
    }
}
