package com.wf.wflib.physics.api;

import com.wf.wflib.physics.step.RigidStep;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerLevel;

/** Per-level rigid step: bodies registered during their tick are stepped together at {@code TickOrder.PHYSICS}. */
public final class PhysicsStep {

    private PhysicsStep() {
    }

    /** Any thread, during the body's tick. */
    public static void register(PhysicsBody body) {
        RigidStep.register(body);
    }

    /** Mod init, once: per-region stepping when {@link PhysicsSettings#regionStep} resolves on. */
    public static void installRegionHost(RegionHost host) {
        RigidStep.install(host);
    }

    /** Host, region thread after its entity phase: steps the bodies registered in {@code region} this tick. */
    public static void regionTickEnd(ServerLevel level, int region) {
        RigidStep.stepPartition(level, region);
    }

    /** {@code on} / {@code off} / {@code off (no host)}. */
    public static String regionMode() {
        return RigidStep.regionMode();
    }

    /** Client tick thread: one predicted body, no neighbours, live world. */
    public static StepResult predict(PhysicsBody body) {
        return RigidStep.predict(body);
    }

    /** Warm-start caches (prediction snapshot). */
    public static void writeWarm(PhysicsBody body, FriendlyByteBuf buf) {
        RigidStep.writeWarm(body, buf);
    }

    public static void readWarm(PhysicsBody body, FriendlyByteBuf buf) {
        RigidStep.readWarm(body, buf);
    }

    /** Bench counters on/off. */
    public static void setStats(boolean on) {
        RigidStep.setStats(on);
    }

    /** Per stepped tick since the last drain: step us, islands, bodies, contacts; region mode. */
    public static String drainStats() {
        return RigidStep.drainStats();
    }
}
