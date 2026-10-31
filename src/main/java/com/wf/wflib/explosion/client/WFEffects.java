package com.wf.wflib.explosion.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.ParticleEngine;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import com.wf.wflib.particle.api.XrGroups;
import com.wf.wflib.particle.api.client.XrParticles;
import net.minecraft.world.phys.Vec3;
import java.util.ArrayList;
import java.util.List;
import com.wf.wflib.particle.api.client.ParticleSprites;
import com.wf.wflib.particle.api.WFParticles;
import com.wf.wflib.particle.api.client.AshParticle;
import com.wf.wflib.particle.api.client.BlockShrapnelParticle;
import com.wf.wflib.particle.api.client.ExplosionSmallParticle;
import com.wf.wflib.particle.api.client.RocketFlameParticle;
import com.wf.wflib.particle.api.client.ShockwaveParticle;
import com.wf.wflib.fx.api.WFLights;

public final class WFEffects {

    private WFEffects() {
    }

    public static void dispatch(String effect, ClientLevel level, double x, double y, double z, CompoundTag data) {
        switch (effect) {
            case "explosion_small" -> explosionSmall(level, x, y, z, data);
            case "explosion_large" -> explosionLarge(level, x, y, z, data);
            case "sonic_boom" -> sonicBoom(level, x, y, z, data);
            case "instanced_smoke" -> instancedSmoke(level, x, y, z, data);
            case "ashes" -> ashes(level, x, y, z, data);
            case "skeleton" -> skeleton(level, x, y, z, data);
            case "emp" -> emp(level, x, y, z, data);
            case "emp_stun" -> empStun(level, x, y, z, data);
            case "mining_blast" -> miningBlast(level, x, y, z, data);
            case "splatter" -> splatter(level, x, y, z, data);
            default -> {}
        }
    }

    /** Camera distance past which blasts drop debris (sub-pixel, physics per piece); clouds thin in GemRender. */
    private static final double FAR_FX = 256.0;

    private static boolean far(double x, double y, double z) {
        return Minecraft.getInstance().gameRenderer.getMainCamera().getPosition().distanceToSqr(x, y, z) > FAR_FX * FAR_FX;
    }

    /** Multiplier on the fallback's puff count. */
    private static final int INSTANCED_SMOKE_DENSITY = 9;

    /** Multiplier on the fallback's cloud-puff count (large blast). */
    private static final int INSTANCED_CLOUD_DENSITY = 5;

    private static final int FOAM_MAX_DEPTH = 24;

    private static final int FOAM_PLUME_PER_BLOCK = 26;
    private static final int FOAM_SURGE_PER_BLOCK = 20;

    /** Multiplier on {@code debrisCount} (destroyed 2x2x2 cells). */
    private static final int INSTANCED_DEBRIS_DENSITY = 3;

    /** Same blast as {@link #explosionSmall}, but the cloud is drawn as one Flywheel GPU-instanced batch. */
    private static void instancedSmoke(ClientLevel level, double x, double y, double z, CompoundTag data) {
        int count = data.getInt("count");
        float scale = data.contains("scale") ? data.getFloat("scale") : 2F;
        float speed = data.contains("speed") ? data.getFloat("speed") : 0.5F;

        if (com.wf.wflib.fx.api.FlywheelEffectManager.isAvailable(level)) {
            if (!waterFoam(level, x, y, z, scale * 1.5F)) {
                com.wf.wflib.fx.api.FlywheelEffectManager.spawn(
                        new com.wf.wflib.fx.api.InstancedParticleEffect(
                                level, x, y, z, count * INSTANCED_SMOKE_DENSITY, scale, speed, BlastFrame.of(level, x, y, z)));
            }
            spawnDebris(level, x, y, z, data); // smoke is instanced; debris stays on the normal path
        } else {
            explosionSmall(level, x, y, z, data);
        }
    }

