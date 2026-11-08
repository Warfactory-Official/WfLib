package com.wf.wflib.ragdoll.api.client;

import com.wf.wflib.ragdoll.client.RagdollClient;
import net.minecraft.world.entity.Entity;
import org.jetbrains.annotations.Nullable;

/** What a client needs to draw things that ride a ragdolled body. */
public final class RagdollView {
    private RagdollView() {
    }

    /**
     * {centre, face direction, crown direction} of a ragdolled entity's head at render time, world coordinates, or null
     * when it is not ragdolled: something mounted on the head follows the bone, whichever way it is turned.
     */
    public static @Nullable double[][] head(Entity entity, float partialTick) {
        return RagdollClient.headFrame(entity, partialTick);
    }
}
