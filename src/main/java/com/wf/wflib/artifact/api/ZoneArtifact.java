package com.wf.wflib.artifact.api;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.FloatTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.function.Predicate;
import com.wf.wflib.artifact.detect.Detection;

/**
 * An artefact lying in the world, X-Ray's {@code CArtefact} out of anyone's hands: a body that falls, bounces and
 * rolls (round, at the model's mean size); anomalies ignore it ({@code ignore_artefacts}).
 * <ul>
 * <li>The Zone's (placed by a site): unseen and untouchable until a player's detector comes near enough
 * ({@link Detection#revealed}), then shown to them and theirs to take with a use. Unseen by everyone, it drifts about
 * its anomaly ({@code SArtefactDetectorsSupport::UpdateOnFrame} on GAMMA's {@code artefact_ways}): pushed towards a
 * point near home while slow or off course, hopping off the ground.</li>
 * <li>Dropped (a player's: {@link ArtifactItem#createEntity}): seen by all, still; taken by walking over or a use.</li>
 * </ul>
 * Never despawns, can't be hurt. Orientation synced as a quaternion, slerped on the client.
 */
public class ZoneArtifact extends Entity {
    private static final EntityDataAccessor<ItemStack> ITEM = SynchedEntityData.defineId(ZoneArtifact.class,
            EntityDataSerializers.ITEM_STACK);
    private static final EntityDataAccessor<Quaternionf> ORIENTATION = SynchedEntityData.defineId(ZoneArtifact.class,
            EntityDataSerializers.QUATERNION);
    private static final EntityDataAccessor<Boolean> DROPPED = SynchedEntityData.defineId(ZoneArtifact.class,
            EntityDataSerializers.BOOLEAN);

    /** Blocks/tick², tick⁻¹. */
    static final double GRAVITY = 0.04D;
    static final double DRAG = 0.98D;
    static final double WATER_GRAVITY = 0.006D;
    static final double WATER_DRAG = 0.8D;
    static final double RESTITUTION = 0.35D;
    /** Ground roll kept per tick. */
    static final double ROLL = 0.9D;
    static final double REST_SPEED = 0.004D;
    /**
     * GAMMA's commonest anomaly field {@code applying_force_xz} / {@code _y}, m/s² (zone configs: 200 / 10). X-Ray
     * applies them as one ODE step's force ({@code CPHElement::applyGravityAccel}, 0.02 s): a kick of F x 0.02 m/s.
     */
    static final double FORCE_XZ = 200.0D;
    static final double FORCE_Y = 10.0D;
    static final double PH_STEP = 0.02D;
    /** Pushed again below this speed (m/s), or this speed along the way (cos 45 deg, unnormalised as X-Ray). */
    static final double SLOW = 0.7D;
    static final double ON_COURSE = 0.7071D;
    /** A new point once this close (m); points within {@link #RANGE} of home. Ground for the hop within 0.75 m. */
    static final double ARRIVE = 2.0D;
    public static final double RANGE = 4.0D;
    static final double HOP_GROUND = 0.75D;
    /** Players this close are asked whether their detector shows it. */
    private static final double SEEN_CHECK = 8.0D;
    static final int PICKUP_DELAY = 40;

    /** Client: shown to the local player (set by the client side, which knows what it holds); server: always. */
    private static volatile Predicate<ZoneArtifact> shownHere = a -> true;

    public static void showWhen(Predicate<ZoneArtifact> shown) {
        shownHere = shown;
    }

    /** Whether this client's detector shows {@code artifact}; true on a server. */
    public static boolean shownHere(ZoneArtifact artifact) {
        return shownHere.test(artifact);
    }


    /** World-frame angular velocity, rad/tick. Server only. */
    private final Vector3f spin = new Vector3f();
    @Nullable
    private BlockPos home;
    @Nullable
    private Vec3 dest;
    private boolean seen;
    private boolean resting;
    private int pickupDelay;

    private final Quaternionf prevOrientation = new Quaternionf();
    private final Quaternionf orientation = new Quaternionf();
    private int lerpSteps;
    private double lerpX;
    private double lerpY;
    private double lerpZ;

    public ZoneArtifact(EntityType<? extends ZoneArtifact> type, Level level) {
        super(type, level);
    }

    /** The Zone's, drifting about {@code home}. */
    public ZoneArtifact(Level level, ItemStack stack, double x, double y, double z, BlockPos home) {
        this(ArtifactContent.ZONE_ARTIFACT.get(), level);
        setItem(stack);
        setPos(x, y, z);
        this.home = home;
        entityData.set(ORIENTATION, new Quaternionf().rotateY(random.nextFloat() * Mth.TWO_PI));
    }

    /** Dropped: where {@code from} (the item entity it replaces) was, moving as it moved. */
    public static ZoneArtifact dropped(Level level, Entity from, ItemStack stack) {
        ZoneArtifact a = new ZoneArtifact(ArtifactContent.ZONE_ARTIFACT.get(), level);
        a.setItem(stack);
        a.entityData.set(DROPPED, true);
        a.setPos(from.getX(), from.getY(), from.getZ());
        a.setDeltaMovement(from.getDeltaMovement());
        a.pickupDelay = PICKUP_DELAY;
        a.entityData.set(ORIENTATION, new Quaternionf().rotateY(a.random.nextFloat() * Mth.TWO_PI));
        return a;
    }