    /**
     * @param reach blast reach, blocks; sets how much water it moves
     * @return true if the charge went off underwater (no fireball to draw above)
     */
    private static boolean waterFoam(ClientLevel level, double x, double y, double z, float reach) {
        if (!com.wf.wflib.fx.api.FlywheelEffectManager.isAvailable(level)) {
            return false;
        }
        double surface = WaterSurface.find(level, x, y, z);
        if (Double.isNaN(surface)) {
            return false;
        }

        double depth = surface - y;
        if (depth > FOAM_MAX_DEPTH) {
            return false;
        }

        // 0.35 floor: a deep charge still vents some foam.
        float vented = reach * (float) Mth.clamp(1.0 - depth / (FOAM_MAX_DEPTH * 1.5), 0.35, 1.0);
        int plume = Math.max(24, Math.round(vented * FOAM_PLUME_PER_BLOCK));
        int surge = Math.max(20, Math.round(vented * FOAM_SURGE_PER_BLOCK));

        com.wf.wflib.fx.api.FlywheelEffectManager.spawn(
                com.wf.wflib.fx.api.WFBursts.foamPlume(level, x, surface, z, plume, vented,
                        level.random));
        com.wf.wflib.fx.api.FlywheelEffectManager.spawn(
                com.wf.wflib.fx.api.WFBursts.foamSurge(level, x, surface, z, surge, vented,
                        level.random));
        return depth > 0.2;
    }

    private static void explosionSmall(ClientLevel level, double x, double y, double z, CompoundTag data) {
        int count = data.getInt("count");
        float scale = data.contains("scale") ? data.getFloat("scale") : 2F;
        float speed = data.contains("speed") ? data.getFloat("speed") : 0.5F;

        boolean submerged = waterFoam(level, x, y, z, scale * 1.5F);
        Quaternionf vent = BlastFrame.of(level, x, y, z);
        if (!submerged) {
            WFLights.blast(x, y, z, vent, 6 + 4 * scale, 6);
        }

        ParticleEngine engine = Minecraft.getInstance().particleEngine;
        if (!submerged && ParticleSprites.EXPLOSION_SMALL.get() != null) {
            for (int i = 0; i < count; i++) {
                ExplosionSmallParticle particle = new ExplosionSmallParticle(level, x, y, z, scale, speed);
                particle.vent(vent);
                particle.pickSprite(ParticleSprites.EXPLOSION_SMALL.get());
                engine.add(particle);
            }
        }

        spawnDebris(level, x, y, z, data);
    }

    private static void explosionLarge(ClientLevel level, double x, double y, double z, CompoundTag data) {
        int cloudCount = data.getInt("cloudCount");
        float cloudScale = data.contains("cloudScale") ? data.getFloat("cloudScale") : 5F;
        float cloudSpeed = data.contains("cloudSpeed") ? data.getFloat("cloudSpeed") : 1F;
        float waveScale = data.contains("waveScale") ? data.getFloat("waveScale") : 45F;
        int debrisCount = data.getInt("debrisCount");
        float debrisVelocity = data.contains("debrisVelocity") ? data.getFloat("debrisVelocity") : 1F;

        ParticleEngine engine = Minecraft.getInstance().particleEngine;
        RandomSource rand = level.random;
        Quaternionf vent = BlastFrame.of(level, x, y, z);

        if (waveScale > 0F) {
            int waveAge = Math.max(1, (int) (25F * waveScale / 45F));
            Vector3f ring = vent.transform(new Vector3f(0, 2, 0));
            engine.add(new ShockwaveParticle(level, x + ring.x, y + ring.y, z + ring.z, waveScale, waveAge, vent));
        }

        boolean submerged = waterFoam(level, x, y, z, (float) Mth.clamp(waveScale * 0.2, 3.0, 24.0));

        if (!submerged) {
            WFLights.blast(x, y, z, vent, Mth.clamp(waveScale * 0.9F, 16, 64), 12);
        }
        if (submerged) {
            // Submerged: no fireball draw; the foam is what's visible of it.
        } else if (com.wf.wflib.fx.api.FlywheelEffectManager.isAvailable(level)) {
            com.wf.wflib.fx.api.FlywheelEffectManager.spawn(
                    com.wf.wflib.fx.api.WFBursts.cloud(level, x, y, z,
                            cloudCount * INSTANCED_CLOUD_DENSITY, cloudScale, cloudSpeed, vent, rand));
        } else if (ParticleSprites.ROCKET_FLAME.get() != null) {
            for (int i = 0; i < cloudCount; i++) {
                RocketFlameParticle p = new RocketFlameParticle(level, x, y, z, cloudScale);
                p.setLifetime(70 + rand.nextInt(20));
                p.setCollision(false);
                Vector3f v = vent.transform(new Vector3f((float) (rand.nextGaussian() * 0.5),
                        (float) (rand.nextDouble() * 3.0), (float) (rand.nextGaussian() * 0.5)).mul(cloudSpeed));
                p.setParticleSpeed(v.x, v.y, v.z);
                p.pickSprite(ParticleSprites.ROCKET_FLAME.get());
                engine.add(p);
            }
        }

        if (!far(x, y, z)) {
            com.wf.wflib.explosion.api.client.BlastDebris.schedule(level, x, y, z, debrisCount * INSTANCED_DEBRIS_DENSITY,
                    debrisVelocity, vent);
        }

    }

