package com.wf.wflib.round;

import com.wf.wflib.kinetic.KineticPreset;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.Event;

/**
 * A round flew within {@code rounds.nearMissRadius} of a player's eyes without striking them, on its way past (closest
 * approach inside the swept segment). Game bus, server; once per segment, a long flight can repeat it.
 */
public final class RoundNearMissEvent extends Event {

    private final ServerLevel level;
    private final ServerPlayer player;
    private final KineticPreset preset;
    private final double distance;
    private final Vec3 velocity;

    public RoundNearMissEvent(ServerLevel level, ServerPlayer player, KineticPreset preset, double distance,
                              Vec3 velocity) {
        this.level = level;
        this.player = player;
        this.preset = preset;
        this.distance = distance;
        this.velocity = velocity;
    }

    public ServerLevel level() {
        return this.level;
    }

    public ServerPlayer player() {
        return this.player;
    }

    public KineticPreset preset() {
        return this.preset;
    }

    /** Eyes to the round's path, blocks. */
    public double distance() {
        return this.distance;
    }

    /** Blocks per tick. */
    public Vec3 velocity() {
        return this.velocity;
    }
}
