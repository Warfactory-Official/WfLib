package com.wf.wflib.missile;

import com.wf.wflib.missile.api.MissileFlight;
import com.wf.wflib.missile.api.MissileBuilder;
import com.wf.wflib.missile.api.MissileEntity;
import com.wf.wflib.missile.api.MissileEntity.Phase;
import com.wf.wflib.missile.api.MissileModels;
import com.wf.wflib.missile.flight.ArrivalEstimator;
import com.wf.wflib.missile.api.FlightStages;
import com.wf.wflib.missile.flight.LoiterStage;
import com.wf.wflib.recon.api.ReconOwners;
import com.wf.wflib.warhead.api.Warheads;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.Vec3;
import com.wf.wflib.missile.api.MissileContent;

public final class LaunchConfig {
    public ResourceLocation modelId = MissileModels.defaultId();
    public ResourceLocation warheadId = Warheads.defaultId();
    public boolean highAltitude;
    public double targetX;
    public double targetY;
    public double targetZ;
    public float explosionOffset;
    public double altitudeParam = 24.0;
    public int fragmentCount = MissileEntity.DEFAULT_FRAGMENT_COUNT;
    public double cruiseSpeed = MissileEntity.CRUISE_SPEED;
    // <=0: auto-size to reach target under power (spawn()).
    public int fuelTicks = 0;
    public double turnRate;
    public float health = MissileEntity.DEFAULT_HEALTH;
    public boolean startInCruise;
    public boolean startArmed;
    public ResourceLocation ascentStageId;
    public ResourceLocation cruiseStageId;
    public ResourceLocation attackStageId;
    // Horizontal dir, target->approach side; (0,0) = unconstrained.
    public double attackApproachX;
    public double attackApproachZ;
    public String attackProfile = "SPEED";
    public double approachJoinCap = MissileEntity.DEFAULT_APPROACH_JOIN_CAP;

    public void write(FriendlyByteBuf b) {
        b.writeResourceLocation(modelId);
        b.writeResourceLocation(warheadId);
        b.writeBoolean(highAltitude);
        b.writeDouble(targetX);
        b.writeDouble(targetY);
        b.writeDouble(targetZ);
        b.writeFloat(explosionOffset);
        b.writeDouble(altitudeParam);
        b.writeVarInt(fragmentCount);
        b.writeDouble(cruiseSpeed);
        b.writeVarInt(fuelTicks);
        b.writeDouble(turnRate);
        b.writeFloat(health);
        b.writeBoolean(startInCruise);
        b.writeBoolean(startArmed);
        b.writeResourceLocation(ascentStageId);
        b.writeResourceLocation(cruiseStageId);
        b.writeResourceLocation(attackStageId);
        b.writeDouble(attackApproachX);
        b.writeDouble(attackApproachZ);
        b.writeUtf(attackProfile);
        b.writeDouble(approachJoinCap);
    }

    public static LaunchConfig read(FriendlyByteBuf b) {
        LaunchConfig c = new LaunchConfig();
        c.modelId = b.readResourceLocation();
        c.warheadId = b.readResourceLocation();
        c.highAltitude = b.readBoolean();
        c.targetX = b.readDouble();
        c.targetY = b.readDouble();
        c.targetZ = b.readDouble();
        c.explosionOffset = b.readFloat();
        c.altitudeParam = b.readDouble();
        c.fragmentCount = b.readVarInt();
        c.cruiseSpeed = b.readDouble();
        c.fuelTicks = b.readVarInt();
        c.turnRate = b.readDouble();
        c.health = b.readFloat();
        c.startInCruise = b.readBoolean();
        c.startArmed = b.readBoolean();
        c.ascentStageId = b.readResourceLocation();
        c.cruiseStageId = b.readResourceLocation();
        c.attackStageId = b.readResourceLocation();
        c.attackApproachX = b.readDouble();
        c.attackApproachZ = b.readDouble();
        c.attackProfile = b.readUtf();
        c.approachJoinCap = b.readDouble();
        return c;
    }

    public void save(CompoundTag tag) {
        tag.putString("Model", modelId.toString());
        tag.putString("Warhead", warheadId.toString());
        tag.putBoolean("HighAltitude", highAltitude);
        tag.putDouble("TargetX", targetX);
        tag.putDouble("TargetY", targetY);
        tag.putDouble("TargetZ", targetZ);
        tag.putFloat("ExplosionOffset", explosionOffset);
        tag.putDouble("AltitudeParam", altitudeParam);
        tag.putInt("FragmentCount", fragmentCount);
        tag.putDouble("CruiseSpeed", cruiseSpeed);
        tag.putInt("FuelTicks", fuelTicks);
        tag.putDouble("TurnRate", turnRate);
        tag.putFloat("Health", health);
        tag.putBoolean("StartInCruise", startInCruise);
        tag.putBoolean("StartArmed", startArmed);
        if (ascentStageId != null) {
            tag.putString("AscentStage", ascentStageId.toString());
        }
        if (cruiseStageId != null) {
            tag.putString("CruiseStage", cruiseStageId.toString());
        }
        if (attackStageId != null) {
            tag.putString("AttackStage", attackStageId.toString());
        }
        tag.putDouble("AttackApproachX", attackApproachX);
        tag.putDouble("AttackApproachZ", attackApproachZ);
        tag.putString("AttackProfile", attackProfile);
        tag.putDouble("ApproachJoinCap", approachJoinCap);
    }

