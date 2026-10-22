package com.wf.wflib.ordnance;

import com.wf.wflib.MissileEntity;
import com.wf.wflib.MissileModels;
import com.wf.wflib.api.ThreatKind;
import com.wf.wflib.flight.AttackProfile;
import com.wf.wflib.flight.FlightStageRegistry;
import com.wf.wflib.item.MissilePreset;
import com.wf.wflib.kinetic.KineticPreset;
import com.wf.wflib.missile.SeekerMode;
import com.wf.wflib.warhead.WarheadRegistry;
import net.minecraft.resources.ResourceLocation;

/**
 * SI builders for air/vehicle-launched rockets, bombs and missiles; register nothing. Inputs SI (kg, N, motor ticks,
 * Cd*A m^2, blast radius blocks); accel = T/m/400, quadratic drag k = CdA/m, cruise = sqrt(accel/k), turn = maxG*g
 * authority from {@code refSpeed}; homing seekers glide after burn-out.
 * Rocket => {@code rocket_<id>}, dumb bomb => {@code bomb_<id>} (kinetic); missile => {@code missile_<id>}, guided
 * bomb => {@code bomb_<id>} (missile). Size tags: {@code wflib:missile/<size>}, {@code wflib:bomb/<size>},
 * {@code wflib:rocket/<calibre>}.
 */
public final class VehicleOrdnance {

    static final double G = 9.8 / 400.0;
    private static final double ACTIVE_RADAR_RANGE = 1024.0;
    private static final ResourceLocation DIRECT = FlightStageRegistry.rl("direct");
    private static final ResourceLocation ATTACK = FlightStageRegistry.rl("attack");
    private static final ResourceLocation AIR_LAUNCH = FlightStageRegistry.rl("air_launch");

    private VehicleOrdnance() {
    }

    private static ResourceLocation warhead(ThreatKind threat, float blast) {
        return blast <= 0 ? WarheadRegistry.rl("inert")
                : threat.isShapedCharge() ? WarheadRegistry.rl("shaped_charge") : WarheadRegistry.defaultId();
    }

    public static KineticPreset rocket(ResourceLocation id, float calibre, double mass, double thrust,
                                       int burnTicks, double cdA, float damage, float blast) {
        return KineticPreset.builder(id, MissileModels.rl("micro"), warhead(ThreatKind.HE, blast))
                .item("rocket_" + id.getPath())
                .threat(ThreatKind.HE)
                .blast(blast, true)
                .speed(10.0)
                .drag(0.0)
                .quadraticDrag(cdA / mass)
                .motor(thrust / mass / 400.0, burnTicks)
                .gravity(G)
                .life(1200)
                .impactDamage(damage)
                .headshot(1.5)
                .caliber(calibre)
                .stackSize(16)
                .build();
    }

    /** Rests, burrows by hardness, then fuse. */
    public static KineticPreset bomb(ResourceLocation id, double mass, double cdA, float blast, float burrow) {
        return KineticPreset.builder(id, MissileModels.rl("micro"), warhead(ThreatKind.HE, blast))
                .item("bomb_" + id.getPath())
                .threat(ThreatKind.HE)
                .blast(blast, true)
                .speed(0.0)
                .drag(0.0)
                .quadraticDrag(cdA / mass)
                .gravity(G)
                .life(1200)
                .mass(mass)
                .caliber(125)
                .fuseDelay(60)
                .burrow(burrow)
                .noEntityContact()
                .stackSize(4)
                .build();
    }

    /** Glides under gravity to the lock or aim point. */
    public static MissilePreset guidedBomb(ResourceLocation id, double mass, double cdA, float blast) {
        double k = cdA / mass;
        double ref = 2.0;
        return MissilePreset.builder(id, MissileModels.rl("guided_rocket"), warhead(ThreatKind.HE, blast))
                .item("bomb_" + id.getPath())
                .cruiseSpeed(ref)
                .accel(0.0, k * ref * ref)
                .fuel(MissileEntity.FuelType.SOLID, 1)
                .drag(k, true)
                .gLimit(2.0 * G, ref)
                .threat(ThreatKind.HE, 125, 0)
                .blast(blast, true)
                .ascentStage(AIR_LAUNCH)
                .cruiseStage(DIRECT)
                .attackStage(DIRECT)
                .terrainFollow(0.0)
                .build();
    }

    public static MissilePreset missile(ResourceLocation id, ResourceLocation model, ThreatKind threat, float calibre,
                                        float damage, float blast, float proximity, double mass, double thrust,
                                        int burnTicks, double cdA, double maxG, double refSpeed, SeekerMode seeker,
                                        float fov) {
        return base(id, model, threat, calibre, damage, blast, proximity, mass, thrust,
                burnTicks, cdA, maxG, refSpeed, seeker != SeekerMode.BEAM_RIDING)
                .cruiseStage(DIRECT)
                .attackStage(DIRECT)
                .terrainFollow(0.0)
                .seekerMode(seeker, fov, seeker == SeekerMode.BEAM_RIDING ? 0.0 : ACTIVE_RADAR_RANGE)
                .build();
    }

    /** {@link #missile} with a LOFT terminal: climbs over, dives onto the target at {@code diveDeg}. */
    public static MissilePreset topAttack(ResourceLocation id, ResourceLocation model, ThreatKind threat,
                                          float calibre, float damage, float blast, double mass, double thrust,
                                          int burnTicks, double cdA, double maxG, double refSpeed, SeekerMode seeker,
                                          float fov, double diveDeg) {
        return base(id, model, threat, calibre, damage, blast, 0, mass, thrust, burnTicks, cdA, maxG, refSpeed, true)
                .cruiseStage(DIRECT)
                .attackStage(ATTACK)
                .attackAngle(diveDeg)
                .attackProfile(AttackProfile.LOFT)
                .terrainFollow(0.0)
                .seekerMode(seeker, fov, ACTIVE_RADAR_RANGE)
                .build();
    }

    public static MissilePreset.Builder base(ResourceLocation id, ResourceLocation model, ThreatKind threat,
                                             float calibre, float damage, float blast, float proximity, double mass,
                                             double thrust, int burnTicks, double cdA, double maxG, double refSpeed,
                                             boolean glides) {
        double accel = thrust / mass / 400.0;
        double k = cdA / mass;
        double cruise = Math.sqrt(accel / k);
        return MissilePreset.builder(id, model, warhead(threat, blast))
                .cruiseSpeed(cruise)
                .accel(accel, Math.max(accel, k * cruise * cruise))
                .fuel(MissileEntity.FuelType.SOLID, burnTicks)
                .drag(k, glides)
                .gLimit(maxG * G, Math.max(0.05, refSpeed))
                .impactDamage(damage)
                .threat(threat, calibre, 0)
                .blast(blast, true)
                .proximity(proximity)
                .ascentStage(AIR_LAUNCH);
    }
}
