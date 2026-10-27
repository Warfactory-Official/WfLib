package com.wf.wflib.anomaly.client;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleProvider;
import net.minecraft.client.particle.ParticleRenderType;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.client.particle.TextureSheetParticle;
import net.minecraft.core.particles.SimpleParticleType;

/**
 * Dust and grit circling an anomaly's centre and drawn inward as it rises (STALKER's {@code vortex}-action leaves).
 * Spawn velocity is not a velocity: {@code (centreX, spin, centreZ)}, spin in blocks/tick along the orbit.
 */
public class AnomalyDebrisParticle extends TextureSheetParticle {
    private final double centreX;
    private final double centreZ;
    private final double spin;
    private double angle;
    private double radius;

    protected AnomalyDebrisParticle(ClientLevel level, double x, double y, double z, double centreX, double spin,
                                    double centreZ) {
        super(level, x, y, z);
        this.centreX = centreX;
        this.centreZ = centreZ;
        this.spin = spin;
        this.angle = Math.atan2(z - centreZ, x - centreX);
        this.radius = Math.hypot(x - centreX, z - centreZ);
        this.lifetime = 40 + random.nextInt(40);
        this.quadSize = 0.04F + random.nextFloat() * 0.06F;
        float shade = 0.75F + random.nextFloat() * 0.25F;
        setColor(0.42F * shade, 0.36F * shade, 0.28F * shade);
        this.hasPhysics = false;
        this.roll = random.nextFloat() * 6.2831855F;
    }

    @Override
    public void tick() {
        xo = x;
        yo = y;
        zo = z;
        oRoll = roll;
        if (age++ >= lifetime) {
            remove();
            return;
        }
        angle += spin / Math.max(radius, 0.3D);
        radius *= 0.985D;
        roll += 0.2F;
        x = centreX + Math.cos(angle) * radius;
        z = centreZ + Math.sin(angle) * radius;
        y += 0.015D;
        float left = lifetime - age;
        alpha = Math.min(1.0F, Math.min(age / 8.0F, left / 15.0F));
    }

    @Override
    public ParticleRenderType getRenderType() {
        return ParticleRenderType.PARTICLE_SHEET_TRANSLUCENT;
    }

    public static class Provider implements ParticleProvider<SimpleParticleType> {
        private final SpriteSet sprites;

        public Provider(SpriteSet sprites) {
            this.sprites = sprites;
        }

        @Override
        public Particle createParticle(SimpleParticleType type, ClientLevel level, double x, double y, double z,
                                       double xd, double yd, double zd) {
            AnomalyDebrisParticle p = new AnomalyDebrisParticle(level, x, y, z, xd, yd, zd);
            p.pickSprite(sprites);
            return p;
        }
    }
}