    private static void miningBlast(ClientLevel level, double x, double y, double z, CompoundTag data) {
        int radius = data.getInt("radius");
        int[] debris = data.getIntArray("debris");
        RandomSource rand = RandomSource.create(Double.doubleToLongBits(x) * 31L
                + Double.doubleToLongBits(y) * 17L + Double.doubleToLongBits(z));

        int plumes = 30 + radius * 12;
        WFLights.blast(x, y, z, 6 + 2 * radius, 5);
        boolean instanced = com.wf.wflib.fx.api.FlywheelEffectManager.isAvailable(level);

        if (instanced) {
            com.wf.wflib.fx.api.FlywheelEffectManager.spawn(
                    com.wf.wflib.fx.api.WFBursts.plume(level, x, y, z, plumes, radius, rand));
        } else {
            handTickedPlume(level, x, y, z, plumes, radius, rand);
        }

        if (debris.length == 0) {
            return;
        }

        float spread = radius * 0.55F;
        int perType = Math.max(120, 110 / debris.length);

        com.wf.gemrender.particle.ParticleCollision.Probe probe = instanced
                ? com.wf.gemrender.particle.LevelContactProbe.of(level)
                : null;

        for (int id : debris) {
            BlockState state = Block.stateById(id);
            if (state.isAir()) {
                continue;
            }

            if (instanced) {
                com.wf.wflib.fx.InstancedBurst burst =
                        com.wf.wflib.fx.api.WFBursts.blockChunks(level, x, y, z, perType,
                                spread, 0.45F, state, rand, probe);
                if (burst != null) {
                    com.wf.wflib.fx.api.FlywheelEffectManager.spawn(burst);
                    continue;
                }
            }
            handTickedChunks(level, x, y, z, perType, spread, state, rand);
        }
    }

    private static void handTickedPlume(ClientLevel level, double x, double y, double z, int count,
                                        float radius, RandomSource rand) {
        for (int i = 0; i < count; i++) {
            double dx = rand.nextGaussian();
            double dy = rand.nextGaussian();
            double dz = rand.nextGaussian();
            double len = Math.sqrt(dx * dx + dy * dy + dz * dz);
            if (len < 1.0e-4) {
                dx = 0.0;
                dy = 1.0;
                dz = 0.0;
                len = 1.0;
            }
            double ux = dx / len;
            double uy = dy / len;
            double uz = dz / len;
            double dist = rand.nextDouble() * radius * 0.6;
            double speed = 0.45 + rand.nextDouble() * 0.4;
            double vy = uy < 0.0 ? uy * 0.2 : uy;

            level.addParticle(WFParticles.SMOKE_PLUME.get(), true,
                    x + ux * dist, y + uy * dist, z + uz * dist, ux * speed, vy * speed, uz * speed);
        }
    }

