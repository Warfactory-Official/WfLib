package com.wf.wflib.photo.api;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.Event;

/**
 * Posted on the server when a camera's shutter fires, before any image exists. The pose is what the server holds
 * (the client renders the picture from exactly this eye and orientation), so objective checks such as "was the target
 * in frame" can trust it. Not cancellable: the shot is already paid for.
 */
public class PhotoTakenEvent extends Event {
    private final ServerPlayer player;
    private final ServerLevel level;
    private final Vec3 eye;
    private final float yaw;
    private final float pitch;
    private final float fov;

    public PhotoTakenEvent(ServerPlayer player, ServerLevel level, Vec3 eye, float yaw, float pitch, float fov) {
        this.player = player;
        this.level = level;
        this.eye = eye;
        this.yaw = yaw;
        this.pitch = pitch;
        this.fov = fov;
    }

    public ServerPlayer player() {
        return player;
    }

    public ServerLevel level() {
        return level;
    }

    public Vec3 eye() {
        return eye;
    }

    /** Vanilla convention, degrees. */
    public float yaw() {
        return yaw;
    }

    /** Positive = looking down, degrees. */
    public float pitch() {
        return pitch;
    }

    /** Vertical field of view of the picture, degrees. */
    public float fov() {
        return fov;
    }
}
