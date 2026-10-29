package com.wf.wflib.artifact.detect;

import com.wf.wflib.WFSounds;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.FloatTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.Set;
import java.util.UUID;

/**
 * A thrown bolt (STALKER's), carrying the stack it was thrown from: tumbles in flight, bounces off blocks (friction
 * turning slide into spin), glances off bodies, rolls and slides to rest, then lies flat. Sinks slowly in water. Taken
 * back by walking over it or with a use. Anomalies take it for a body ({@code AnomalyBlockEntity}: it sets them off),
 * and what they do to its motion wakes it.
 *
 * <p>Orientation: the item sprite's plane is local XY (normal +Z); synced as a quaternion, slerped on the client.
 */
public class BoltEntity extends Entity {
    private static final EntityDataAccessor<ItemStack> ITEM = SynchedEntityData.defineId(BoltEntity.class,
            EntityDataSerializers.ITEM_STACK);
    private static final EntityDataAccessor<Quaternionf> ORIENTATION = SynchedEntityData.defineId(BoltEntity.class,
            EntityDataSerializers.QUATERNION);
    private static final EntityDataAccessor<Boolean> RESTING = SynchedEntityData.defineId(BoltEntity.class,
            EntityDataSerializers.BOOLEAN);

    /** Blocks/tick², tick⁻¹ (an item's). */
    static final double GRAVITY = 0.04D;
    static final double DRAG = 0.99D;
    static final double WATER_GRAVITY = 0.004D;
    static final double WATER_DRAG = 0.8D;
    /** Share of the speed into a face it leaves with. */
    static final double RESTITUTION = 0.3D;
    /** Coulomb: tangential speed lost per unit of normal impulse. */
    static final double FRICTION = 0.45D;
    /** Ground friction per tick while sliding. */
    static final double SLIDE = 0.82D;
    /** Rolling radius, blocks: tangential speed -> spin. */
    static final float RADIUS = 0.05F;
    static final float MAX_SPIN = 1.2F;
    static final double REST_SPEED = 0.012D;
    static final float REST_SPIN = 0.06F;
    /** Item lifetime (vanilla's 5 minutes). */
    static final int LIFE = 6000;
    static final int PICKUP_DELAY = 10;
    /** The thrower can't be hit by their own bolt this long. */
    private static final int OWNER_GRACE = 5;
    private static final double THROW_SPEED = 1.1D;
    private static final Set<SoundType> METAL = Set.of(SoundType.METAL, SoundType.NETHERITE_BLOCK, SoundType.COPPER,
            SoundType.ANVIL, SoundType.CHAIN, SoundType.LANTERN, SoundType.COPPER_GRATE, SoundType.COPPER_BULB,
            SoundType.HEAVY_CORE);

    /** World-frame angular velocity, rad/tick. Server only. */
    private final Vector3f spin = new Vector3f();
    @Nullable
    private UUID owner;
    private int age;
    private int pickupDelay;
    private int still;

    /** Client: last tick's orientation and this tick's, for the renderer's slerp. */
    private final Quaternionf prevOrientation = new Quaternionf();
    private final Quaternionf orientation = new Quaternionf();
    private int lerpSteps;
    private double lerpX;
    private double lerpY;
    private double lerpZ;

    public BoltEntity(EntityType<? extends BoltEntity> type, Level level) {
        super(type, level);
    }

    /** Thrown by {@code thrower}: out of the eyes along their look, plus their own motion, spinning. */
    public BoltEntity(Level level, Player thrower, ItemStack stack) {
        this(ArtifactEntities.BOLT.get(), level);
        setItem(stack);
        owner = thrower.getUUID();
        pickupDelay = PICKUP_DELAY;
        Vec3 look = thrower.getLookAngle();
        setPos(thrower.getX(), thrower.getEyeY() - 0.1D, thrower.getZ());
        Vec3 own = thrower.getDeltaMovement();
        setDeltaMovement(look.scale(THROW_SPEED).add(own.x, thrower.onGround() ? 0.0D : own.y, own.z));
        Vector3f axis = new Vector3f(random.nextFloat() - 0.5F, random.nextFloat() - 0.5F,
                random.nextFloat() - 0.5F).normalize();
        spin.set(axis.mul(0.3F + random.nextFloat() * 0.4F));
        Quaternionf q = new Quaternionf().rotateY(-thrower.getYRot() * Mth.DEG_TO_RAD)
                .rotateX(thrower.getXRot() * Mth.DEG_TO_RAD);
        entityData.set(ORIENTATION, q);
    }

