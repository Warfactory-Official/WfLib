package com.wf.wflib.drone;

import com.wf.wflib.cam.api.CameraSpec;
import com.wf.wflib.cam.api.FeedHost;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import com.wf.wflib.drone.api.ControllableDrone;
import com.wf.wflib.drone.api.DroneEntity;
import com.wf.wflib.drone.api.DroneRecall;
import com.wf.wflib.drone.api.DroneTracker;

/** Drones as camera hosts. */
final class DroneFeedHost implements FeedHost {
    @Override
    public boolean hosts(Entity entity) {
        return entity instanceof DroneEntity;
    }

    @Nullable
    @Override
    public CameraSpec spec(Entity entity) {
        return ((DroneEntity) entity).cameraSpec();
    }

    @Override
    public List<Entity> near(ServerLevel level, double x, double y, double z, double range) {
        double rangeSqr = range * range;
        List<Entity> found = new ArrayList<>();
        for (DroneEntity drone : DroneTracker.drones(level)) {
            if (drone.isAlive() && drone.cameraSpec() != null && drone.distanceToSqr(x, y, z) <= rangeSqr) {
                found.add(drone);
            }
        }
        return found;
    }

    @Override
    public boolean recall(ServerLevel level, UUID id) {
        return DroneRecall.recall(level, id);
    }

    @Override
    public boolean classified(ServerLevel level, Entity entity) {
        ControllableDrone body = DroneTracker.byId(level, entity.getUUID());
        return body != null && body.orders().isClassified();
    }
}
