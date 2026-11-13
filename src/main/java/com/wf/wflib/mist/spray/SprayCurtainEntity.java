package com.wf.wflib.mist.spray;

import com.wf.wflib.compat.api.WarforgeCompat;
import com.wf.wflib.mist.MistSystem;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.extensions.common.IClientFluidTypeExtensions;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.BitSet;
import java.util.HashSet;
import java.util.List;
import java.util.UUID;

/**
 * Liquid sinking over a disc of columns, {@code speed} layers per tick. Per column: {@link SprayEffect} of the fluid
 * (default {@link SprayEffect#plain}); STOP, a claim-protected edit or an unloaded chunk ends the column. Entities in
 * the disc get the payload's {@link SprayEntityEffect} once each (not claim-filtered); default none.
 */
public class SprayCurtainEntity extends Entity {

    /** Depth cap below the release layer. */
    public static final int MAX_FALL = 160;
    private static final int PARTICLES_PER_TICK = 24;

    private static final EntityDataAccessor<Integer> RADIUS =
            SynchedEntityData.defineId(SprayCurtainEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> FALLEN =
            SynchedEntityData.defineId(SprayCurtainEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<String> FLUID =
            SynchedEntityData.defineId(SprayCurtainEntity.class, EntityDataSerializers.STRING);

    /** Disc column offsets, {dx, dz} interleaved. */
    private int[] columns = new int[0];
    private BitSet stopped = new BitSet();
    private int speed = 1;
    private UUID igniterFaction;
    private final HashSet<UUID> touched = new HashSet<>();

    public SprayCurtainEntity(EntityType<? extends SprayCurtainEntity> type, Level level) {
        super(type, level);
        this.noPhysics = true;
        this.setNoGravity(true);
    }

    public static SprayCurtainEntity spawn(Level level, Fluid fluid, Vec3 at, int radius, int speed,
                                           UUID igniterFaction) {
        SprayCurtainEntity spray = new SprayCurtainEntity(MistSystem.SPRAY_CURTAIN.get(), level);
        spray.moveTo(at.x, at.y, at.z);
        spray.setRadius(radius);
        spray.entityData.set(FLUID, BuiltInRegistries.FLUID.getKey(fluid).toString());
        spray.speed = Math.max(1, speed);
        spray.igniterFaction = igniterFaction;
        level.addFreshEntity(spray);
        return spray;
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        builder.define(RADIUS, 0);
        builder.define(FALLEN, 0);
        builder.define(FLUID, "minecraft:empty");
    }

    private void setRadius(int radius) {
        this.entityData.set(RADIUS, radius);
        int r2 = radius * radius;
        IntArrayList out = new IntArrayList();
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dz = -radius; dz <= radius; dz++) {
                if (dx * dx + dz * dz <= r2) {
                    out.add(dx);
                    out.add(dz);
                }
            }
        }
        this.columns = out.toIntArray();
    }

    public int radius() {
        return this.entityData.get(RADIUS);
    }

    public int fallen() {
        return this.entityData.get(FALLEN);
    }

    public Fluid fluid() {
        return BuiltInRegistries.FLUID.get(ResourceLocation.parse(this.entityData.get(FLUID)));
    }

    @Override
    public void tick() {
        super.tick();
        if (this.level().isClientSide) {
            mist();
            return;
        }
        ServerLevel level = (ServerLevel) this.level();
        Fluid fluid = fluid();
        SprayPayload payload = SprayPayloads.get(fluid);
        SprayEffect effect = payload == null ? null : payload.blocks();
        int n = this.columns.length / 2;
        int fallen = fallen();
        for (int step = 0; step < this.speed; step++, fallen++) {
            int y = this.getBlockY() - fallen;
            if (fallen > MAX_FALL || y < level.getMinBuildHeight()) {
                this.discard();
                return;
            }
            layer(level, fluid, effect, y);
            if (payload != null && payload.entities() != null) {
                entities(level, fluid, payload.entities(), y);
            }
            if (this.stopped.cardinality() >= n) {
                this.discard();
                return;
            }
        }
        this.entityData.set(FALLEN, fallen);
    }

