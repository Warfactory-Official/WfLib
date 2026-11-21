package com.wf.wflib.contract.api;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/**
 * What a vehicle mod gives bases: parked vehicles in the bays, and a soldier who climbs in and drives off when the base is
 * alerted. Registered with {@code SiteVehicles.provider}; a base without a provider simply has empty bays.
 */
public interface VehicleProvider {
    /** Parks one vehicle of class {@code kind} ("light" or "armour") at {@code at} facing {@code yaw}; null when this mod has none of that class. */
    @Nullable
    Entity park(ServerLevel level, Vec3 at, float yaw, String kind);

    /**
     * Seats {@code driver} in {@code vehicle} and sends it after {@code target} (null: wherever the soldiers' own AI wants it to
     * go). The soldier is already beside it. False when it could not be done (no seat, taken, wrecked): the soldier carries on on foot.
     */
    boolean mount(ServerPlayer driver, Entity vehicle, @Nullable Vec3 target);
}