    public ItemStack item() {
        return entityData.get(ITEM);
    }

    public void setItem(ItemStack stack) {
        entityData.set(ITEM, stack.copy());
    }

    @Nullable
    public Artifact artifact() {
        return item().getItem() instanceof ArtifactItem a ? a.artifact() : null;
    }

    public boolean isDropped() {
        return entityData.get(DROPPED);
    }

    /** Drawn and aimable here: dropped ones always; the Zone's for whoever's detector shows them. */
    public boolean shown() {
        return isDropped() || shownHere(this);
    }

    /** The lowest detector rank that finds it: STALKER's lists put the commoner ones on the Echo already. */
    public int detectRank() {
        return item().getItem() instanceof ArtifactItem a ? a.detectRank() : 1;
    }

    /** Client: orientation at {@code partialTick} between the last two ticks. */
    public Quaternionf orientation(float partialTick, Quaternionf out) {
        return prevOrientation.slerp(orientation, partialTick, out);
    }

    /** Collision cube side, blocks: the solid body's mean extent as drawn (an artefact rolls as a ball that size). */
    public static float size(@Nullable Artifact a) {
        return a == null ? 0.3F : Mth.clamp(a.bodySize(), 0.12F, 0.6F);
    }

    @Override
    public EntityDimensions getDimensions(Pose pose) {
        float s = size(artifact());
        return EntityDimensions.fixed(s, s);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        builder.define(ITEM, ItemStack.EMPTY);
        builder.define(ORIENTATION, new Quaternionf());
        builder.define(DROPPED, false);
    }

    @Override
    public void onSyncedDataUpdated(EntityDataAccessor<?> key) {
        super.onSyncedDataUpdated(key);
        if (key == ITEM) {
            refreshDimensions();
        }
        if (key == ORIENTATION && tickCount == 0) {
            orientation.set(entityData.get(ORIENTATION));
            prevOrientation.set(orientation);
        }
    }

    @Override
    public void tick() {
        super.tick();
        if (level().isClientSide) {
            prevOrientation.set(orientation);
            orientation.set(entityData.get(ORIENTATION));
            if (lerpSteps > 0) {
                lerpPositionAndRotationStep(lerpSteps, lerpX, lerpY, lerpZ, getYRot(), getXRot());
                lerpSteps--;
            }
            return;
        }
        if (item().isEmpty()) {
            discard();
            return;
        }
        if (pickupDelay > 0) {
            pickupDelay--;
        }
        if (home == null) {
            home = blockPosition();
        }
        if (!isDropped() && (tickCount <= 1 || tickCount % 4 == 0)) {
            seen = seenByAnyone();
        }
        if (!isDropped() && !seen) {
            drift();
        }
        if (resting && getDeltaMovement().lengthSqr() < 1.0E-8D
                && !level().noCollision(this, getBoundingBox().move(0.0D, -0.02D, 0.0D))) {
            return;
        }
        resting = false;
        step();
    }

    private boolean seenByAnyone() {
        for (Player p : level().players()) {
            if (p.distanceToSqr(this) < SEEN_CHECK * SEEN_CHECK && Detection.revealed(p, this)) {
                return true;
            }
        }
        return false;
    }

    /** {@code SArtefactDetectorsSupport::UpdateOnFrame}: towards the way point, a kick when slow or off course. */
    private void drift() {
        Vec3 at = position();
        if (dest == null || at.distanceTo(dest) < ARRIVE) {
            double a = random.nextDouble() * Mth.TWO_PI;
            double r = Math.sqrt(random.nextDouble()) * RANGE;
            dest = Vec3.atBottomCenterOf(home).add(Math.cos(a) * r, 0.0D, Math.sin(a) * r);
        }
        Vec3 dir = dest.subtract(at).normalize();
        Vec3 v = getDeltaMovement().scale(20.0D);
        if (v.lengthSqr() >= SLOW * SLOW && v.dot(dir) >= ON_COURSE) {
            return;
        }
        boolean ground = !level().noCollision(this, getBoundingBox().expandTowards(0.0D, -HOP_GROUND, 0.0D));
        Vec3 kick = new Vec3(dir.x * FORCE_XZ, (dir.y + (ground ? 1.0D : 0.0D)) * FORCE_Y, dir.z * FORCE_XZ)
                .scale(PH_STEP / 20.0D);
        setDeltaMovement(getDeltaMovement().add(kick));
        resting = false;
    }

    private void step() {
        boolean water = isInWater();
        Vec3 v = getDeltaMovement().add(0.0D, -(water ? WATER_GRAVITY : GRAVITY), 0.0D)
                .scale(water ? WATER_DRAG : DRAG);
        Vec3 from = position();
        move(MoverType.SELF, v);
        Vec3 moved = position().subtract(from);
        v = bounce(v, moved);
        float r = getBbWidth() * 0.5F;
        if (onGround()) {
            v = v.multiply(ROLL, 1.0D, ROLL);
            spin.set((float) v.z, 0.0F, (float) -v.x).div(r);
        } else {
            spin.mul(0.98F);
        }
        setDeltaMovement(v);
        turn();
        if (onGround() && v.horizontalDistance() < REST_SPEED && Math.abs(v.y) < 0.02D) {
            setDeltaMovement(Vec3.ZERO);
            spin.zero();
            resting = true;
        }
    }

