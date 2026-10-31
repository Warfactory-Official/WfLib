package com.wf.wflib.mist.client;

import com.wf.gemrender.particle.ParticleBuffer;
import com.wf.gemrender.particle.ParticleEmitter;
import com.wf.gemrender.particle.ParticleOptics;
import com.wf.wflib.fx.api.ParticleLight;
import net.minecraft.client.renderer.LightTexture;
import com.wf.wflib.mist.api.MistEntity;
import dev.engine_room.flywheel.api.visual.EffectVisual;
import dev.engine_room.flywheel.api.visualization.VisualizationContext;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import com.wf.wflib.fx.api.WFFlywheelEffect;
import com.wf.wflib.fx.api.WFParticleStyles;

public class InstancedMistEffect implements WFFlywheelEffect {

    private static final double PARTICLE_DENSITY = 0.045;

    private static final int MAX_EMIT = 180;

    private static final int LIFE_TICKS_MIN = 50;
    private static final int LIFE_TICKS_JITTER = 10;
    private static final float MEAN_LIFE_TICKS = LIFE_TICKS_MIN + (LIFE_TICKS_JITTER - 1) * 0.5F;

    /** Ring capacity margin over live-particle count, so the cursor can't overwrite one still alive. */
    private static final float RING_HEADROOM = 1.2F;

    /** Vanilla {@code getQuadSize} is a half-extent (quad spans +-that); GemRender spans [-0.5, 0.5], scaled by full width. */
    private static final float BASE_SIZE = 1.5F;

    /** Live effects, client thread. */
    private static final List<InstancedMistEffect> LIVE = new ArrayList<>();

    private final Level level;
    private final MistEntity source;
    private final ParticleEmitter emitter;
    private final int style;

    private AABB opticsBox;
    private float extinction;
    /** Fractional emission carried between ticks. */
    private double owed;

    private boolean sourceGone = false;

    public InstancedMistEffect(MistEntity source) {
        this.level = source.level();
        this.source = source;

        AABB box = source.getBoundingBox();
        double mx = (box.minX + box.maxX) * 0.5;
        double my = (box.minY + box.maxY) * 0.5;
        double mz = (box.minZ + box.maxZ) * 0.5;

        int packed = ParticleLight.surface(source.level(), mx, my, mz);
        int tint = MistClientFX.tintFor(source);
        int blockLight = LightTexture.block(packed);
        int skyLight = LightTexture.sky(packed);

        int capacity = (int) Math.ceil(emitFor(box) * (LIFE_TICKS_MIN + LIFE_TICKS_JITTER) * RING_HEADROOM) + 1;
        style = WFParticleStyles.mist(tint, blockLight, skyLight);
        emitter = ParticleEmitter.create(style, capacity, mx, my, mz);
        LIVE.add(this);
    }

    /** Live effect whose cell holds {@code at}; null => none. */
    @Nullable
    static InstancedMistEffect containing(Vec3 at) {
        for (InstancedMistEffect effect : LIVE) {
            if (!effect.sourceGone && effect.source.getBoundingBox()
                    .contains(at)) {
                return effect;
            }
        }
        return null;
    }

    /** {@code box} grown by every live cell of the same style touching {@code near}. */
    static AABB cloud(AABB box, int style, AABB near) {
        for (InstancedMistEffect effect : LIVE) {
            AABB cell = effect.source.getBoundingBox();
            if (effect.style == style && !effect.sourceGone && cell.intersects(near)) {
                box = box.minmax(cell);
            }
        }
        return box;
    }

    int style() {
        return style;
    }

    /** Steady-state density = emission rate x mean life / cell volume. */
    float extinction(float[] spriteAlpha) {
        AABB box = source.getBoundingBox();
        if (!box.equals(opticsBox)) {
            double volume = box.getXsize() * box.getYsize() * box.getZsize();
            float density = (float) (emitFor(box) * MEAN_LIFE_TICKS / volume);
            extinction = ParticleOptics.extinction(ParticleBuffer.getInstance()
                    .style(style), BASE_SIZE, density, spriteAlpha);
            opticsBox = box;
        }
        return extinction;
    }

    /** Particles/tick; fractional => small cells keep the large-cell density. */
    private static double emitFor(AABB box) {
        double volume = (box.maxX - box.minX) * (box.maxY - box.minY) * (box.maxZ - box.minZ);
        return Math.min(volume * PARTICLE_DENSITY, MAX_EMIT);
    }

    ParticleEmitter emitter() {
        return emitter;
    }

    @Override
    public LevelAccessor level() {
        return level;
    }

    @Override
    public EffectVisual<?> visualize(VisualizationContext ctx, float partialTick) {
        return new InstancedMistVisual(ctx, this, partialTick);
    }

    @Override
    public void tickEffect() {
        if (source.isRemoved() || !source.isAlive()) {
            sourceGone = true;
            return;
        }

        AABB box = source.getBoundingBox();
        owed += emitFor(box);
        int emit = (int) owed;
        owed -= emit;

        for (int i = 0; i < emit; i++) {
            double x = box.minX + level.random.nextDouble() * (box.maxX - box.minX);
            double y = box.minY + level.random.nextDouble() * (box.maxY - box.minY);
            double z = box.minZ + level.random.nextDouble() * (box.maxZ - box.minZ);

            emitter.spawn(x, y, z,
                    0.0, 0.4, 0.0,
                    (LIFE_TICKS_MIN + level.random.nextInt(LIFE_TICKS_JITTER)) / 20F,
                    BASE_SIZE,
                    level.random.nextFloat() * Mth.TWO_PI,
                    1.0F);
        }
    }

    @Override
    public boolean isExpired() {
        return sourceGone && emitter.isIdle();
    }

    @Override
    public void disposeEffect() {
        LIVE.remove(this);
        emitter.close();
    }
}