    public ItemStack item() {
        return entityData.get(ITEM);
    }

    public void setItem(ItemStack stack) {
        entityData.set(ITEM, stack.copy());
    }

    public boolean resting() {
        return entityData.get(RESTING);
    }

    /** As synced: the server's latest. */
    public Quaternionf orientation() {
        return new Quaternionf(entityData.get(ORIENTATION));
    }

    /** Client: orientation at {@code partialTick} between the last two ticks. */
    public Quaternionf orientation(float partialTick, Quaternionf out) {
        return prevOrientation.slerp(orientation, partialTick, out);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        builder.define(ITEM, ItemStack.EMPTY);
        builder.define(ORIENTATION, new Quaternionf());
        builder.define(RESTING, false);
    }

    @Override
    public void onSyncedDataUpdated(EntityDataAccessor<?> key) {
        super.onSyncedDataUpdated(key);
        if (key == ORIENTATION && tickCount == 0) {
            orientation.set(entityData.get(ORIENTATION));
            prevOrientation.set(orientation);
        }
    }

    @Override
    public void tick() {
        super.tick();
        if (level().isClientSide) {
            clientTick();
            return;
        }
        if (++age >= LIFE || item().isEmpty()) {
            discard();
            return;
        }
        if (pickupDelay > 0) {
            pickupDelay--;
        }
        if (resting()) {
            if (getDeltaMovement().lengthSqr() < 1.0E-8D && !level().noCollision(this, getBoundingBox()
                    .move(0.0D, -0.02D, 0.0D))) {
                return;
            }
            entityData.set(RESTING, false);
            still = 0;
        }
        step();
    }

    private void clientTick() {
        prevOrientation.set(orientation);
        orientation.set(entityData.get(ORIENTATION));
        if (lerpSteps > 0) {
            lerpPositionAndRotationStep(lerpSteps, lerpX, lerpY, lerpZ, getYRot(), getXRot());
            lerpSteps--;
        }
    }

    /** One tick of flight or slide: forces, the move, then whatever it ran into. */
    private void step() {
        boolean water = isInWater();
        Vec3 v = getDeltaMovement().add(0.0D, -(water ? WATER_GRAVITY : GRAVITY), 0.0D)
                .scale(water ? WATER_DRAG : DRAG);
        spin.mul(water ? 0.8F : 0.98F);
        if (!onGround()) {
            v = glance(v);
        }
        Vec3 from = position();
        move(MoverType.SELF, v);
        Vec3 moved = position().subtract(from);
        v = bounce(v, moved, water);
        if (onGround()) {
            v = v.multiply(SLIDE, 1.0D, SLIDE);
            // Rolling: spin about the horizontal axis across its slide.
            Vector3f roll = new Vector3f((float) v.z, 0.0F, (float) -v.x).div(RADIUS);
            spin.lerp(roll, 0.3F);
        }
        clampSpin();
        setDeltaMovement(v);
        turn();
        if (onGround() && v.horizontalDistance() < REST_SPEED && Math.abs(v.y) < 0.02D
                && spin.length() < REST_SPIN) {
            if (++still > 4) {
                lieFlat();
            }
        } else {
            still = 0;
        }
    }

