package com.wf.wflib.continuum;

import com.hbm.api.event.HbmEvents;
import com.hbm.entity.missile.EntityMissileBaseNT;
import com.hbm.entity.missile.EntityMissileStealth;
import com.hbm.entity.missile.EntityRocketBase;
import com.wf.wflib.recon.api.ContactClass;
import com.wf.wflib.recon.api.EmconState;
import com.wf.wflib.recon.api.EntityTargetSource;
import com.wf.wflib.recon.api.ReconSignatures;
import com.wf.wflib.recon.api.ReconTargets;
import com.wf.wflib.recon.api.SeismicEvents;
import com.wf.wflib.recon.api.Signature;
import com.wf.wflib.recon.api.SignatureProvider;
import com.wf.wflib.recon.api.SignatureProviders;
import com.wf.wflib.recon.api.TargetSink;
import com.wf.wflib.recon.api.TargetSource;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.AABB;
import org.jetbrains.annotations.Nullable;

/**
 * Continuum on WFLib's sensors (docs, 3.8): its missiles and rockets are contacts on the PDA radar and every network's scope, with a cross-section from their size
 * (a stealth missile has a tenth of it), and its explosions are heard by geophones as the blasts they are. Nothing of Continuum's own radar or its alerts is
 * touched; its radar blocks keep working beside.
 */
final class ReconBridge {
    private ReconBridge() {
    }

    /** Blocks of frontal area a cross-section of 1 is, as WFLib's own fallback reads it. */
    private static final double REFERENCE_AREA = 4.0;

    static void install() {
        ReconTargets.SOURCES.register(ResourceLocation.fromNamespaceAndPath(ContinuumBridge.ID, "missiles"), 0, new Missiles());
        SignatureProviders.PROVIDERS.register(ResourceLocation.fromNamespaceAndPath(ContinuumBridge.ID, "flyers"), 0, new Signatures());
        HbmEvents.EXPLOSION.register(e -> {
            if (e.level() instanceof ServerLevel level && e.size() > 0.0F) SeismicEvents.report(level, e.x(), e.y(), e.z(), e.size());
        });
        ContinuumBridge.LOG.info("Recon: Continuum missiles and rockets are radar contacts; its explosions are heard by seismic sensors");
    }

    static boolean continuumFlyer(Entity e) {
        return e instanceof EntityMissileBaseNT || e instanceof EntityRocketBase;
    }

    private static final class Missiles implements TargetSource {
        @Override
        public void collect(ServerLevel level, AABB volume, TargetSink sink) {
            for (EntityMissileBaseNT missile : level.getEntitiesOfClass(EntityMissileBaseNT.class, volume, e -> !e.isRemoved())) {
                sink.accept(EntityTargetSource.snapshot(missile, ContactClass.MISSILE, SignatureProviders.of(missile), EmconState.ACTIVE, 0L, false));
            }
            for (EntityRocketBase rocket : level.getEntitiesOfClass(EntityRocketBase.class, volume, e -> !e.isRemoved())) {
                sink.accept(EntityTargetSource.snapshot(rocket, ContactClass.MISSILE, SignatureProviders.of(rocket), EmconState.ACTIVE, 0L, false));
            }
        }
    }

    private static final class Signatures implements SignatureProvider {
        @Override
        public @Nullable Signature of(Entity entity) {
            if (!continuumFlyer(entity)) return null;
            AABB box = entity.getBoundingBox();
            double rcs = Math.max(0.01, box.getXsize() * box.getYsize() / REFERENCE_AREA);
            if (entity instanceof EntityMissileStealth) rcs *= 0.1;
            return ReconSignatures.missile((float) rcs);
        }
    }
}
