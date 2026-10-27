package com.wf.wflib.anomaly.client;

import com.wf.wflib.anomaly.AnomalyBlockEntity;
import com.wf.wflib.anomaly.AnomalyLook;
import com.wf.wflib.anomaly.MincerTuning;
import com.wf.wflib.client.particle.WFParticles;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;

/**
 * Vanilla particles by {@link AnomalyLook.Style}, for anomalies without STALKER particle groups (the gravitational
 * ones): idle (each tick while present), blowout (each tick of it) and the explosion (its tick). Sounds are STALKER's
 * own ({@link com.wf.wflib.anomaly.AnomalySounds}, played by {@link AnomalyClient} and the server).
 */
final class AnomalyEffects {
    private static final DustParticleOptions ACID_DUST = new DustParticleOptions(new Vector3f(0.45F, 0.95F, 0.3F), 1.2F);
    private static final DustParticleOptions FUZZ_DUST = new DustParticleOptions(new Vector3f(0.6F, 0.4F, 0.22F), 0.8F);
    private static final BlockParticleOption ACID_SPLASH =
            new BlockParticleOption(ParticleTypes.BLOCK, Blocks.SLIME_BLOCK.defaultBlockState());

    private AnomalyEffects() {
    }

    /** {@code count} as a whole number of particles this tick: a fraction is a chance of one more. */
    private static int count(float count, RandomSource random) {
        return (int) count + (random.nextFloat() < count % 1.0F ? 1 : 0);
    }

    static void idle(Level level, AnomalyBlockEntity be, Vec3 c, RandomSource random) {
        AnomalyLook look = be.type().look();
        if (random.nextFloat() >= be.presence(0.0F)) {
            return;
        }
        // STALKER's own particles, when it has them (XrParticles, from AnomalyClient), instead of these.
        int n = look.idleParticles() != null ? 0 : count(look.idleDebris(), random);
        for (int i = 0; i < n; i++) {
            switch (look.style()) {
                case GRAVITY -> debris(level, c, random, look.lensRadius(), look.lensHeight(), look.idleSpin());
                case ELECTRIC -> arc(level, c, random, look.lensRadius(), 0.8D);
                case FIRE -> {
                    double r = random.nextDouble() * look.lensRadius();
                    double a = random.nextDouble() * Mth.TWO_PI;
                    level.addParticle(ParticleTypes.SMOKE, c.x + Math.cos(a) * r, c.y + 0.1D, c.z + Math.sin(a) * r,
                            0.0D, 0.03D, 0.0D);
                }
                case ACID -> {
                    double r = random.nextDouble() * look.lensRadius();
                    double a = random.nextDouble() * Mth.TWO_PI;
                    level.addParticle(ACID_DUST, c.x + Math.cos(a) * r, c.y + 0.1D, c.z + Math.sin(a) * r,
                            0.0D, 0.02D, 0.0D);
                }
                case FUZZ -> strand(level, c, random, be.behaviour().radius() * 0.7D);
            }
        }
    }

    /** One hanging strand: a short line of dust from the block down, swaying a little. */
    private static void strand(Level level, Vec3 c, RandomSource random, double radius) {
        double a = random.nextDouble() * Mth.TWO_PI;
        double r = Math.sqrt(random.nextDouble()) * radius;
        double x = c.x + Math.cos(a) * r;
        double z = c.z + Math.sin(a) * r;
        double length = 0.8D + random.nextDouble() * (com.wf.wflib.anomaly.BlastTuning.HAIRS_DROP - 1.0D);
        for (double d = 0.0D; d < length; d += 0.15D) {
            double sway = 0.06D * d * d;
            level.addParticle(FUZZ_DUST, x + random.nextGaussian() * sway, c.y + 0.95D - d,
                    z + random.nextGaussian() * sway, 0.0D, 0.0D, 0.0D);
        }
    }