    /** A body in the way this tick: the bolt glances off it, a third of its speed left; nobody is hurt. */
    private Vec3 glance(Vec3 v) {
        EntityHitResult hit = ProjectileUtil.getEntityHitResult(level(), this, position(), position().add(v),
                getBoundingBox().expandTowards(v).inflate(0.3D), e -> e.isPickable() && !e.isSpectator()
                        && !(e instanceof BoltEntity) && !(e instanceof ZoneArtifact)
                        && (age > OWNER_GRACE || !e.getUUID().equals(owner)));
        if (hit == null) {
            return v;
        }
        Vec3 n = position().subtract(hit.getEntity().getBoundingBox().getCenter()).normalize();
        double into = v.dot(n);
        if (into >= 0.0D) {
            return v;
        }
        impact(-into, WFSounds.BOLT_IMPACT_DEFAULT.get());
        tumble(-into);
        return v.subtract(n.scale(2.0D * into)).scale(RESTITUTION);
    }

    /**
     * What the move stopped, per axis: that component reflected and damped, the rest rubbed down by friction, the
     * rub becoming spin.
     */
    Vec3 bounce(Vec3 v, Vec3 moved, boolean water) {
        double[] c = {v.x, v.y, v.z};
        double[] m = {moved.x, moved.y, moved.z};
        for (int axis = 0; axis < 3; axis++) {
            if (Math.abs(c[axis] - m[axis]) < 1.0E-5D) {
                continue;
            }
            double into = Math.abs(c[axis]);
            boolean knock = into > 0.06D;
            if (knock) {
                impact(into, water ? WFSounds.BOLT_IMPACT_WATER.get() : material(axis, c[axis]));
            }
            c[axis] = into < 0.05D ? 0.0D : -c[axis] * RESTITUTION;
            int a = (axis + 1) % 3;
            int b = (axis + 2) % 3;
            double tangential = Math.hypot(c[a], c[b]);
            if (tangential > 1.0E-6D) {
                double lost = Math.min(tangential, FRICTION * (1.0D + RESTITUTION) * into);
                double k = (tangential - lost) / tangential;
                // Rub along the face: spin about (normal x slide).
                float[] n = new float[3];
                n[axis] = (float) -Math.signum(v.get(Direction.Axis.VALUES[axis]));
                Vector3f slide = new Vector3f((float) (axis == 0 ? 0 : c[0]), (float) (axis == 1 ? 0 : c[1]),
                        (float) (axis == 2 ? 0 : c[2])).normalize();
                spin.add(new Vector3f(n[0], n[1], n[2]).cross(slide).mul((float) (lost / RADIUS * 0.5D)));
                c[a] *= k;
                c[b] *= k;
            }
            if (knock) {
                tumble(into);
            }
        }
        return new Vec3(c[0], c[1], c[2]);
    }

    /** A knock sets it turning some random way, harder the harder the knock. */
    private void tumble(double speed) {
        Vector3f axis = new Vector3f(random.nextFloat() - 0.5F, random.nextFloat() - 0.5F,
                random.nextFloat() - 0.5F);
        if (axis.lengthSquared() > 1.0E-6F) {
            spin.add(axis.normalize().mul((float) speed * 2.0F));
        }
    }

    private void clampSpin() {
        float s = spin.length();
        if (s > MAX_SPIN) {
            spin.mul(MAX_SPIN / s);
        }
    }

    private void turn() {
        float angle = spin.length();
        if (angle < 1.0E-5F) {
            return;
        }
        Quaternionf q = new Quaternionf().rotateAxis(angle, spin.x / angle, spin.y / angle, spin.z / angle)
                .mul(entityData.get(ORIENTATION)).normalize();
        entityData.set(ORIENTATION, q);
    }

    /** At rest: flat on the ground, its heading kept. */
    void lieFlat() {
        Vector3f x = entityData.get(ORIENTATION).transform(new Vector3f(1.0F, 0.0F, 0.0F));
        float yaw = (float) Math.atan2(-x.z, x.x);
        entityData.set(ORIENTATION, new Quaternionf().rotateY(yaw).rotateX(-Mth.HALF_PI));
        entityData.set(RESTING, true);
        setDeltaMovement(Vec3.ZERO);
        spin.zero();
        still = 0;
    }