    private static void handTickedChunks(ClientLevel level, double x, double y, double z, int count,
                                         float spread, BlockState state, RandomSource rand) {
        BlockParticleOption option = new BlockParticleOption(WFParticles.BLOCK_DEBRIS.get(), state);
        for (int i = 0; i < count; i++) {
            level.addParticle(option, true,
                    x + rand.nextGaussian() * spread,
                    y + 0.6 + rand.nextGaussian() * spread * 0.5,
                    z + rand.nextGaussian() * spread,
                    rand.nextGaussian() * 0.45, rand.nextGaussian() * 0.45, rand.nextGaussian() * 0.45);
        }
    }

    private static void sonicBoom(ClientLevel level, double x, double y, double z, CompoundTag data) {
        float waveScale = data.contains("waveScale") ? data.getFloat("waveScale") : 12F;
        int waveAge = Math.max(1, (int) (12F * waveScale / 45F));
        Minecraft.getInstance().particleEngine.add(new ShockwaveParticle(level, x, y, z, waveScale, waveAge));
    }

    private static void skeleton(ClientLevel level, double x, double y, double z, CompoundTag data) {
        if (!com.wf.wflib.fx.api.FlywheelEffectManager.isAvailable(level)) return;
        float bodyYaw = data.getFloat("bodyYaw");
        float headYaw = data.getFloat("headYaw");
        float height = data.contains("height") ? data.getFloat("height") : 1.8F;
        float brightness = data.contains("brightness") ? data.getFloat("brightness") : 0.85F;
        com.wf.wflib.fx.api.FlywheelEffectManager.spawn(
                com.wf.wflib.fx.api.SkeletonBoneEffect.biped(level, x, y, z, bodyYaw, headYaw, height, brightness));
    }

    private static void spawnDebris(ClientLevel level, double x, double y, double z, CompoundTag data) {
        if (far(x, y, z)) {
            return;
        }
        BlockState surface = nearbySurface(level, x, y, z);
        if (surface != null && !surface.isAir()) {
            int debris = data.contains("debris") ? data.getInt("debris") : 15;
            if (com.wf.wflib.fx.api.FlywheelEffectManager.isAvailable(level)) {
                com.wf.wflib.fx.api.FlywheelEffectManager.spawn(
                        com.wf.wflib.fx.api.WFBursts.shrapnel(level, x, y, z, debris, surface,
                                level.random));
            } else {
                ParticleEngine engine = Minecraft.getInstance().particleEngine;
                for (int i = 0; i < debris; i++) {
                    double vx = level.random.nextGaussian() * 0.25;
                    double vy = 0.35 + level.random.nextDouble() * 0.5;
                    double vz = level.random.nextGaussian() * 0.25;
                    engine.add(new BlockShrapnelParticle(level, x, y + 0.1, z, vx, vy, vz, surface));
                }
            }
        }
    }

    private static final int EMP_BURST_CAP = 4;
    private static final int EMP_STUN_CAP = 24;
    private static final double EMP_STUN_RANGE = 48.0;
    private static final List<XrParticles.Playing> EMP_BURSTS = new ArrayList<>();
    private static final List<XrParticles.Playing> EMP_STUNS = new ArrayList<>();

    private static void emp(ClientLevel level, double x, double y, double z, CompoundTag data) {
        int radius = data.contains("radius") ? data.getInt("radius") : 48;
        WFLights.flash(x, y + WFLights.FLASH_LIFT, z, 24, WFLights.EMP_RGB, 24 * 24 * 0.5F, 8);
        EMP_BURSTS.removeIf(XrParticles.Playing::finished);
        if (EMP_BURSTS.size() < EMP_BURST_CAP) {
            float scale = Mth.clamp(radius / 6.0F * 1.5F, 3.0F, 12.0F);
            XrParticles.play(XrGroups.ELECTRA2_BLAST, new Vec3(x, y, z), scale, 2.0F, false)
                    .ifPresent(EMP_BURSTS::add);
        }
    }