    public static LaunchConfig load(CompoundTag tag) {
        LaunchConfig c = new LaunchConfig();
        c.modelId = MissileModels.parse(tag.getString("Model"));
        c.warheadId = Warheads.parse(tag.getString("Warhead"));
        c.highAltitude = tag.getBoolean("HighAltitude");
        c.targetX = tag.getDouble("TargetX");
        c.targetY = tag.getDouble("TargetY");
        c.targetZ = tag.getDouble("TargetZ");
        c.explosionOffset = tag.getFloat("ExplosionOffset");
        c.altitudeParam = tag.getDouble("AltitudeParam");
        c.fragmentCount = tag.getInt("FragmentCount");
        c.cruiseSpeed = tag.getDouble("CruiseSpeed");
        c.fuelTicks = tag.getInt("FuelTicks");
        c.turnRate = tag.getDouble("TurnRate");
        c.health = tag.getFloat("Health");
        c.startInCruise = tag.getBoolean("StartInCruise");
        c.startArmed = tag.getBoolean("StartArmed");
        if (tag.contains("AscentStage")) {
            c.ascentStageId = FlightStages.parse(Phase.ASCEND, tag.getString("AscentStage"));
        }
        if (tag.contains("CruiseStage")) {
            c.cruiseStageId = FlightStages.parse(Phase.CRUISE, tag.getString("CruiseStage"));
        }
        if (tag.contains("AttackStage")) {
            c.attackStageId = FlightStages.parse(Phase.ATTACK, tag.getString("AttackStage"));
        }
        c.attackApproachX = tag.getDouble("AttackApproachX");
        c.attackApproachZ = tag.getDouble("AttackApproachZ");
        if (tag.contains("AttackProfile")) {
            c.attackProfile = tag.getString("AttackProfile");
        }
        if (tag.contains("ApproachJoinCap")) {
            c.approachJoinCap = tag.getDouble("ApproachJoinCap");
        }
        return c;
    }

    public void spawn(ServerLevel level, BlockPos pos, MissileDispenserBlockEntity dispenser) {
        ResourceLocation model = MissileModels.exists(modelId) ? modelId : MissileModels.defaultId();
        ResourceLocation warhead = Warheads.exists(warheadId) ? warheadId : Warheads.defaultId();
        double speed = cruiseSpeed > 1.0E-3 ? cruiseSpeed : MissileEntity.CRUISE_SPEED;
        int frags = Math.max(0, fragmentCount);
        float hp = health > 0.0f ? health : MissileEntity.DEFAULT_HEALTH;

        Vec3 target = new Vec3(targetX, targetY, targetZ);
        double cruiseAltitudeY = highAltitude ? altitudeParam : pos.getY() + altitudeParam;
        int loiter = LoiterStage.loiterTicksOf(cruiseStageId);
        Vec3 approachDir = (attackApproachX != 0.0 || attackApproachZ != 0.0)
                ? new Vec3(attackApproachX, 0.0, attackApproachZ) : null;
        int fuel = fuelTicks > 0 ? fuelTicks
                : ArrivalEstimator.fuelToReach(Vec3.atCenterOf(pos), target, speed,
                MissileFlight.ascentSpeedFor(speed), cruiseAltitudeY, loiter, approachDir, approachJoinCap);

        MissileBuilder builder = MissileEntity.builder(MissileContent.STEALTH_MISSILE.get(), level)
                .target(target)
                .model(model)
                .detonation(warhead)
                .explosionOffset(explosionOffset)
                .fragmentCount(frags)
                .cruiseSpeed(speed)
                .fuel(MissileEntity.FuelType.SOLID, fuel)
                .health(hp)
                .controlId(dispenser.getControlId())
                .teamId(ReconOwners.owningAt(level, pos))
                .ascentStage(FlightStages.exists(Phase.ASCEND, ascentStageId)
                        ? ascentStageId : FlightStages.defaultId(Phase.ASCEND))
                .cruiseStage(FlightStages.exists(Phase.CRUISE, cruiseStageId)
                        ? cruiseStageId : FlightStages.defaultId(Phase.CRUISE))
                .attackStage(FlightStages.exists(Phase.ATTACK, attackStageId)
                        ? attackStageId : FlightStages.defaultId(Phase.ATTACK));
        if (highAltitude) {
            builder.highAltitude(altitudeParam);
        } else {
            builder.terrainFollow(altitudeParam);
        }
        if (turnRate > 0.0) {
            builder.turnRate(turnRate);
        }
        if (attackApproachX != 0.0 || attackApproachZ != 0.0) {
            builder.attackFrom(attackApproachX, attackApproachZ);
        }
        builder.attackProfile(com.wf.wflib.missile.api.AttackProfile.byName(attackProfile));
        builder.approachJoinCap(approachJoinCap);
        if (startInCruise) {
            builder.startInCruise();
        }
        if (startArmed) {
            builder.startArmed();
        }

        MissileEntity missile = builder.build();
        Vec3 spawn = Vec3.atCenterOf(pos).add(0.0, 2.0, 0.0);
        missile.moveTo(spawn.x, spawn.y, spawn.z, 0.0f, 0.0f);
        level.addFreshEntity(missile);
    }
}
