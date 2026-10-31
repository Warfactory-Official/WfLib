package com.wf.wflib.drone;

import com.wf.wflib.drone.api.SimDrone;
import com.wf.wflib.drone.api.SimDroneManager;
import com.wf.wflib.recon.api.ContactClass;
import com.wf.wflib.recon.api.ReconSignatures;
import com.wf.wflib.recon.api.EmconState;
import com.wf.wflib.recon.api.SourceIds;
import com.wf.wflib.recon.api.TargetSink;
import com.wf.wflib.recon.api.TargetSource;
import com.wf.wflib.recon.api.TargetSnapshot;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.List;

/** Drones that are off the world, flying as records rather than entities. */
public final class SimDroneSource implements TargetSource {

    @Override
    public void collect(ServerLevel level, AABB volume, TargetSink sink) {
        List<SimDrone> drones = SimDroneManager.tier(level).view();
        for (int i = 0; i < drones.size(); i++) {
            SimDrone drone = drones.get(i);
            Vec3 pos = drone.pos;
            if (pos == null || !volume.contains(pos.x, drone.simY, pos.z)) {
                continue;
            }
            Vec3 velocity = drone.velocity == null ? Vec3.ZERO : drone.velocity;
            EmconState emcon = drone.program.isEmpty() ? EmconState.ACTIVE : EmconState.SILENT;
            sink.accept(new TargetSnapshot(SourceIds.of(drone.id), ContactClass.DRONE,
                    pos.x, drone.simY, pos.z,
                    velocity.x, velocity.y, velocity.z,
                    ReconSignatures.drone(), TargetSnapshot.NO_COUNTERMEASURES, emcon, 0L, false));
        }
    }
}
