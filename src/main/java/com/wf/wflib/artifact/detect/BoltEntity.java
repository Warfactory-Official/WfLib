package com.wf.wflib.artifact.detect;

import net.minecraft.core.Direction;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.ThrowableItemProjectile;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;

/**
 * A thrown bolt (STALKER's): bounces off what it hits, losing most of its speed, settles and lies a while. Anomalies
 * take it for a body: it sets them off where nothing else that is not alive does, so it shows where they are.
 */
public class BoltEntity extends ThrowableItemProjectile {
    /** Ticks it lies about once thrown, then it's gone. */
    private static final int LIFE = 20 * 60;
    private boolean settled;

    public BoltEntity(EntityType<? extends BoltEntity> type, Level level) {
        super(type, level);
    }

    public BoltEntity(Level level, LivingEntity thrower) {
        super(ArtifactEntities.BOLT.get(), thrower, level);
    }

    @Override
    protected Item getDefaultItem() {
        return ArtifactEntities.BOLT_ITEM.get();
    }

    @Override
    public void tick() {
        if (settled) {
            // Lying on the ground: no flight, still in anomalies' reach (they pull it about through its motion).
            setDeltaMovement(getDeltaMovement().multiply(0.6D, 1.0D, 0.6D).add(0.0D, -0.04D, 0.0D));
            move(net.minecraft.world.entity.MoverType.SELF, getDeltaMovement());
            if (onGround()) {
                setDeltaMovement(getDeltaMovement().multiply(1.0D, 0.0D, 1.0D));
            }
            if (!level().isClientSide && tickCount > LIFE) {
                discard();
            }
            return;
        }
        super.tick();
        if (!level().isClientSide && tickCount > LIFE) {
            discard();
        }
    }

    @Override
    protected void onHitBlock(BlockHitResult hit) {
        super.onHitBlock(hit);
        Vec3 v = getDeltaMovement();
        Direction face = hit.getDirection();
        // Bounce: the component into the face reversed, all of it damped.
        Vec3 n = new Vec3(face.getStepX(), face.getStepY(), face.getStepZ());
        Vec3 out = v.subtract(n.scale(2.0D * v.dot(n))).scale(0.35D);
        setPos(hit.getLocation().add(n.scale(0.05D)));
        setDeltaMovement(out);
        if (v.length() > 0.15D) {
            level().playSound(null, getX(), getY(), getZ(), SoundEvents.CHAIN_HIT, SoundSource.PLAYERS,
                    (float) Math.min(1.0D, v.length()), 1.4F + random.nextFloat() * 0.3F);
        }
        if (face == Direction.UP && out.length() < 0.12D) {
            settled = true;
            setDeltaMovement(Vec3.ZERO);
        }
    }

    @Override
    protected void onHitEntity(EntityHitResult hit) {
        // A bolt glances off whoever it hits; it isn't a weapon.
        setDeltaMovement(getDeltaMovement().scale(-0.2D));
    }

    @Override
    protected double getDefaultGravity() {
        return 0.05D;
    }
}
