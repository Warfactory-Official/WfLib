package com.wf.wflib.entity;

import com.mojang.logging.LogUtils;
import com.wf.wflib.ModEntities;
import com.wf.wflib.aef.nuke.BombForkJoinPool;
import com.wf.wflib.aef.nuke.ExplosionNukeGeneric;
import com.wf.wflib.aef.nuke.ExplosionNukeRayParallelized;
import com.wf.wflib.aef.nuke.NukeConfig;
import com.wf.wflib.recon.event.SeismicEvents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;
import org.slf4j.Logger;

import java.util.concurrent.ForkJoinPool;

// NTM-next: fallout rain, radiation sweep, detonation advancement not ported (no wflib radiation system).
public class EntityNukeExplosionMK5 extends EntityExplosionChunkLoading implements BombForkJoinPool.IJobCancellable {

    private static final Logger LOGGER = LogUtils.getLogger();

    private int strength;
    private int radius;
    private int algorithm;

    private ExplosionNukeRayParallelized explosion;
    private boolean initialized = false;
    private ForkJoinPool generationPool;
    private long explosionStart;

    public EntityNukeExplosionMK5(EntityType<? extends EntityNukeExplosionMK5> type, Level level) {
        super(type, level);
    }

    public static EntityNukeExplosionMK5 statFac(Level level, int r, double x, double y, double z) {
        if (r == 0) r = 25;
        EntityNukeExplosionMK5 mk5 = new EntityNukeExplosionMK5(ModEntities.NUKE_EXPLOSION.get(), level);
        mk5.strength = 2 * r;
        mk5.radius = r;
        mk5.algorithm = NukeConfig.explosionAlgorithm;
        mk5.setPos(x, y, z);
        return mk5;
    }

    public int getRadius() {
        return radius;
    }

    @Override
    public void tick() {
        super.tick();
        if (level().isClientSide()) {
            discard();
            return;
        }
        if (strength == 0) {
            discard();
            return;
        }

        ExplosionNukeGeneric.dealDamage(level(), getX(), getY(), getZ(), radius * 2.0D);

        if (!initialized) {
            ServerLevel server = (ServerLevel) level();
            explosionStart = System.currentTimeMillis();
            SeismicEvents.report(server, getX(), getY(), getZ(), strength);
            generationPool = BombForkJoinPool.acquire();
            BombForkJoinPool.register(generationPool, server.dimension(), this);
            try {
                explosion = new ExplosionNukeRayParallelized(server, getX(), getY(), getZ(), strength, radius, algorithm);
            } catch (RuntimeException | Error cause) {
                discard();
                throw cause;
            }
            initialized = true;
        }

        if (explosion.hasFailed()) {
            discard();
        } else if (!explosion.isComplete()) {
            explosion.update(NukeConfig.mk5);
        } else {
            LOGGER.info("[NUKE] done: radius {}, {} ms", radius, System.currentTimeMillis() - explosionStart);
            discard();
        }
    }

    @Override
    public void onAddedToLevel() {
        super.onAddedToLevel();
        if (!level().isClientSide()) anchor(0);
    }

    @Override
    public void remove(RemovalReason reason) {
        try {
            if (explosion != null) explosion.cancel();
        } finally {
            if (generationPool != null) {
                BombForkJoinPool.unregister(generationPool, level().dimension(), this);
                BombForkJoinPool.release(generationPool);
                generationPool = null;
            }
            super.remove(reason);
        }
    }

    @Override
    public void cancelJob() {
        ServerLevel server = (ServerLevel) level();
        if (server.getServer().isSameThread()) discard();
        else server.getServer().execute(this::discard);
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        radius = tag.getInt("radius");
        strength = tag.getInt("strength");
        algorithm = tag.getInt("algorithm");
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putInt("radius", radius);
        tag.putInt("strength", strength);
        tag.putInt("algorithm", algorithm);
    }
}
