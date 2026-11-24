package com.wf.wflib.round.api;

import com.wf.wflib.core.api.HitRouter;
import com.wf.wflib.damage.api.event.ProjectileStrikeEvent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * Strike on a foreign-region target: armour answer computed on the target's owner, answer carried back to the striker
 * ({@code null} = target removed before delivery: striker flies on unhurt). Striker parks meanwhile; <= 2 ticks.
 */
public final class StrikeRelay {

    /** Ticks a parked striker waits for its answer before it is lost at the hit point. */
    public static final int PARK_TICKS = 40;

    private StrikeRelay() {
    }

    /** Non-terminal answer: striker resumes at {@code at} moving {@code velocity}; {@code glance} => ricochet bookkeeping around {@code normal}. */
    public record Next(boolean glance, Vec3 at, Vec3 velocity, @Nullable Vec3 normal) {
    }

    /** RICOCHET => reflected, lifted {@link Ricochet#OFF} along the surface normal; THROUGH => exit velocity; PROCEED/PASS => unchanged. */
    public static Next next(ProjectileStrikeEvent answer, Vec3 hitAt, Vec3 velocity) {
        switch (answer.outcome()) {
            case RICOCHET -> {
                Vec3 out = answer.ricochet();
                Vec3 normal = out.normalize().subtract(velocity.normalize()).normalize();
                return new Next(true, hitAt.add(normal.scale(Ricochet.OFF)), out, normal);
            }
            case THROUGH -> {
                return new Next(false, hitAt, answer.through(), null);
            }
            default -> {
                return new Next(false, hitAt, velocity, null);
            }
        }
    }

    public static <R> Runnable work(Supplier<R> compute, Consumer<R> answer) {
        return () -> answer.accept(compute.get());
    }

    /** {@code compute} on {@code target}'s owner, then {@code answer} there. false: caller owns it, nothing queued. */
    public static boolean start(ServerLevel level, Entity target, Supplier<ProjectileStrikeEvent> compute,
                         Consumer<ProjectileStrikeEvent> answer) {
        return HitRouter.defer(level, target, work(compute, answer), () -> answer.accept(null));
    }
}
