package com.wf.wflib.drone;

import com.wf.wflib.drone.api.DroneEntity;
import com.wf.wflib.recon.api.ContactClass;
import com.wf.wflib.recon.api.EmconState;
import com.wf.wflib.recon.api.SignatureProviders;
import com.wf.wflib.recon.api.TargetSink;
import com.wf.wflib.recon.api.TargetSource;
import com.wf.wflib.recon.api.EntityTargetSource;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.AABB;

import java.util.List;

/** Drones that are loaded entities. */
final class DroneTargetSource implements TargetSource {
    @Override
    public void collect(ServerLevel level, AABB volume, TargetSink sink) {
        List<DroneEntity> drones = level.getEntitiesOfClass(DroneEntity.class, volume, e -> !e.isRemoved());
        for (int i = 0; i < drones.size(); i++) {
            DroneEntity drone = drones.get(i);
            // A drone flying its own program needs no link, so it is dark; one being flown is talking.
            EmconState emcon = drone.orders().getProgram().isEmpty() ? EmconState.ACTIVE : EmconState.SILENT;
            sink.accept(EntityTargetSource.snapshot(drone, ContactClass.DRONE, SignatureProviders.of(drone), emcon, 0L,
                    false));
        }
    }
}
