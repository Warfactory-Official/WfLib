package com.wf.wflib.ragdoll.client;

import com.wf.wflib.ragdoll.RagdollPackets;
import com.wf.wflib.ragdoll.RagdollStore;
import com.wf.wflib.ragdoll.sim.RagdollPose;
import com.wf.wflib.ragdoll.sim.Skeleton;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.Entity;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import org.jetbrains.annotations.Nullable;

import java.util.Iterator;
import java.util.Map;

public final class RagdollClient {

    private RagdollClient() {
    }

    public static void pose(RagdollPackets.Pose packet) {
        RagdollStore.Track track = RagdollStore.of(true).open(packet.id());
        packet.apply(track.push(packet.stamp()));
    }

    public static void stop(int id, int blend) {
        RagdollStore store = RagdollStore.of(true);
        if (blend <= 0) {
            store.close(id);
        } else {
            RagdollStore.Track track = store.track(id);
            if (track != null) {
                track.release(blend);
            }
        }
    }

    public static void onTick(ClientTickEvent.Pre event) {
        ClientLevel level = Minecraft.getInstance().level;
        RagdollStore store = RagdollStore.of(true);
        if (level == null) {
            return;
        }
        LocalPlayer self = Minecraft.getInstance().player;
        for (Iterator<Map.Entry<Integer, RagdollStore.Track>> it = store.all().iterator(); it.hasNext(); ) {
            Map.Entry<Integer, RagdollStore.Track> e = it.next();
            RagdollStore.Track track = e.getValue();
            track.advance();
            if (track.blendDone() || level.getEntity(e.getKey()) == null) {
                it.remove();
                continue;
            }
            if (self != null && e.getKey() == self.getId() && !track.released()) {
                RagdollPose pose = track.sample(1.0F);
                self.setPos(pose.x, pose.y - pose.footDrop, pose.z);
                self.setDeltaMovement(net.minecraft.world.phys.Vec3.ZERO);
            }
        }
    }

    public static void onLogout(ClientPlayerNetworkEvent.LoggingOut event) {
        RagdollStore.of(true).clear();
    }

    /**
     * {centre, face direction, crown direction} of a ragdolled entity's head at render time, in world coordinates, or
     * null when it is not ragdolled: what is mounted on the head follows the bone, whichever way it is turned.
     */
    public static @Nullable double[][] headFrame(Entity entity, float pt) {
        RagdollStore.Track track = RagdollStore.of(true).track(entity.getId());
        if (track == null || track.empty() || track.released()) {
            return null;
        }
        double[][] out = new double[3][3];
        track.sample(pt).headFrame(com.wf.wflib.ragdoll.RagdollServer.skeleton(), out[0], out[1], out[2]);
        return out;
    }

    /** Head / chest centre for the camera at render time; null => not ragdolled. */
    public static @Nullable double[] view(Entity entity, float pt, boolean head) {
        RagdollStore.Track track = RagdollStore.of(true).track(entity.getId());
        if (track == null || track.empty() || track.released()) {
            return null;
        }
        double[] out = new double[3];
        track.sample(pt).bodyCentre(com.wf.wflib.ragdoll.RagdollServer.skeleton(), head ? Skeleton.HEAD : Skeleton.CHEST, out);
        return out;
    }
}
