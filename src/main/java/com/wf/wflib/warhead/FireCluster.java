package com.wf.wflib.warhead;

import com.wf.wflib.warhead.api.BombletEntity;
import com.wf.wflib.explosion.api.ExplosionCreator;
import com.wf.wflib.explosion.api.FragmentationUtil;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.projectile.SmallFireball;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import com.wf.wflib.warhead.api.BombletWarhead;
import com.wf.wflib.warhead.api.WarheadCarrier;
import com.wf.wflib.core.api.WFLib;

public final class FireCluster {

    public static final ResourceLocation ID = ResourceLocation.fromNamespaceAndPath(WFLib.MODID, "fire_cluster");
    private static final double HALF_ANGLE = Math.toRadians(60.0);
    private static final double SPEED = 1.2;
    private static final double SPEED_JITTER = 0.4;

    private FireCluster() {
    }

    public static void detonate(WarheadCarrier source, Vec3 pos) {
        Level level = source.level();
        if (level.isClientSide) {
            return;
        }
        int count = source.getFragmentCount();
        if (WarheadConfig.fireBombletsAsFireballs()) {
            spawnFireballs(level, pos, count);
        } else {
            FragmentationUtil.cone(level, pos, new Vec3(0.0, -1.0, 0.0), HALF_ANGLE, count,
                    SPEED, SPEED_JITTER, BombletWarhead.FIRE_ID, BombletWarhead.FIRE, BombletEntity.DEFAULT_FUSE, null);
        }
        ExplosionCreator.composeEffectSmall(level, pos.x, pos.y, pos.z);
    }

    private static void spawnFireballs(Level level, Vec3 pos, int count) {
        RandomSource rng = level.random;
        Vec3 axis = new Vec3(0.0, -1.0, 0.0);
        for (int i = 0; i < count; i++) {
            Vec3 dir = FragmentationUtil.randomConeVector(rng, axis, HALF_ANGLE);
            SmallFireball fireball = new SmallFireball(level, pos.x, pos.y, pos.z, new Vec3(dir.x, dir.y, dir.z));
            fireball.setPos(pos.x, pos.y, pos.z);
            level.addFreshEntity(fireball);
        }
    }
}
