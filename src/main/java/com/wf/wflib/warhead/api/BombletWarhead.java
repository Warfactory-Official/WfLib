package com.wf.wflib.warhead.api;

import com.wf.wflib.explosion.api.ExplosionNukeGeneric;
import com.wf.wflib.fire.api.FireLingeringEntity;
import com.wf.wflib.explosion.api.ExplosionSmallCreator;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import com.wf.wflib.core.api.WFLib;

public final class BombletWarhead {

    public static final ResourceLocation ID = ResourceLocation.fromNamespaceAndPath(WFLib.MODID, "bomblet");
    public static final ResourceLocation FIRE_ID = ResourceLocation.fromNamespaceAndPath(WFLib.MODID, "bomblet_fire");

    public static final float BLAST_RADIUS = 4.0f;
    public static final float MAX_DAMAGE = 40.0f;

    public static final Warheads.Detonation STANDARD = (source, pos) -> {
        Level level = source.level();
        if (level.isClientSide) {
            return;
        }
        ExplosionNukeGeneric.dealDamage(level, pos.x, pos.y, pos.z, BLAST_RADIUS, MAX_DAMAGE);
        ExplosionSmallCreator.composeEffect(level, pos.x, pos.y, pos.z, 3, 1.0f, 0.6f);
    };

    public static final Warheads.Detonation FIRE = (source, pos) -> {
        Level level = source.level();
        if (level.isClientSide) {
            return;
        }
        ExplosionNukeGeneric.dealDamage(level, pos.x, pos.y, pos.z, BLAST_RADIUS, MAX_DAMAGE * 0.5f);
        FireLingeringEntity.spawn(level, pos.x, pos.y, pos.z, 3.0f, 2.0f, 120, com.wf.wflib.fire.api.FireType.NORMAL);
        ExplosionSmallCreator.composeEffect(level, pos.x, pos.y, pos.z, 3, 1.0f, 0.6f);
    };

    private BombletWarhead() {
    }
}