    private void entities(ServerLevel level, Fluid fluid, SprayEntityEffect effect, int y) {
        double r = radius() + 0.5;
        AABB box = new AABB(this.getX() - r, y, this.getZ() - r, this.getX() + r, y + 1, this.getZ() + r);
        for (Entity entity : level.getEntities(this, box, e -> !(e instanceof SprayCurtainEntity))) {
            double dx = entity.getX() - this.getX();
            double dz = entity.getZ() - this.getZ();
            if (dx * dx + dz * dz <= r * r && this.touched.add(entity.getUUID())) {
                effect.affect(level, entity, fluid);
            }
        }
    }

    private void layer(ServerLevel level, Fluid fluid, SprayEffect effect, int y) {
        int n = this.columns.length / 2;
        List<SprayEffect.Touch> edits = new ArrayList<>();
        IntArrayList editColumns = new IntArrayList();
        BlockPos.MutableBlockPos at = new BlockPos.MutableBlockPos();
        for (int i = this.stopped.nextClearBit(0); i < n; i = this.stopped.nextClearBit(i + 1)) {
            at.set(this.getBlockX() + this.columns[2 * i], y, this.getBlockZ() + this.columns[2 * i + 1]);
            if (!level.isLoaded(at)) {
                this.stopped.set(i);
                continue;
            }
            BlockState state = level.getBlockState(at);
            SprayEffect.Touch touch = effect == null ? SprayEffect.plain(level, at, state)
                    : effect.touch(level, at, state, fluid);
            if (touch.to() != null) {
                edits.add(touch);
                editColumns.add(i);
            }
            if (touch.stop()) {
                this.stopped.set(i);
            }
        }
        if (edits.isEmpty()) {
            return;
        }
        HashSet<BlockPos> allowed = new HashSet<>();
        for (SprayEffect.Touch edit : edits) {
            allowed.add(edit.at());
        }
        WarforgeCompat.filterClaimProtected(level, this.igniterFaction, allowed);
        for (int k = 0; k < edits.size(); k++) {
            SprayEffect.Touch edit = edits.get(k);
            if (allowed.contains(edit.at())) {
                level.setBlock(edit.at(), edit.to(), Block.UPDATE_ALL);
            } else {
                this.stopped.set(editColumns.getInt(k));
            }
        }
    }

    private void mist() {
        Fluid fluid = fluid();
        if (fluid == Fluids.EMPTY) {
            return;
        }
        SprayPayload payload = SprayPayloads.get(fluid);
        int rgb = (payload != null && payload.color() != -1 ? payload.color()
                : IClientFluidTypeExtensions.of(fluid).getTintColor()) & 0xFFFFFF;
        DustParticleOptions dust = new DustParticleOptions(
                new Vector3f((rgb >> 16 & 255) / 255.0f, (rgb >> 8 & 255) / 255.0f, (rgb & 255) / 255.0f), 2.0f);
        int radius = Math.max(1, radius());
        double y = this.getY() - fallen();
        for (int k = 0; k < PARTICLES_PER_TICK; k++) {
            double a = this.random.nextDouble() * Math.PI * 2.0;
            double r = Math.sqrt(this.random.nextDouble()) * radius;
            this.level().addParticle(dust, this.getX() + Math.cos(a) * r, y + this.random.nextDouble() * 2.0,
                    this.getZ() + Math.sin(a) * r, 0.0, -0.05, 0.0);
        }
    }

    @Override
    public boolean isPickable() {
        return false;
    }

    @Override
    public boolean isAttackable() {
        return false;
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
        setRadius(tag.getInt("Radius"));
        this.entityData.set(FALLEN, tag.getInt("Fallen"));
        this.entityData.set(FLUID, tag.getString("Fluid"));
        this.speed = Math.max(1, tag.getInt("Speed"));
        this.stopped = BitSet.valueOf(tag.getLongArray("Stopped"));
        this.igniterFaction = tag.hasUUID("Faction") ? tag.getUUID("Faction") : null;
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
        tag.putInt("Radius", radius());
        tag.putInt("Fallen", fallen());
        tag.putString("Fluid", this.entityData.get(FLUID));
        tag.putInt("Speed", this.speed);
        tag.putLongArray("Stopped", this.stopped.toLongArray());
        if (this.igniterFaction != null) {
            tag.putUUID("Faction", this.igniterFaction);
        }
    }
}
