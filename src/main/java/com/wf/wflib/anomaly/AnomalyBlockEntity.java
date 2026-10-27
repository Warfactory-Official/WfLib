package com.wf.wflib.anomaly;

import com.wf.wflib.anomaly.AnomalyType.Quirk;
import com.wf.wflib.block.ModBlockEntities;
import com.wf.wflib.hazard.ContaminationUtil;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ItemParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.entity.vehicle.VehicleEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * An anomaly's state, its {@link AnomalyType} from its block. The server starts each blowout with a block event; both
 * sides then run the same timeline: the server moves everything but players and deals the damage, each client moves
 * its own player (player motion is the client's) and draws.
 */
public class AnomalyBlockEntity extends BlockEntity {
    private static final int EVENT_BLOWOUT = 1;
    private static final int EVENT_FIZZLE = 2;

    /** SPENT: fired, recharging (a mincer is gone meanwhile, a blast faintly there). */
    public enum Phase {
        IDLE, BLOWOUT, SPENT
    }

    private final AnomalyType type;
    private Phase phase = Phase.IDLE;
    /** Ticks into {@link #phase}. */
    private int ticks;
    /** Ticks loaded: idle float cycle, client animation. */
    private int age;
    /** Server: blowout ticks in a row with nobody big enough inside. */
    private int empty;
    /** Server: who was inside last tick, for the entrance sound. */
    private java.util.Set<Integer> inside = java.util.Set.of();
    /** Entity ids hit this blowout; each side keeps its own. */
    private final java.util.Set<Integer> hit = new java.util.HashSet<>();
    /** Server, a CHairsZone: where each body inside was last tick, for its speed. */
    private java.util.Map<Integer, Vec3> seen = java.util.Map.of();
    /** Server: Rebounders loaded now, for {@link #shielding}. */
    private static final java.util.Set<AnomalyBlockEntity> SHIELDS =
            java.util.Collections.newSetFromMap(new java.util.WeakHashMap<>());
    /**
     * A {@link Quirk#moves wandering} anomaly's path, from its position so both sides agree: reach on each axis,
     * degrees per tick, start angle ({@code zone_mine_ghost}: 0.5-3 m, 1-3 and 1-4 degrees per 22 ms, 0-180).
     */
    private final double pathX, pathZ, rateX, rateZ, phase0;

    public AnomalyBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.ANOMALY.get(), pos, state);
        type = ((AnomalyBlock) state.getBlock()).type();
        RandomSource path = RandomSource.create(pos.asLong());
        boolean comet = type.quirk() == Quirk.ORBIT;
        double near = comet ? 5.0D : 0.5D;
        double spread = comet ? 4.0D : 2.5D;
        pathX = (near + spread * path.nextDouble()) * (path.nextBoolean() ? 1 : -1);
        pathZ = (near + spread * path.nextDouble()) * (path.nextBoolean() ? 1 : -1);
        int stepX = 1 + path.nextInt(3);
        int stepZ = stepX == 1 ? 2 + path.nextInt(2) : stepX == 2 ? 1 + 2 * path.nextInt(2) : 2 + 2 * path.nextInt(2);
        double perTick = (comet ? 0.35D : 1.0D) * 50.0D / 22.0D;
        rateX = stepX * perTick;
        rateZ = stepZ * perTick;
        phase0 = 45.0D * path.nextInt(5);
    }

    public AnomalyType type() {
        return type;
    }

    public AnomalyBehaviour behaviour() {
        return type.behaviour();
    }

    public Phase phase() {
        return phase;
    }

    public int ticks() {
        return ticks;
    }

    public int age() {
        return age;
    }

    /** Ground centre: where the pull aims ({@code CMincer::ThrowInCenter}); a wandering one's, where it is now. */
    public Vec3 center() {
        return center(0.0F);
    }

    public Vec3 center(float partial) {
        Vec3 base = Vec3.atBottomCenterOf(worldPosition);
        if (!type.quirk().moves() || level == null) {
            return base;
        }
        double t = level.getGameTime() + partial;
        return base.add(Math.sin(Math.toRadians(phase0 + rateX * t)) * pathX,
                type.quirk() == Quirk.ORBIT ? 1.0D : 0.0D, Math.sin(Math.toRadians(rateZ * t)) * pathZ);
    }

    /**
     * How far {@code e} is, as this anomaly measures: from the centre; a {@code CHairsZone}'s sideways only, and only
     * for what is below it, down to {@link BlastTuning#HAIRS_DROP} ({@code Affect}: {@code P.y = object y}).
     */
    public double reach(Entity e, Vec3 c) {
        AABB box = e.getBoundingBox();
        if (behaviour() instanceof BlastTuning blast && blast.hairs()) {
            if (box.minY > c.y + 1.0D || box.maxY < c.y - BlastTuning.HAIRS_DROP) {
                return Double.MAX_VALUE;
            }
            return Math.hypot(box.getCenter().x - c.x, box.getCenter().z - c.z);
        }
        return box.getCenter().distanceTo(c);
    }

    /** This tick is the explosion. */
    public boolean exploding() {
        return phase == Phase.BLOWOUT && ticks == behaviour().explosionTick();
    }

    /**
     * This tick hits: a mincer's explosion only; a blast's from its explosion to the end of its blowout (a Burner's
     * column burns whatever walks in, {@link #claim} keeps it to once per blowout).
     */
    public boolean striking() {
        return behaviour() instanceof BlastTuning ? sinceExplosion() >= 0 && phase == Phase.BLOWOUT : exploding();
    }

    /** Ticks since the explosion: 0 on it, on through the rest of the blowout and spent; -1 before it / idle. */
    public int sinceExplosion() {
        int explosion = behaviour().explosionTick();
        return switch (phase) {
            case IDLE -> -1;
            case BLOWOUT -> ticks >= explosion ? ticks - explosion : -1;
            case SPENT -> ticks + behaviour().blowoutTicks() - explosion;
        };
    }

    @Override
    public boolean triggerEvent(int id, int param) {
        if (id == EVENT_BLOWOUT) {
            phase = Phase.BLOWOUT;
            ticks = 0;
            empty = 0;
            hit.clear();
            return true;
        }
        if (id == EVENT_FIZZLE) {
            // Back to a fully present idle: it never fired.
            phase = Phase.IDLE;
            ticks = MincerTuning.FADE_TICKS;
            return true;
        }
        return super.triggerEvent(id, param);
    }

    private void step() {
        age++;
        ticks++;
        if (phase == Phase.BLOWOUT && ticks > behaviour().blowoutTicks()) {
            phase = Phase.SPENT;
            ticks = 0;
        } else if (phase == Phase.SPENT && ticks >= behaviour().cooldownTicks()) {
            phase = Phase.IDLE;
            ticks = 0;
        }
    }

    public static void serverTick(Level level, BlockPos pos, BlockState state, AnomalyBlockEntity be) {
        if (!AnomalyConfig.enabled) {
            return;
        }
        be.step();
        if (be.type.quirk() == Quirk.REBOUNDS) {
            SHIELDS.add(be);
        }
        if (be.phase == Phase.SPENT) {
            return;
        }
        AnomalyBehaviour behaviour = be.behaviour();
        Vec3 c = be.center();
        boolean occupied = false;
        boolean tripped = false;
        boolean hairs = behaviour instanceof BlastTuning blast && blast.hairs();
        java.util.Set<Integer> now = new java.util.HashSet<>();
        java.util.Map<Integer, Vec3> seen = hairs ? new java.util.HashMap<>() : java.util.Map.of();
        AABB around = new AABB(c, c).inflate(behaviour.radius());
        for (Entity e : level.getEntities((Entity) null, hairs ? around.expandTowards(0.0D, -BlastTuning.HAIRS_DROP,
                0.0D) : around, AnomalyBlockEntity::affected)) {
            double dist = be.reach(e, c);
            if (dist > behaviour.radius()) {
                continue;
            }
            now.add(e.getId());
            boolean alive = e instanceof LivingEntity;
            if (alive && !be.inside.contains(e.getId()) && be.type.sounds().entrance() != null) {
                level.playSound(null, e.getX(), e.getY(), e.getZ(), be.type.sounds().entrance().get(),
                        SoundSource.HOSTILE, 1.0F, 1.0F);
            }
            boolean big = alive && !MincerTuning.small(e.getBbWidth(), e.getBbHeight());
            occupied |= big;
            // A CHairsZone only wakes for something moving fast enough (CHairsZone::CheckForAwaking).
            boolean moving = true;
            if (hairs) {
                Vec3 last = be.seen.get(e.getId());
                seen.put(e.getId(), e.position());
                moving = last != null && Math.hypot(e.getX() - last.x, e.getZ() - last.z)
                        > ((BlastTuning) behaviour).minSpeed();
            }
            // Only the living set it off (items, bolts, arrows don't); while one stays inside it fires again as soon
            // as it has recharged (CCustomZone::AccumulateState).
            tripped |= alive && (big || behaviour.smallTrips()) && behaviour.trips(dist) && moving;
            if (alive && level instanceof ServerLevel server) {
                be.dwell(server, (LivingEntity) e, dist);
            }
            boolean struck = true;
            if (be.striking()) {
                struck = be.claim(e);
                // Hit first: the throw below then throws the body (or what dropped from it).
                if (struck && e instanceof LivingEntity living && level instanceof ServerLevel server) {
                    be.hit(server, living, c, behaviour.hit(dist, living instanceof Player));
                }
            }
            // Fields move nothing (but the no-gravity one): no motion to sync.
            boolean moves = !(behaviour instanceof FieldTuning) || be.type.quirk() == Quirk.WEIGHTLESS;
            if (struck && moves && !(e instanceof Player)) {
                be.affect(e);
                e.hurtMarked = true;
            }
        }
        be.inside = now;
        be.seen = seen;
        if (be.phase == Phase.IDLE && tripped) {
            level.blockEvent(pos, state.getBlock(), EVENT_BLOWOUT, 0);
        } else if (be.phase == Phase.BLOWOUT && !be.exploding() && behaviour.fizzles()) {
            be.empty = occupied ? 0 : be.empty + 1;
            if (be.empty >= MincerTuning.FIZZLE_TICKS) {
                level.blockEvent(pos, state.getBlock(), EVENT_FIZZLE, 0);
            }
        }
    }

    /**
     * Each tick a living thing is inside: a field's hurt (every {@link FieldTuning#PULSE_TICKS}) or dose, Green
     * Dragon's drag, Liquid Gold's drain.
     */
    private void dwell(ServerLevel level, LivingEntity living, double dist) {
        if (behaviour() instanceof FieldTuning field) {
            if (field.radiation()) {
                ContaminationUtil.contaminate(living, ContaminationUtil.HazardType.RADIATION,
                        ContaminationUtil.ContaminationType.CREATIVE, field.dose(dist));
            } else if ((age + living.getId()) % FieldTuning.PULSE_TICKS == 0 && field.perSecond() > 0.0D) {
                living.hurt(source(level), field.hit(dist, living instanceof Player));
            }
        }
        double near = 1.0D - (dist / behaviour().radius()) * (dist / behaviour().radius());
        switch (type.quirk()) {
            case SLOWS -> {
                if (age % 10 == 0) {
                    living.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 30, 2, true, false, true));
                }
            }
            // About a food point every 4 s in the middle (DAO: stamina -0.002 a frame, thirst).
            case DRAINS -> {
                if (living instanceof Player player) {
                    player.causeFoodExhaustion((float) (0.05D * near));
                }
            }
            default -> {
            }
        }
    }

    private DamageSource source(ServerLevel level) {
        return new DamageSource(level.registryAccess().registryOrThrow(Registries.DAMAGE_TYPE)
                .getHolderOrThrow(type.damage()), center());
    }

    /** The Rebounder {@code victim} is inside, if any (server). */
    @org.jetbrains.annotations.Nullable
    public static AnomalyBlockEntity shielding(LivingEntity victim) {
        for (AnomalyBlockEntity be : SHIELDS) {
            if (!be.isRemoved() && be.level == victim.level()
                    && be.reach(victim, be.center()) <= be.behaviour().radius()) {
                return be;
            }
        }
        return null;
    }

    /** While {@link #striking}: true (and remembered) the first time this blowout. */
    public boolean claim(Entity e) {
        return hit.add(e.getId());
    }

    /**
     * The explosion's hit, by this type's damage (armour resolves it as shock, thermal, chemical...); a Burner sets
     * it alight; a mincer kill tears the body apart ({@code body_tear}): gore everywhere, no slow death pose.
     */
    private void hit(ServerLevel level, LivingEntity living, Vec3 c, float hit) {
        if (type.quirk() == Quirk.TIME_SLIP) {
            timeSlip(level, living);
            return;
        }
        if (hit <= 0.0F) {
            return;
        }
        living.hurt(source(level), hit);
        if (type.sounds().hit() != null) {
            level.playSound(null, living.getX(), living.getY(), living.getZ(), type.sounds().hit().get(),
                    SoundSource.HOSTILE, 1.0F, 1.0F);
        }
        if (behaviour() instanceof BlastTuning blast && blast.igniteSeconds() > 0) {
            living.igniteForSeconds(blast.igniteSeconds());
        }
        if (!(behaviour() instanceof MincerTuning) || !living.isDeadOrDying()) {
            return;
        }
        Vec3 at = living.getBoundingBox().getCenter();
        double spread = living.getBbWidth() * 0.5D;
        level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, Blocks.REDSTONE_BLOCK.defaultBlockState()),
                at.x, at.y, at.z, 80, spread, living.getBbHeight() * 0.3D, spread, 0.6D);
        level.sendParticles(new ItemParticleOption(ParticleTypes.ITEM, new ItemStack(Items.BEEF)),
                at.x, at.y, at.z, 30, spread, living.getBbHeight() * 0.3D, spread, 0.45D);
        if (type.sounds().tear() != null) {
            level.playSound(null, at.x, at.y, at.z, type.sounds().tear().get(), SoundSource.HOSTILE, 2.0F, 1.0F);
        }
        if (!(living instanceof Player)) {
            // Nothing left to fall over: finish the death next tick (XP still drops).
            living.deathTime = 19;
            living.setInvisible(true);
        }
    }

    /**
     * Flash ({@code anomalies_hit_functions.zone_mine_flash}): a player loses 3-8 hours, coming to hungry (a food
     * point an hour), a few % of health down and dazed; alone on the server, the clock moves on too. A creature is
     * simply gone ({@code safe_release_manager.release}).
     */
    private void timeSlip(ServerLevel level, LivingEntity living) {
        if (type.sounds().hit() != null) {
            level.playSound(null, living.getX(), living.getY(), living.getZ(), type.sounds().hit().get(),
                    SoundSource.HOSTILE, 1.0F, 1.0F);
        }
        Vec3 at = living.getBoundingBox().getCenter();
        level.sendParticles(ParticleTypes.FLASH, at.x, at.y, at.z, 1, 0.0D, 0.0D, 0.0D, 0.0D);
        if (!(living instanceof Player player)) {
            living.discard();
            return;
        }
        RandomSource random = level.random;
        int minutes = (3 + random.nextInt(6)) * 60 + 1 + random.nextInt(59);
        player.causeFoodExhaustion(4.0F * minutes / 60.0F);
        player.hurt(source(level), player.getMaxHealth() * (0.01F + 0.03F * random.nextFloat()));
        player.addEffect(new MobEffectInstance(MobEffects.CONFUSION, 120, 0));
        if (level.getServer().getPlayerCount() == 1) {
            ServerLevel overworld = level.getServer().overworld();
            overworld.setDayTime(overworld.getDayTime() + minutes * 1000L / 60L);
        }
    }

    public static void clientTick(Level level, BlockPos pos, BlockState state, AnomalyBlockEntity be) {
        if (!AnomalyConfig.enabled) {
            return;
        }
        be.step();
        com.wf.wflib.anomaly.client.AnomalyClient.tick(be);
    }

    /** Pulled, floated or thrown: the living, loose objects, vehicles and anything in flight. Not spectators or creative players. */
    public static boolean affected(Entity e) {
        if (e.isSpectator() || e instanceof Player p && p.isCreative()) {
            return false;
        }
        return e instanceof LivingEntity || e instanceof ItemEntity || e instanceof ExperienceOrb
                || e instanceof Projectile || e instanceof VehicleEntity;
    }

    /** One tick of this anomaly on {@code e} (caller checks {@link #affected} and range). */
    public void affect(Entity e) {
        if (phase == Phase.SPENT) {
            return;
        }
        if (behaviour() instanceof FieldTuning) {
            if (type.quirk() == Quirk.WEIGHTLESS) {
                // Gravity undone, drifting slowed; a nudge off the ground so the living lift away.
                Vec3 v = e.getDeltaMovement();
                e.setDeltaMovement(v.x, v.y * 0.98D + e.getGravity() + (e.onGround() ? 0.08D : 0.0D), v.z);
                e.resetFallDistance();
            }
            return;
        }
        if (behaviour() instanceof BlastTuning blast) {
            if (type.quirk() == Quirk.REBOUNDS && e instanceof Projectile) {
                glance(e);
            } else if (striking()) {
                Vec3 thrown = blast.throwAt(reach(e, center()), e.getRandom());
                if (!thrown.equals(Vec3.ZERO)) {
                    e.setDeltaMovement(type.quirk() == Quirk.REBOUNDS ? outward(e).scale(thrown.length()) : thrown);
                }
            }
            return;
        }
        MincerTuning tuning = (MincerTuning) behaviour();
        Vec3 c = center();
        Vec3 at = e.getBoundingBox().getCenter();
        Vec3 to = c.subtract(at);
        double dist = to.length();
        boolean player = e instanceof Player;
        RandomSource random = e.getRandom();
        if (exploding()) {
            if (e instanceof LivingEntity ? tuning.caught(dist, player) : dist <= tuning.radius()) {
                e.setDeltaMovement(tuning.launch(random));
            }
            return;
        }
        if (e instanceof LivingEntity) {
            if (phase != Phase.BLOWOUT) {
                return;
            }
            if (!tuning.caught(dist, player)) {
                Vec3 in = dist < 1.0E-4D ? Vec3.ZERO : to.scale(1.0D / dist);
                e.addDeltaMovement(in.scale(tuning.pull(dist, player)));
                return;
            }
            // Caught: dragged round the centre on a tightening, rising orbit, jerked about harder as it builds.
            double p = (double) ticks / tuning.explosionTick();
            double angle = ticks * (tuning.spin() + tuning.spinGrowth() * p) + e.getId();
            double orbit = tuning.caughtOrbit() * (1.0D - 0.4D * p);
            Vec3 target = c.add(Math.cos(angle) * orbit,
                    tuning.caughtHeight() * (1.0D + 0.45D * p) + 0.3D * Math.sin(ticks * 0.7D),
                    Math.sin(angle) * orbit);
            Vec3 v = target.subtract(at).scale(0.35D);
            double jerk = random.nextInt(6) == 0 ? 0.5D + 0.5D * p : 0.12D * p;
            v = v.add(random.nextGaussian() * jerk, random.nextGaussian() * jerk * 0.6D, random.nextGaussian() * jerk);
            e.setDeltaMovement(v);
            e.resetFallDistance();
            return;
        }
        boolean floating = phase == Phase.BLOWOUT || tuning.idleFloat(age);
        if (floating) {
            Vec3 target = c.add(0.0D, phase == Phase.BLOWOUT ? tuning.lift() : tuning.lift() * 0.4D, 0.0D);
            e.setDeltaMovement(e.getDeltaMovement().scale(0.85D).add(target.subtract(at).scale(0.03D))
                    .add(0.0D, e.getGravity(), 0.0D));
        }
    }

    /** Rebounder: a projectile heading in is turned back off the disc, at half speed. */
    private void glance(Entity e) {
        Vec3 n = e.position().subtract(center());
        if (n.lengthSqr() < 1.0E-6D) {
            return;
        }
        n = n.normalize();
        Vec3 v = e.getDeltaMovement();
        double in = v.dot(n);
        if (in < 0.0D) {
            e.setDeltaMovement(v.subtract(n.scale(2.0D * in)).scale(0.5D));
        }
    }

    /** Away from the centre, half as much up again: the Rebounder's bat. */
    private Vec3 outward(Entity e) {
        Vec3 d = e.position().subtract(center());
        Vec3 flat = new Vec3(d.x, 0.0D, d.z);
        if (flat.lengthSqr() < 1.0E-6D) {
            double a = e.getRandom().nextDouble() * Mth.TWO_PI;
            flat = new Vec3(Math.cos(a), 0.0D, Math.sin(a));
        }
        return flat.normalize().add(0.0D, 0.5D, 0.0D).normalize();
    }

    @Override
    public void setRemoved() {
        super.setRemoved();
        SHIELDS.remove(this);
        if (level != null && level.isClientSide) {
            com.wf.wflib.anomaly.client.AnomalyClient.forget(this);
        }
    }

    /** Fade of the idle look: 0 hidden, rising to 1 over {@link MincerTuning#FADE_TICKS} once back. */
    public float presence(float partial) {
        return switch (phase) {
            case SPENT -> type.look().spentPresence();
            case BLOWOUT -> 1.0F;
            case IDLE -> Mth.clamp((ticks + partial) / MincerTuning.FADE_TICKS, 0.0F, 1.0F);
        };
    }
}
