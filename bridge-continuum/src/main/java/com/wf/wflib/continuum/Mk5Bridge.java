package com.wf.wflib.continuum;

import com.hbm.entity.logic.EntityNukeExplosionMK5;
import com.hbm.interfaces.IExplosionRay;
import com.wf.wflib.explosion.nuke.BombForkJoinPool;
import com.wf.wflib.explosion.nuke.ExplosionNukeRayParallelized;
import com.wf.wflib.explosion.nuke.NukeConfig;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;

import java.util.UUID;
import java.util.concurrent.ForkJoinPool;

/**
 * Nukes (docs, 5.2): Continuum's bombs run WFLib's parallel MK5 ray. Continuum asks {@link EntityNukeExplosionMK5#RAY} (the bridge's upstream seam) for the crater
 * ray of every blast and this answers with {@link WflibRay}, which adapts WFLib's ray to Continuum's five-method {@link IExplosionRay}. The entity, its fallout, its
 * satellite report and its events stay Continuum's.
 */
final class Mk5Bridge {
    private Mk5Bridge() {
    }

    static void install() {
        EntityNukeExplosionMK5.RAY.register((level, x, y, z, strength, radius) -> {
            if (!(level instanceof ServerLevel server)) return null;
            return new WflibRay(server, x, y, z, strength, radius);
        });
        ContinuumBridge.LOG.info("Nukes: Continuum's blasts run WFLib's parallel ray");
    }

    /** WFLib's ray behind Continuum's interface, with the worker pool the ray needs acquired for its life. */
    static final class WflibRay implements IExplosionRay, BombForkJoinPool.IJobCancellable {
        private final ServerLevel level;
        private final ExplosionNukeRayParallelized ray;
        private ForkJoinPool pool;

        WflibRay(ServerLevel level, int x, int y, int z, int strength, int radius) {
            this.level = level;
            this.pool = BombForkJoinPool.acquire();
            BombForkJoinPool.register(pool, level.dimension(), this);
            ExplosionNukeRayParallelized made;
            try {
                made = new ExplosionNukeRayParallelized(level, x, y, z, strength, radius, NukeConfig.explosionAlgorithm);
            } catch (RuntimeException | Error e) {
                release();
                throw e;
            }
            this.ray = made;
        }

        @Override
        public void update(int processTimeMs) {
            if (!ray.isComplete() && !ray.hasFailed()) ray.update(processTimeMs);
            if (ray.isComplete() || ray.hasFailed()) {
                if (ray.hasFailed()) ContinuumBridge.LOG.error("WFLib's MK5 ray failed; the blast ends here");
                release();
            }
        }

        @Override
        public void cancel() {
            ray.cancel();
            release();
        }

        @Override
        public boolean isComplete() {
            return ray.isComplete() || ray.hasFailed();
        }

        @Override
        public boolean isContained() {
            return true;
        }

        @Override
        public void setDetonator(UUID detonator) {
        }

        @Override
        public void cancelJob() {
            if (level.getServer().isSameThread()) cancel();
            else level.getServer().execute(this::cancel);
        }

        private void release() {
            if (pool == null) return;
            BombForkJoinPool.unregister(pool, level.dimension(), this);
            BombForkJoinPool.release(pool);
            pool = null;
        }
    }
}
