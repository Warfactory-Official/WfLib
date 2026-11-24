package com.wf.wflib.core.api;

import com.wf.wflib.physics.api.RegionHost;
import com.wf.wflib.physics.step.RigidStep;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

/**
 * Cross-region hit delivery: geometry resolved where detected, the mutation of the target (damage, knockback, ignite,
 * blast, armour state) runs on the thread that ticks it. No {@link RegionHost} => inline, one volatile read.
 *
 * <ul>
 *   <li>Owner = region owning the target's block position; unowned => main. Main caller => always routed.
 *   <li>Routed work runs at the owner's next tick start (latency <= 1 tick) in the target's ownership scope, skipped if the target was removed meanwhile.
 *   <li>Inside work routed for T, T counts as owned: unowned T drains on main, which owns nothing; no requeue loop.
 *   <li>Routed hit => caller must not read the outcome; {@link #defer} true => treat as accepted.
 * </ul>
 */
public final class HitRouter {

    private static final ThreadLocal<Entity[]> DELIVERING = ThreadLocal.withInitial(() -> new Entity[1]);

    private HitRouter() {
    }

    /** Caller thread does not own {@code target}: its mutation must be {@link #defer deferred}. */
    public static boolean foreign(Level level, Entity target) {
        RegionHost host = RigidStep.regionHost();
        return host != null && level instanceof ServerLevel server && !host.owns(server, target.blockPosition())
                && DELIVERING.get()[0] != target;
    }

    /** {@code work} queued to the owner of {@code target}: true. Inline-eligible (no host / owned): false, nothing run. */
    public static boolean defer(Level level, Entity target, Runnable work) {
        return defer(level, target, work, null);
    }

    /** As {@link #defer(Level, Entity, Runnable)}; {@code dropped} runs on the owner instead of {@code work} when {@code target} was removed meanwhile. */
    public static boolean defer(Level level, Entity target, Runnable work, @Nullable Runnable dropped) {
        RegionHost host = RigidStep.regionHost();
        if (host == null || !(level instanceof ServerLevel server) || DELIVERING.get()[0] == target) {
            return false;
        }
        return route(host, server, target.blockPosition(), () -> {
            if (!target.isRemoved()) {
                Entity[] current = DELIVERING.get();
                Entity outer = current[0];
                current[0] = target;
                try {
                    work.run();
                } finally {
                    current[0] = outer;
                }
            } else if (dropped != null) {
                dropped.run();
            }
        });
    }

    /** Runs {@code work} inline when the caller owns {@code target}, else on its owner. */
    public static void apply(Level level, Entity target, Runnable work) {
        if (!defer(level, target, work)) {
            work.run();
        }
    }

    static boolean route(RegionHost host, ServerLevel level, BlockPos pos, Runnable work) {
        if (host.owns(level, pos)) {
            return false;
        }
        host.runOnOwner(level, pos, work);
        return true;
    }
}
