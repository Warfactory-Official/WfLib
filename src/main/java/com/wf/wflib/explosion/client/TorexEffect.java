package com.wf.wflib.explosion.client;

import com.wf.gemrender.particle.ParticleBuffer;
import com.wf.gemrender.particle.ParticleEmitter;
import com.wf.gemrender.particle.ParticleStyle;
import com.wf.wflib.explosion.api.EntityNukeTorex;
import com.wf.wflib.explosion.api.EntityNukeTorex.Cloudlet;
import com.wf.wflib.explosion.api.EntityNukeTorex.TorexType;
import dev.engine_room.flywheel.api.visual.EffectVisual;
import dev.engine_room.flywheel.api.visualization.VisualizationContext;
import net.minecraft.client.Minecraft;
import net.minecraft.core.Vec3i;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.LevelAccessor;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;

import java.util.List;
import java.util.Random;
import com.wf.wflib.fx.api.FlywheelEffectManager;
import com.wf.wflib.fx.api.WFFlywheelEffect;
import com.wf.wflib.core.api.WFLib;

/**
 * Cloudlets simulated on the CPU per tick (no closed form) => every live cloudlet rewritten into its GemRender slot
 * each tick; GPU lerps within the tick ({@code torex.vert}). Absorbance: order-independent, no sort.
 */
@EventBusSubscriber(modid = WFLib.MODID, value = Dist.CLIENT)
public final class TorexEffect implements WFFlywheelEffect {

    static final ResourceLocation VERTEX = ResourceLocation.fromNamespaceAndPath(WFLib.MODID, "instance/torex.vert");
    static final ResourceLocation CULL = ResourceLocation.fromNamespaceAndPath(WFLib.MODID, "instance/cull/torex.glsl");
    static final ResourceLocation TEXTURE_CLOUDLET =
            ResourceLocation.fromNamespaceAndPath(WFLib.MODID, "textures/particle/particle_base.png");
    static final ResourceLocation TEXTURE_FLARE =
            ResourceLocation.fromNamespaceAndPath(WFLib.MODID, "textures/particle/flare.png");

    static final int FLASH_BASE_DURATION = 30;
    static final int FLARE_BASE_DURATION = 100;
    private static final int FLARES = 3;
    private static final float NEVER = 1e9F;

    private static volatile int style = -1;

    private final EntityNukeTorex cloud;
    private final ParticleEmitter cloudlets;
    private final ParticleEmitter flares;
    private final float[] flareOffset = new float[FLARES * 3];
    private int written;
    private boolean gone;

    private TorexEffect(EntityNukeTorex cloud) {
        this.cloud = cloud;
        this.cloudlets = ParticleEmitter.create(style(), EntityNukeTorex.MAX_CLOUDLETS, cloud.getX(), cloud.getY(), cloud.getZ());
        this.flares = ParticleEmitter.create(style(), FLARES, cloud.getX(), cloud.getY(), cloud.getZ());
        Random rand = new Random(cloud.getId());
        for (int i = 0; i < flareOffset.length; i++) flareOffset[i] = (float) rand.nextGaussian() * 0.5F;
    }

    private static int style() {
        int s = style;
        if (s < 0) {
            synchronized (TorexEffect.class) {
                if (style < 0) style = ParticleBuffer.getInstance().registerStyle(ParticleStyle.builder().build());
                s = style;
            }
        }
        return s;
    }

    @SubscribeEvent
    public static void onEntityJoin(EntityJoinLevelEvent event) {
        if (!event.getLevel().isClientSide || !(event.getEntity() instanceof EntityNukeTorex torex)) return;
        if (!FlywheelEffectManager.isAvailable(event.getLevel())) return;
        FlywheelEffectManager.spawn(new TorexEffect(torex));
    }

    EntityNukeTorex cloud() {
        return cloud;
    }

    ParticleEmitter cloudlets() {
        return cloudlets;
    }

    ParticleEmitter flares() {
        return flares;
    }

