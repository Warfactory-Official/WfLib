package com.wf.wflib.cam;

import com.wf.wflib.cam.api.CameraSpec;
import com.wf.wflib.cam.api.FeedHost;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Loops over {@link FeedHost#HOSTS}. */
public final class FeedHosts {
    private FeedHosts() {
    }

    public static boolean hosts(Entity entity) {
        for (FeedHost host : FeedHost.HOSTS.values()) {
            if (host.hosts(entity)) {
                return true;
            }
        }
        return false;
    }

    @Nullable
    public static CameraSpec spec(Entity entity) {
        for (FeedHost host : FeedHost.HOSTS.values()) {
            if (host.hosts(entity)) {
                return host.spec(entity);
            }
        }
        return null;
    }

    public static List<Entity> near(ServerLevel level, double x, double y, double z, double range) {
        List<Entity> out = new ArrayList<>();
        for (FeedHost host : FeedHost.HOSTS.values()) {
            out.addAll(host.near(level, x, y, z, range));
        }
        out.sort(Comparator.comparingDouble(e -> e.distanceToSqr(x, y, z)));
        return out;
    }

    public static boolean recall(ServerLevel level, UUID id) {
        for (FeedHost host : FeedHost.HOSTS.values()) {
            if (host.recall(level, id)) {
                return true;
            }
        }
        return false;
    }

    public static boolean classified(ServerLevel level, Entity entity) {
        for (FeedHost host : FeedHost.HOSTS.values()) {
            if (host.classified(level, entity)) {
                return true;
            }
        }
        return false;
    }

    /** The host the player is looking at within {@code reach}, or null. */
    @Nullable
    public static Entity lookedAt(Level level, Entity viewer, double reach) {
        Vec3 start = viewer.getEyePosition();
        Vec3 end = start.add(viewer.getLookAngle().scale(reach));
        AABB box = new AABB(start, end).inflate(1.0);
        Entity best = null;
        double bestDist = Double.MAX_VALUE;
        for (Entity entity : level.getEntities(viewer, box, FeedHosts::hosts)) {
            Optional<Vec3> hit = entity.getBoundingBox().inflate(0.3).clip(start, end);
            if (hit.isEmpty()) {
                continue;
            }
            double dist = start.distanceToSqr(hit.get());
            if (dist < bestDist) {
                bestDist = dist;
                best = entity;
            }
        }
        return best;
    }
}
