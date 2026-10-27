package com.wf.wflib.anomaly;

import com.wf.wflib.block.ModBlockEntities;
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
    /** Entity id -> game time it was last hit (disable_time); each side keeps its own. */
    private final java.util.Map<Integer, Long> hitAt = new java.util.HashMap<>();

    public AnomalyBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.ANOMALY.get(), pos, state);
        type = ((AnomalyBlock) state.getBlock()).type();
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

    /** Ground centre: where the pull aims ({@code CMincer::ThrowInCenter}). */
    public Vec3 center() {
        return Vec3.atBottomCenterOf(worldPosition);
    }

    /** This tick is the explosion. */
    public boolean exploding() {
        return phase == Phase.BLOWOUT && ticks == behaviour().explosionTick();
    }

    /**
     * This tick hits: a mincer's explosion only; a blast's from its explosion to the end of its blowout (a Burner's
     * column burns whatever walks in, {@link #claim} keeps it to once per disable_time).
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
        if (be.phase == Phase.SPENT) {
            return;
        }
        AnomalyBehaviour behaviour = be.behaviour();
        Vec3 c = be.center();
        boolean occupied = false;
        boolean tripped = false;
        java.util.Set<Integer> now = new java.util.HashSet<>();
        for (Entity e : level.getEntities((Entity) null, new AABB(c, c).inflate(behaviour.radius()),
                AnomalyBlockEntity::affected)) {
            double dist = e.getBoundingBox().getCenter().distanceTo(c);
            if (dist > behaviour.radius()) {
                continue;
            }
            now.add(e.getId());
            if (!be.inside.contains(e.getId())) {
                level.playSound(null, e.getX(), e.getY(), e.getZ(), be.type.sounds().entrance().get(),
                        SoundSource.HOSTILE, 1.0F, 1.0F);
            }
            boolean big = e instanceof LivingEntity && !MincerTuning.small(e.getBbWidth(), e.getBbHeight());
            occupied |= big;
            tripped |= (big || behaviour.smallTrips()) && behaviour.trips(dist) && !be.immune(e);
            boolean struck = true;
            if (be.striking()) {
                struck = be.claim(e);
                // Hit first: the throw below then throws the body (or what dropped from it).
                if (struck && e instanceof LivingEntity living && level instanceof ServerLevel server) {
                    be.hit(server, living, c, behaviour.hit(dist, living instanceof Player));
                }
            }
            if (struck && !(e instanceof Player)) {
                be.affect(e);
                e.hurtMarked = true;
            }
        }
        be.inside = now;
        if (be.phase == Phase.IDLE && tripped) {
            level.blockEvent(pos, state.getBlock(), EVENT_BLOWOUT, 0);
        } else if (be.phase == Phase.BLOWOUT && !be.exploding() && behaviour.fizzles()) {
            be.empty = occupied ? 0 : be.empty + 1;
            if (be.empty >= MincerTuning.FIZZLE_TICKS) {
                level.blockEvent(pos, state.getBlock(), EVENT_FIZZLE, 0);
            }
        }
    }

    /** Not hit again yet: within disable_time of the last hit. */
    public boolean immune(Entity e) {
        Long last = hitAt.get(e.getId());
        return last != null && level != null && level.getGameTime() - last < behaviour().immuneTicks();
    }

    /** At the explosion: true (and remembered) unless {@code e} is still {@link #immune}. */
    public boolean claim(Entity e) {
        if (behaviour().immuneTicks() <= 0 || level == null) {
            return true;
        }
        if (immune(e)) {
            return false;
        }
        hitAt.values().removeIf(t -> level.getGameTime() - t >= behaviour().immuneTicks());
        hitAt.put(e.getId(), level.getGameTime());
        return true;
    }

    /**
     * The explosion's hit, by this type's damage (armour resolves it as shock, thermal, chemical...); a Burner sets
     * it alight; a mincer kill tears the body apart ({@code body_tear}): gore everywhere, no slow death pose.
     */
    private void hit(ServerLevel level, LivingEntity living, Vec3 c, float hit) {
        if (hit <= 0.0F) {
            return;
        }
        living.hurt(new DamageSource(level.registryAccess().registryOrThrow(Registries.DAMAGE_TYPE)
                .getHolderOrThrow(type.damage()), c), hit);
        level.playSound(null, living.getX(), living.getY(), living.getZ(), type.sounds().hit().get(),
                SoundSource.HOSTILE, 1.0F, 1.0F);
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
        if (behaviour() instanceof BlastTuning blast) {
            if (striking()) {
                Vec3 thrown = blast.throwAt(e.getBoundingBox().getCenter().distanceTo(center()), e.getRandom());
                if (!thrown.equals(Vec3.ZERO)) {
                    e.setDeltaMovement(thrown);
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

    @Override
    public void setRemoved() {
        super.setRemoved();
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
