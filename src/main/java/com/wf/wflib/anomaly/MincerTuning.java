package com.wf.wflib.anomaly;

import net.minecraft.util.RandomSource;
import net.minecraft.world.phys.Vec3;

/**
 * A mincer's numbers (X-Ray {@code CMincer : CBaseGraviZone}: Vortex, Whirligig) in blocks and ticks. Idle until
 * something alive and not {@link #small} comes within {@code trigger} of {@code radius}; then a blowout pulls the
 * living toward the centre (power^5 falloff, {@code AffectPullAlife}), winding down if nobody stays inside; whatever
 * reaches the core ring is caught and thrown around the centre ever harder ({@code Telekinesis}); at
 * {@code explosionTick} it is torn and what is left launched every which way; then the anomaly is gone for
 * {@code cooldownTicks}.
 *
 * @param radius        blocks; $def_sphere, scaled up for Minecraft
 * @param trigger       share of the radius it trips within
 * @param core          share of the radius that catches (blowout_radius_percent)
 * @param playerCore    the same for players (actor_blowout_radius_percent)
 * @param explosionTick blowout_explosion_time
 * @param cooldownTicks gone after firing (disable_time / accamulate_time)
 * @param maxPower      max_start_power: the tear at the centre, in X-Ray hits ({@link #FULL_HIT} each)
 * @param pull          blocks/tick^2 at relative power 1 (throw_in_impulse_alive, tuned by feel)
 * @param caughtOrbit   radius of the caught orbit, blocks
 * @param caughtHeight  height of the caught orbit (tele_height, scaled)
 * @param spin          caught orbit, radians/tick at the start
 * @param spinGrowth    added by the time of the explosion (linear)
 * @param lift          loose objects float this high (tele_height)
 * @param launchMin     explosion throw, blocks/tick (throw_out_impulse)
 * @param launchSpread  plus up to this
 * @param teleTicks     idle float-and-drop cycle of loose objects: floating (time_to_tele)
 * @param telePause     resting (tele_pause)
 */
public record MincerTuning(double radius, double trigger, double core, double playerCore, int explosionTick,
                           int cooldownTicks, double maxPower, double pull, double caughtOrbit, double caughtHeight,
                           double spin, double spinGrowth, double lift, double launchMin, double launchSpread,
                           int teleTicks, int telePause) implements AnomalyBehaviour {
    /** X-Ray hit power 1.0 = a stalker's whole health; a player's here. */
    public static final float FULL_HIT = 20.0F;
    /** ignore_small: a body under this (bounding width x height) neither trips nor holds a blowout. */
    public static final double SMALL = 0.5D;
    /** A blowout with nothing big enough inside this long winds down instead of firing. */
    public static final int FIZZLE_TICKS = 40;
    /** Coming back: idle shimmer and debris fade in over this. */
    public static final int FADE_TICKS = 40;

    /** {@code CCustomZone::RelativePower}, attenuation 1, effective_radius 1: {@code 1 - (d / R)^2}, 0 outside. */
    public double relativePower(double dist) {
        double x = dist / radius;
        return x >= 1.0D ? 0.0D : 1.0D - x * x;
    }

    /** Small (chicken, cat, bat): ignored for tripping and holding a blowout ({@code ignore_small}). */
    public static boolean small(float width, float height) {
        return width * height < SMALL;
    }

    @Override
    public boolean trips(double dist) {
        return dist <= trigger * radius;
    }

    /** Caught (thrown around) rather than pulled: inside the core ring, the player's own if a player. */
    public boolean caught(double dist, boolean player) {
        return dist <= (player ? playerCore : core) * radius;
    }

    /** Pull on the living at {@code dist}, blocks/tick^2; inside the core held at the core edge's ({@code Affect}). */
    public double pull(double dist, boolean player) {
        if (dist > radius) {
            return 0.0D;
        }
        double r = relativePower(Math.max(dist, (player ? playerCore : core) * radius));
        return pull * r * r * r * r * r;
    }

    /** Explosion hit at {@code dist}, health points; nothing outside the core ({@code AffectThrow}). */
    @Override
    public float hit(double dist, boolean player) {
        return caught(dist, player) ? (float) (maxPower * relativePower(dist) * FULL_HIT) : 0.0F;
    }

    @Override
    public boolean fizzles() {
        return true;
    }

    /** Idle telekinesis: loose objects float during the first {@code teleTicks} of each cycle. */
    public boolean idleFloat(int age) {
        return age % (teleTicks + telePause) < teleTicks;
    }

    /** Random direction, up-biased, at launch speed: the explosion's throw. */
    public Vec3 launch(RandomSource random) {
        Vec3 d = new Vec3(random.nextGaussian(), 0.3D + random.nextDouble(), random.nextGaussian()).normalize();
        return d.scale(launchMin + random.nextDouble() * launchSpread);
    }
}
