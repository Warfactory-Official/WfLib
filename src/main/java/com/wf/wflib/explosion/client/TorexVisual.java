package com.wf.wflib.explosion.client;

import com.wf.gemrender.particle.GemRenderParticleTypes;
import com.wf.gemrender.particle.ParticleInstance;
import com.wf.gemrender.particle.ParticleModels;
import com.wf.gemrender.particle.ParticlePool;
import com.wf.wflib.explosion.api.EntityNukeTorex;
import dev.engine_room.flywheel.api.instance.InstanceType;
import dev.engine_room.flywheel.api.visual.EffectVisual;
import dev.engine_room.flywheel.api.visualization.VisualizationContext;
import dev.engine_room.flywheel.lib.instance.InstanceTypes;
import dev.engine_room.flywheel.lib.instance.TransformedInstance;
import dev.engine_room.flywheel.lib.visual.AbstractVisual;
import dev.engine_room.flywheel.lib.visual.SimpleDynamicVisual;
import net.minecraft.core.Vec3i;
import net.minecraft.world.level.Level;

public final class TorexVisual extends AbstractVisual implements EffectVisual<TorexEffect>, SimpleDynamicVisual {

    private static final InstanceType<ParticleInstance> TOREX =
            GemRenderParticleTypes.custom(TorexEffect.VERTEX, TorexEffect.CULL);

    private final TorexEffect effect;
    private final ParticlePool cloudlets;
    private final ParticlePool flares;
    private final TransformedInstance flash;

    public TorexVisual(VisualizationContext ctx, TorexEffect effect, float partialTick) {
        super(ctx, (Level) effect.level(), partialTick);
        this.effect = effect;
        this.cloudlets = new ParticlePool(ctx, effect.cloudlets(), TOREX, ParticleModels.absorbance(TorexEffect.TEXTURE_CLOUDLET));
        this.flares = new ParticlePool(ctx, effect.flares(), TOREX, ParticleModels.glowing(TorexEffect.TEXTURE_FLARE));
        this.flash = instancerProvider().instancer(InstanceTypes.TRANSFORMED, TorexFlashMesh.MODEL).createInstance();
        flash.setZeroTransform().setChanged();
    }

    @Override
    public void beginFrame(Context context) {
        EntityNukeTorex cloud = effect.cloud();
        float duration = cloud.getEntityScale() * TorexEffect.FLASH_BASE_DURATION;
        if (cloud.effectiveAge >= duration) {
            flash.setZeroTransform().setChanged();
            return;
        }
        double intensity = (cloud.effectiveAge + context.partialTick()) / duration;
        intensity = intensity * Math.exp(-intensity) * 2.717391304D;
        float scale = 50F * duration / TorexEffect.FLASH_BASE_DURATION;
        Vec3i origin = renderOrigin();
        flash.setIdentityTransform()
                .translate((float) (cloud.getX() - origin.getX()), (float) (cloud.getY() - origin.getY()),
                        (float) (cloud.getZ() - origin.getZ()))
                .scale(0.2F)
                .translate(0F, (float) (cloud.coreHeight * 4D), 0F)
                .scale((float) (intensity * scale));
        flash.color(1F, 1F, 1F, (float) (1D - intensity)).setChanged();
    }

    @Override
    protected void _delete() {
        cloudlets.delete();
        flares.delete();
        flash.delete();
    }
}
