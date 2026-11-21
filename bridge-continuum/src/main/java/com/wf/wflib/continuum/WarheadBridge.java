package com.wf.wflib.continuum;

import com.hbm.config.BombConfig;
import com.hbm.entity.effect.EntityNukeTorex;
import com.hbm.entity.logic.EntityNukeExplosionMK5;
import com.hbm.explosion.ExplosionLarge;
import com.wf.wflib.warhead.api.WarheadCarrier;
import com.wf.wflib.warhead.api.Warheads;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/**
 * Continuum's blasts as WFLib warheads (docs, 3.6), so a WFLib missile, round or drone can carry them: {@code wflib_continuum:he} (Continuum's smoking blast with
 * cloud, rubble and shrapnel, size from the carrier), {@code nuclear} (the missile-sized MK5 blast with its fallout and torex, at {@code BombConfig.missileRadius}) and
 * {@code nuclear_small} (at the Fat Man radius). Attribution is the carrier's exploder.
 */
final class WarheadBridge {
    static final ResourceLocation HE = id("he"), NUCLEAR = id("nuclear"), NUCLEAR_SMALL = id("nuclear_small");

    private WarheadBridge() {
    }

    private static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(ContinuumBridge.ID, path);
    }

    static void install() {
        Warheads.register(HE, (source, pos) -> {
            Level level = source.level();
            if (level.isClientSide) return;
            ExplosionLarge.explode(level, source.exploder(), pos.x, pos.y, pos.z, source.blastSize() > 0 ? source.blastSize() : 15.0F, true, true, true);
        });
        Warheads.register(NUCLEAR, (source, pos) -> nuke(source, pos, BombConfig.missileRadius));
        Warheads.register(NUCLEAR_SMALL, (source, pos) -> nuke(source, pos, BombConfig.fatmanRadius));
        ContinuumBridge.LOG.info("Warheads: wflib_continuum:he, nuclear and nuclear_small are registered");
    }

    private static void nuke(WarheadCarrier source, Vec3 pos, int radius) {
        Level level = source.level();
        if (level.isClientSide) return;
        EntityNukeExplosionMK5 mk5 = EntityNukeExplosionMK5.statFac(level, radius, pos.x, pos.y, pos.z);
        if (source.exploder() != null) mk5.detonator = source.exploder().getUUID();
        if (level.addFreshEntity(mk5)) mk5.startChunkLoading();
        if (BombConfig.enableNukeClouds) {
            EntityNukeTorex torex = EntityNukeTorex.statFac(level, pos.x, pos.y, pos.z, radius);
            if (level.addFreshEntity(torex)) torex.startChunkLoading();
        }
    }
}