    /** Each tick of the blowout. */
    static void blowout(Level level, AnomalyBlockEntity be, Vec3 c, RandomSource random) {
        AnomalyLook look = be.type().look();
        double radius = be.behaviour().radius();
        int since = be.sinceExplosion();
        for (int i = 0; i < (look.blowoutParticles() != null ? 0 : look.blowoutDebris()); i++) {
            switch (look.style()) {
                case GRAVITY -> debris(level, c, random, radius * 0.8D, look.lensHeight(), look.blowoutSpin());
                case ELECTRIC -> arc(level, c, random, radius, 2.5D);
                case FIRE -> {
                    if (since >= 0) {
                        // The column: flame rising fast from a tight base, smoke above.
                        double r = random.nextDouble() * 0.5D;
                        double a = random.nextDouble() * Mth.TWO_PI;
                        level.addParticle(ParticleTypes.FLAME, c.x + Math.cos(a) * r, c.y + 0.1D, c.z + Math.sin(a) * r,
                                (random.nextDouble() - 0.5D) * 0.05D, 0.25D + random.nextDouble() * 0.2D,
                                (random.nextDouble() - 0.5D) * 0.05D);
                        if (i % 3 == 0) {
                            level.addParticle(ParticleTypes.LARGE_SMOKE, c.x, c.y + 2.5D + random.nextDouble(), c.z,
                                    (random.nextDouble() - 0.5D) * 0.05D, 0.08D, (random.nextDouble() - 0.5D) * 0.05D);
                        }
                    }
                }
                case ACID -> {
                    double a = random.nextDouble() * Mth.TWO_PI;
                    double s = 0.1D + random.nextDouble() * 0.15D;
                    level.addParticle(ACID_DUST, c.x, c.y + 0.2D, c.z, Math.cos(a) * s, 0.15D + random.nextDouble() * 0.2D,
                            Math.sin(a) * s);
                }
            }
        }
    }

    /** The explosion's tick. */
    static void explode(Level level, AnomalyBlockEntity be, Vec3 c, RandomSource random) {
        AnomalyLook look = be.type().look();
        double radius = be.behaviour().radius();
        switch (look.style()) {
            case GRAVITY -> {
                boolean mincer = be.behaviour() instanceof MincerTuning;
                for (int i = 0; i < (mincer ? 40 : 25); i++) {
                    double a = random.nextDouble() * Mth.TWO_PI;
                    double s = (mincer ? 0.5D : 0.25D) + random.nextDouble() * 0.7D;
                    level.addParticle(ParticleTypes.POOF, c.x, c.y + look.lensHeight(), c.z, Math.cos(a) * s,
                            random.nextDouble() * (mincer ? 0.2D : 0.6D), Math.sin(a) * s);
                }
            }
            case ELECTRIC -> {
                if (look.blowoutParticles() == null) {
                    level.addParticle(ParticleTypes.FLASH, c.x, c.y + 0.8D, c.z, 0.0D, 0.0D, 0.0D);
                    for (int i = 0; i < 10; i++) {
                        arc(level, c, random, radius, 3.0D);
                    }
                }
            }
            case FIRE -> {
                // Nothing at once: the flames rise through the blowout ({@link #blowout}).
            }
            case ACID -> {
                for (int i = 0; i < (look.blowoutParticles() != null ? 0 : 40); i++) {
                    double a = random.nextDouble() * Mth.TWO_PI;
                    double s = 0.1D + random.nextDouble() * 0.25D;
                    level.addParticle(ACID_SPLASH, c.x, c.y + 0.3D, c.z, Math.cos(a) * s, 0.2D + random.nextDouble() * 0.3D,
                            Math.sin(a) * s);
                }
            }
        }
    }

    private static void debris(Level level, Vec3 c, RandomSource random, double radius, double height, double spin) {
        double r = 0.4D + random.nextDouble() * radius;
        double a = random.nextDouble() * Mth.TWO_PI;
        level.addParticle(WFParticles.ANOMALY_DEBRIS.get(), c.x + Math.cos(a) * r, c.y + random.nextDouble() * height * 2.0D,
                c.z + Math.sin(a) * r, c.x, spin, c.z);
    }

    /** A jagged spark line from the centre out to a random point within {@code radius}, up to {@code height}. */
    private static void arc(Level level, Vec3 c, RandomSource random, double radius, double height) {
        double a = random.nextDouble() * Mth.TWO_PI;
        double r = random.nextDouble() * radius;
        Vec3 from = c.add(0.0D, 0.05D, 0.0D);
        Vec3 to = c.add(Math.cos(a) * r, random.nextDouble() * height, Math.sin(a) * r);
        int steps = 6 + random.nextInt(5);
        for (int i = 0; i <= steps; i++) {
            double t = (double) i / steps;
            double jitter = 0.15D * Math.sin(Math.PI * t);
            level.addParticle(ParticleTypes.ELECTRIC_SPARK, Mth.lerp(t, from.x, to.x) + random.nextGaussian() * jitter,
                    Mth.lerp(t, from.y, to.y) + random.nextGaussian() * jitter,
                    Mth.lerp(t, from.z, to.z) + random.nextGaussian() * jitter, 0.0D, 0.0D, 0.0D);
        }
    }
}