    @Override
    public LevelAccessor level() {
        return cloud.level();
    }

    @Override
    public EffectVisual<?> visualize(VisualizationContext ctx, float partialTick) {
        return new TorexVisual(ctx, this, partialTick);
    }

    @Override
    public void tickEffect() {
        if (cloud.isRemoved()) {
            gone = true;
            return;
        }
        ParticleBuffer buffer = ParticleBuffer.getInstance();
        float now = Minecraft.getInstance().levelRenderer.getTicks() / 20.0F;
        writeCloudlets(buffer, now);
        writeFlares(buffer, now);
        shake();
    }

    private void writeCloudlets(ParticleBuffer buffer, float now) {
        Vec3i origin = cloudlets.origin();
        int base = cloudlets.slotBase();
        int s = style();
        List<Cloudlet> list = cloud.cloudlets;
        int count = Math.min(list.size(), cloudlets.capacity());
        for (int i = 0; i < count; i++) {
            Cloudlet c = list.get(i);
            boolean condensation = c.type == TorexType.CONDENSATION;
            float brightness = condensation ? 0.9F : 0.75F * c.colorMod;
            double greying = c.type == TorexType.RING ? 0.05D : 0D;
            float r = channel(condensation ? 1D : c.colorR + greying, brightness);
            float g = channel(condensation ? 1D : c.colorG + greying, brightness);
            float b = channel(condensation ? 1D : c.colorB + greying, brightness);
            int light = Math.max(48, (int) (Math.min(1F, (r + g + b) / 3F) * 240F));
            buffer.write(base + i,
                    (float) (c.prevPosX - origin.getX()), (float) (c.prevPosY - origin.getY()),
                    (float) (c.prevPosZ - origin.getZ()), now,
                    (float) ((c.posX - c.prevPosX) * 20D), (float) ((c.posY - c.prevPosY) * 20D),
                    (float) ((c.posZ - c.prevPosZ) * 20D),
                    NEVER, s, 2F * c.getScale(), 0F, c.getAlpha(), r, green(g), b, light);
        }
        for (int i = count; i < written; i++) buffer.clearSlot(base + i);
        written = count;
    }

    /** {@code contactNormal} slot is int-typed: green as a 16-bit fixed point ({@code torex.vert}). */
    private static int green(float g) {
        return Math.round(g * 65535F);
    }

    private static float channel(double color, float brightness) {
        return Math.clamp((float) color * brightness, 0.15F, 1F);
    }

    private void writeFlares(ParticleBuffer buffer, float now) {
        float duration = cloud.getEntityScale() * FLARE_BASE_DURATION;
        int base = flares.slotBase();
        if (cloud.effectiveAge >= duration) {
            for (int i = 0; i < FLARES; i++) buffer.clearSlot(base + i);
            return;
        }
        Vec3i origin = flares.origin();
        float alpha = Math.min(1F, (duration - cloud.effectiveAge) / duration);
        float roller = (float) cloud.rollerSize;
        for (int i = 0; i < FLARES; i++) {
            buffer.write(base + i,
                    (float) (cloud.getX() + flareOffset[3 * i] * roller - origin.getX()),
                    (float) (cloud.getY() + flareOffset[3 * i + 1] * roller + cloud.coreHeight - origin.getY()),
                    (float) (cloud.getZ() + flareOffset[3 * i + 2] * roller - origin.getZ()), now,
                    0F, 0F, 0F, NEVER, style(), 20F * roller, 1F, alpha, alpha, green(alpha), alpha, 240);
        }
    }

    private void shake() {
        if (!cloud.didPlaySound || cloud.didShake) return;
        Player player = Minecraft.getInstance().player;
        if (player != null && player.distanceTo(cloud) < 100F) {
            player.hurtTime = 15;
            player.hurtDuration = 15;
        }
        cloud.didShake = true;
    }

    @Override
    public boolean isExpired() {
        return gone;
    }

    @Override
    public void disposeEffect() {
        cloudlets.close();
        flares.close();
    }
}