    private void impact(double speed, SoundEvent sound) {
        level().playSound(null, getX(), getY(), getZ(), sound, SoundSource.PLAYERS,
                (float) Math.min(1.0D, speed * 2.0D), 0.9F + random.nextFloat() * 0.2F);
    }

    /** The block it hit on {@code axis} moving {@code speed} along it: "Oleh's Bolt Impact Sounds" by surface. */
    private SoundEvent material(int axis, double speed) {
        BlockPos at = BlockPos.containing(position().add(axis == 0 ? Math.signum(speed) * 0.1D : 0.0D,
                axis == 1 ? Math.signum(speed) * 0.1D : 0.0D, axis == 2 ? Math.signum(speed) * 0.1D : 0.0D));
        BlockState state = level().getBlockState(at);
        if (METAL.contains(state.getSoundType())) {
            return WFSounds.BOLT_IMPACT_METAL.get();
        }
        if (state.is(BlockTags.MINEABLE_WITH_SHOVEL)) {
            return WFSounds.BOLT_IMPACT_DIRT.get();
        }
        if (state.is(BlockTags.MINEABLE_WITH_PICKAXE)) {
            return WFSounds.BOLT_IMPACT_CONCRETE.get();
        }
        return WFSounds.BOLT_IMPACT_DEFAULT.get();
    }

    /** Walked over once it has landed: the same stack goes back. */
    @Override
    public void playerTouch(Player player) {
        if (!level().isClientSide && pickupDelay == 0 && (onGround() || resting())) {
            take(player);
        }
    }

    @Override
    public InteractionResult interact(Player player, InteractionHand hand) {
        if (level().isClientSide) {
            return InteractionResult.SUCCESS;
        }
        return take(player) ? InteractionResult.CONSUME : InteractionResult.PASS;
    }

    private boolean take(Player player) {
        if (isRemoved() || !player.getInventory().add(item().copy())) {
            return false;
        }
        player.take(this, 1);
        discard();
        return true;
    }

    @Override
    public boolean isPickable() {
        return !isRemoved();
    }

    @Override
    public boolean canBeHitByProjectile() {
        return false;
    }

    @Override
    public boolean hurt(DamageSource source, float amount) {
        return false;
    }

    @Override
    public boolean skipAttackInteraction(Entity attacker) {
        return true;
    }

    @Override
    public void lerpTo(double x, double y, double z, float yRot, float xRot, int steps) {
        lerpX = x;
        lerpY = y;
        lerpZ = z;
        lerpSteps = 3;
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
        setItem(ItemStack.parseOptional(registryAccess(), tag.getCompound("item")));
        ListTag q = tag.getList("orientation", Tag.TAG_FLOAT);
        if (q.size() == 4) {
            entityData.set(ORIENTATION, new Quaternionf(q.getFloat(0), q.getFloat(1), q.getFloat(2), q.getFloat(3)));
        }
        ListTag w = tag.getList("spin", Tag.TAG_FLOAT);
        if (w.size() == 3) {
            spin.set(w.getFloat(0), w.getFloat(1), w.getFloat(2));
        }
        entityData.set(RESTING, tag.getBoolean("resting"));
        age = tag.getInt("age");
        pickupDelay = tag.getInt("pickupDelay");
        owner = tag.hasUUID("owner") ? tag.getUUID("owner") : null;
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
        if (!item().isEmpty()) {
            tag.put("item", item().save(registryAccess()));
        }
        Quaternionf q = entityData.get(ORIENTATION);
        ListTag ql = new ListTag();
        for (float f : new float[] {q.x, q.y, q.z, q.w}) {
            ql.add(FloatTag.valueOf(f));
        }
        tag.put("orientation", ql);
        ListTag wl = new ListTag();
        for (float f : new float[] {spin.x, spin.y, spin.z}) {
            wl.add(FloatTag.valueOf(f));
        }
        tag.put("spin", wl);
        tag.putBoolean("resting", resting());
        tag.putInt("age", age);
        tag.putInt("pickupDelay", pickupDelay);
        if (owner != null) {
            tag.putUUID("owner", owner);
        }
    }
}
