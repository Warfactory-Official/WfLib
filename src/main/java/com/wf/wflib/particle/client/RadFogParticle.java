package com.wf.wflib.particle.client;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleProvider;
import net.minecraft.client.particle.ParticleRenderType;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.client.particle.TextureSheetParticle;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.core.particles.SimpleParticleType;
import org.jetbrains.annotations.Nullable;

import java.util.Random;

/** NTM ParticleRadiationFog: 25-quad bank, 400-tick sine fade. {@code ya < 0} => cluster marker, else quad size. */
public class RadFogParticle extends TextureSheetParticle {

    private static final int QUAD_COUNT = 25;
    private static final double[] OFF_X = new double[QUAD_COUNT];
    private static final double[] OFF_Y = new double[QUAD_COUNT];
    private static final double[] OFF_Z = new double[QUAD_COUNT];
    private static final float[] SIZE = new float[QUAD_COUNT];

    static {
        // 1.7 translates under one push => steps accumulate (mean -1): bank smears away from origin.
        Random r = new Random(50L);
        double walkX = 0, walkY = 0, walkZ = 0;
        for (int i = 0; i < QUAD_COUNT; i++) {
            walkX += (r.nextGaussian() - 1.0) * 2.5;
            walkY += (r.nextGaussian() - 1.0) * 0.15;
            walkZ += (r.nextGaussian() - 1.0) * 2.5;
            SIZE[i] = (float) (r.nextDouble() * 7.5);
            OFF_X[i] = walkX + r.nextGaussian() * 0.5;
            OFF_Y[i] = walkY + r.nextGaussian() * 0.5;
            OFF_Z[i] = walkZ + r.nextGaussian() * 0.5;
        }
    }

    protected RadFogParticle(ClientLevel level, double x, double y, double z, float size) {
        super(level, x, y, z);
        this.lifetime = 400;
        this.quadSize = size;
        this.gravity = 0.0F;
        this.friction = 0.96F;
        this.hasPhysics = false;
        this.setColor(0.85F, 0.9F, 0.5F);
        this.alpha = alphaForAge(0);
    }

    @Override
    public void tick() {
        this.xo = this.x;
        this.yo = this.y;
        this.zo = this.z;
        if (++this.age >= this.lifetime) {
            this.remove();
            return;
        }
        this.xd *= 0.96D;
        this.yd *= 0.96D;
        this.zd *= 0.96D;
        this.alpha = alphaForAge(this.age);
    }

    @Override
    protected int getLightColor(float partialTick) {
        return LightTexture.FULL_BRIGHT;
    }

    private float alphaForAge(int age) {
        return Math.max(0.0F, (float) Math.sin(age * Math.PI / this.lifetime) * 0.125F);
    }

    @Override
    public ParticleRenderType getRenderType() {
        return ParticleRenderType.PARTICLE_SHEET_TRANSLUCENT;
    }

    public static final class Provider implements ParticleProvider<SimpleParticleType> {
        private final SpriteSet sprites;

        public Provider(SpriteSet sprites) {
            this.sprites = sprites;
        }

        @Override
        public @Nullable Particle createParticle(SimpleParticleType type, ClientLevel level, double x, double y,
                                                 double z, double xa, double ya, double za) {
            if (ya < 0.0D) {
                for (int i = 0; i < QUAD_COUNT; i++) {
                    level.addParticle(type, true, x + OFF_X[i], y + OFF_Y[i], z + OFF_Z[i], 0.0D, SIZE[i], 0.0D);
                }
                return null;
            }
            RadFogParticle p = new RadFogParticle(level, x, y, z, (float) ya);
            p.pickSprite(sprites);
            return p;
        }
    }
}
