package com.wf.wflib.recon.api;

import com.wf.wflib.missile.api.MissileEntity;
import com.wf.wflib.round.api.RocketEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.UUID;

/** Targets that are loaded entities: missiles, rockets and players. */
public final class EntityTargetSource implements TargetSource {

    @Override
    public void collect(ServerLevel level, AABB volume, TargetSink sink) {
        List<MissileEntity> missiles = level.getEntitiesOfClass(MissileEntity.class, volume, e -> !e.isRemoved());
        for (int i = 0; i < missiles.size(); i++) {
            sink.accept(missile(missiles.get(i)));
        }
        List<RocketEntity> rockets = level.getEntitiesOfClass(RocketEntity.class, volume, e -> !e.isRemoved());
        for (int i = 0; i < rockets.size(); i++) {
            RocketEntity rocket = rockets.get(i);
            UUID team = rocket.interceptTeamId();
            sink.accept(snapshot(rocket, ContactClass.MISSILE, SignatureProviders.of(rocket), EmconState.SILENT,
                    team == null ? 0L : SourceIds.of(team), false));
        }
        for (ServerPlayer player : level.players()) {
            if (!player.isSpectator() && !player.isRemoved() && volume.contains(player.position())) {
                sink.accept(player(player));
            }
        }
    }

    private static TargetSnapshot missile(MissileEntity missile) {
        UUID team = missile.getTeamId();
        return snapshot(missile, ContactClass.MISSILE, SignatureProviders.of(missile), EmconState.ACTIVE,
                team == null ? 0L : SourceIds.of(team), false);
    }

    private static TargetSnapshot player(ServerPlayer player) {
        return snapshot(player, ContactClass.PLAYER, SignatureProviders.of(player), EmconState.ACTIVE, 0L, false);
    }

    /**
     * Velocity from the last tick's displacement rather than {@code getDeltaMovement}, which for a pathing mob is
     * the impulse it was given and not the distance it went.
     */
    public static TargetSnapshot snapshot(Entity entity, ContactClass kind, Signature signature,
                                           EmconState emcon, long iffCode, boolean buried) {
        boolean wet = inWater(entity);
        return new TargetSnapshot(SourceIds.of(entity), kind,
                entity.getX(), entity.getY() + entity.getBbHeight() * 0.5, entity.getZ(),
                entity.getX() - entity.xo, entity.getY() - entity.yo, entity.getZ() - entity.zo,
                signature, CountermeasureProviders.of(entity), emcon, iffCode,
                buried || (wet && submerged(entity)), wet);
    }

    /**
     * The fluid state rather than {@code Entity.isInWater}, which is set in {@code baseTick} and so is stale
     * for anything moving under its own guidance: a torpedo never reports itself wet.
     */
    private static boolean inWater(Entity entity) {
        return entity.level().getFluidState(entity.blockPosition()).is(FluidTags.WATER);
    }

    /** Under, not merely in: this is what makes radar and thermal blind, and both already gate on it. */
    private static boolean submerged(Entity entity) {
        return entity.level().getFluidState(
                BlockPos.containing(entity.getX(), entity.getEyeY(), entity.getZ())).is(FluidTags.WATER);
    }

    /**
     * The signatures this mod's own entities present, at the bottom of the chain so anything registered later takes
     * precedence.
     */
    public static final class BuiltIn implements SignatureProvider {

        @Override
        @Nullable
        public Signature of(Entity entity) {
            if (entity instanceof MissileEntity missile) {
                return ReconSignatures.missile(missile.signature().getRcs(), missile.isSubmergedMedium());
            }
            if (entity instanceof Player) {
                return ReconSignatures.player();
            }
            return null;
        }
    }
}