    private static void empStun(ClientLevel level, double x, double y, double z, CompoundTag data) {
        EMP_STUNS.removeIf(XrParticles.Playing::finished);
        Vec3 camera = Minecraft.getInstance().gameRenderer.getMainCamera().getPosition();
        if (EMP_STUNS.size() < EMP_STUN_CAP && camera.distanceToSqr(x, y, z) < EMP_STUN_RANGE * EMP_STUN_RANGE) {
            XrParticles.play(XrGroups.ELECTRA_ENTRANCE_SMALL, new Vec3(x, y, z), 0.6F, 0.6F, false)
                    .ifPresent(p -> EMP_STUNS.add(p.glow(0.6F)));
        }
    }

    /** Invoked by {@code BlastCorpses}. */
    private static void splatter(ClientLevel level, double x, double y, double z, CompoundTag data) {
        if (!com.wf.wflib.fx.api.FlywheelEffectManager.isAvailable(level)) {
            return;
        }
        float width = data.getFloat("width");
        com.wf.wflib.fx.api.FlywheelEffectManager.spawn(com.wf.wflib.fx.api.WFBursts.bloodSpray(
                level, x, y, z, width, data.getFloat("speed"), level.random));
        if (!data.getBoolean("water")) {
            com.wf.wflib.fx.api.FlywheelEffectManager.spawn(
                    com.wf.wflib.fx.api.WFBursts.bloodPool(level, x, y, z, width, level.random));
        }
    }

    private static void ashes(ClientLevel level, double x, double y, double z, CompoundTag data) {
        int count = data.getInt("count");
        float scale = data.contains("scale") ? data.getFloat("scale") : 0.125F;

        boolean instanced = com.wf.wflib.fx.api.FlywheelEffectManager.isAvailable(level);
        if (instanced) {
            com.wf.wflib.fx.api.FlywheelEffectManager.spawn(
                    com.wf.wflib.fx.api.WFBursts.ash(level, x, y, z, count, scale,
                            level.random));
        }

        ParticleEngine engine = Minecraft.getInstance().particleEngine;
        for (int i = 0; i < count; i++) {
            double ox = x + (level.random.nextDouble() - 0.5) * 0.8;
            double oy = y + (level.random.nextDouble() - 0.5) * 0.8;
            double oz = z + (level.random.nextDouble() - 0.5) * 0.8;

            if (!instanced && ParticleSprites.ASH.get() != null) {
                AshParticle ash = new AshParticle(level, ox, oy, oz, scale);
                ash.setParticleSpeed(
                        (level.random.nextDouble() - 0.5) * 0.2,
                        level.random.nextDouble() * 0.25,
                        (level.random.nextDouble() - 0.5) * 0.2);
                ash.pickSprite(ParticleSprites.ASH.get());
                engine.add(ash);
            }
        }
        if (count > 0) {
            XrParticles.play(XrGroups.AF_THERMAL_SHOW, new Vec3(x, y, z), 1.0F, 1.0F, false);
        }
    }

    private static BlockState nearbySurface(ClientLevel level, double x, double y, double z) {
        BlockPos base = BlockPos.containing(x, y, z);
        for (Direction dir : Direction.values()) {
            BlockState state = level.getBlockState(base.relative(dir));
            if (isSolid(state)) return state;
        }
        BlockPos.MutableBlockPos scan = base.mutable();
        for (int i = 0; i < 8; i++) {
            scan.move(Direction.DOWN);
            BlockState state = level.getBlockState(scan);
            if (isSolid(state)) return state;
        }
        return null;
    }

    /** Liquid != solid: no debris from it. */
    private static boolean isSolid(BlockState state) {
        return !state.isAir() && state.getFluidState().isEmpty();
    }
}