    /** Each blocked axis: reflected and damped, a knock (sounded) or a stop. */
    private Vec3 bounce(Vec3 v, Vec3 moved) {
        double[] c = {v.x, v.y, v.z};
        double[] m = {moved.x, moved.y, moved.z};
        for (int axis = 0; axis < 3; axis++) {
            if (Math.abs(c[axis] - m[axis]) < 1.0E-5D) {
                continue;
            }
            double into = Math.abs(c[axis]);
            if (into > 0.08D) {
                knock(into, axis, c[axis]);
                spin.add(new Vector3f(random.nextFloat() - 0.5F, random.nextFloat() - 0.5F,
                        random.nextFloat() - 0.5F).mul((float) into * 4.0F));
            }
            c[axis] = into < 0.05D ? 0.0D : -c[axis] * RESTITUTION;
        }
        return new Vec3(c[0], c[1], c[2]);
    }

    private void knock(double speed, int axis, double along) {
        BlockPos at = BlockPos.containing(position().add(axis == 0 ? Math.signum(along) * 0.1D : 0.0D,
                axis == 1 ? Math.signum(along) * 0.1D : 0.0D, axis == 2 ? Math.signum(along) * 0.1D : 0.0D));
        BlockState state = level().getBlockState(at);
        level().playSound(null, getX(), getY(), getZ(), state.getSoundType().getHitSound(), SoundSource.NEUTRAL,
                (float) Math.min(0.6D, speed * 2.0D), 1.2F + random.nextFloat() * 0.2F);
    }

    private void turn() {
        float angle = spin.length();
        if (angle < 1.0E-5F) {
            return;
        }
        entityData.set(ORIENTATION, new Quaternionf().rotateAxis(angle, spin.x / angle, spin.y / angle,
                spin.z / angle).mul(entityData.get(ORIENTATION)).normalize());
    }

    /** Walked into: dropped and landed, picked up; else nudged along (X-Ray bodies roll off a boot). */
    @Override
    public void playerTouch(Player player) {
        if (level().isClientSide) {
            return;
        }
        if (isDropped()) {
            if (pickupDelay == 0 && onGround()) {
                take(player);
            }
            return;
        }
        Vec3 away = position().subtract(player.position()).multiply(1.0D, 0.0D, 1.0D);
        if (away.lengthSqr() > 1.0E-6D) {
            setDeltaMovement(getDeltaMovement().add(away.normalize().scale(0.04D)).add(0.0D, 0.02D, 0.0D));
            resting = false;
        }
    }

    /** Only what is shown can be aimed at: an unseen artefact can't be found by the crosshair. */
    @Override
    public boolean isPickable() {
        return !isRemoved() && (!level().isClientSide || shown());
    }

    @Override
    public InteractionResult interact(Player player, InteractionHand hand) {
        if (level().isClientSide) {
            return shown() ? InteractionResult.SUCCESS : InteractionResult.PASS;
        }
        if (!isDropped() && !Detection.revealed(player, this)) {
            return InteractionResult.PASS;
        }
        return take(player) ? InteractionResult.CONSUME : InteractionResult.PASS;
    }

    private boolean take(Player player) {
        if (isRemoved() || item().isEmpty()) {
            return false;
        }
        ItemStack stack = item().copy();
        if (!player.getInventory().add(stack)) {
            return false;
        }
        level().playSound(null, getX(), getY(), getZ(), SoundEvents.ITEM_PICKUP, SoundSource.PLAYERS, 0.4F, 0.8F);
        discard();
        return true;
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
    public boolean isIgnoringBlockTriggers() {
        return true;
    }

    @Override
    public boolean canBeHitByProjectile() {
        return false;
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
        entityData.set(DROPPED, tag.getBoolean("dropped"));
        home = NbtUtils.readBlockPos(tag, "home").orElse(null);
        ListTag q = tag.getList("orientation", Tag.TAG_FLOAT);
        if (q.size() == 4) {
            entityData.set(ORIENTATION, new Quaternionf(q.getFloat(0), q.getFloat(1), q.getFloat(2), q.getFloat(3)));
        }
        pickupDelay = tag.getInt("pickupDelay");
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
        if (!item().isEmpty()) {
            tag.put("item", item().save(registryAccess()));
        }
        tag.putBoolean("dropped", isDropped());
        if (home != null) {
            tag.put("home", NbtUtils.writeBlockPos(home));
        }
        Quaternionf q = entityData.get(ORIENTATION);
        ListTag ql = new ListTag();
        for (float f : new float[] {q.x, q.y, q.z, q.w}) {
            ql.add(FloatTag.valueOf(f));
        }
        tag.put("orientation", ql);
        tag.putInt("pickupDelay", pickupDelay);
    }
}
