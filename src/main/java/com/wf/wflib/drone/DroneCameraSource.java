package com.wf.wflib.drone;

import com.wf.wflib.cam.api.CameraFeed;
import com.wf.wflib.cam.api.CameraSource;
import com.wf.wflib.cam.api.CameraSpec;
import net.minecraft.world.phys.Vec3;
import com.wf.wflib.drone.api.DroneEntity;

/** A drone's camera. */
public record DroneCameraSource(DroneEntity drone, CameraSpec spec) implements CameraSource {

    @Override
    public Vec3 eye() {
        return this.drone.lensPosition(1.0f);
    }

    @Override
    public float restYaw() {
        return this.drone.getYRot();
    }

    @Override
    public float restPitch() {
        return 0.0f;
    }

    @Override
    public float battery() {
        return this.drone.battery().percent() / 100.0f;
    }

    @Override
    public float speed() {
        return (float) this.drone.getDeltaMovement().length() * 20.0f;
    }

    @Override
    public void bill(int fe, int ticks) {
        this.drone.battery().drain(fe * ticks / 20.0);
    }

    @Override
    public byte kind() {
        return CameraFeed.DRONE;
    }
}
