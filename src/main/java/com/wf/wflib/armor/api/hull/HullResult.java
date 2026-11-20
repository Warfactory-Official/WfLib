package com.wf.wflib.armor.api.hull;

import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/**
 * @param nominalThickness   mm, entry plate
 * @param effectiveThickness mm, entry penetration that just defeats the entry plate ({@link WalkResult#interiorNeeded})
 * @param penetration        mm, at entry
 * @param normal             struck surface, outward, world frame
 * @param faceNormal         armour face by shot aspect, outward, world frame ({@link HullResolver.Plate})
 * @param plateId            entry plate, null = unbound face (hull / part / zone value)
 * @param part               owning part of the entry plate, null = hull
 * @param incidence          deg between the shot and the entry plate's normal (declared, mesh when curved)
 * @param crossingPlates     plate id (null = unbound) of each walked crossing, aligned with {@link WalkResult#crossingAt}
 * @param hullShare          entry plate's {@link PlateSpec#hull}: scales the hull pool only, not {@code hullDamage} as a ramp
 * @param walk               every layer the shot line met, in order; {@link WalkResult#NONE} for a ricochet
 */
public record HullResult(HullOutcome outcome, float hullDamage, float partDamage, float nominalThickness,
                         float effectiveThickness, float penetration, Vec3 normal, Vec3 faceNormal,
                         WalkResult walk, float hullShare,
                         @Nullable String plateId, @Nullable String part, float incidence,
                         String[] crossingPlates) {

    public float ratio() {
        return effectiveThickness <= 0 ? Float.POSITIVE_INFINITY : penetration / effectiveThickness;
    }

    /** mm RHA left after the entry plate: crew and modules behind it are reachable only when {@code > 0}. */
    public float interiorPen() {
        return walk.interiorPen();
    }

    /** The entry plate is defeated: {@link #interiorPen} {@code > 0}, {@link HullOutcome#isThrough}. */
    public boolean through() {
        return walk.interiorPen() > 0.0f;
    }

    /** mm RHA left after every plate on the line: what the round leaves the hull with. */
    public float residualPen() {
        return walk.residualPen();
    }

    /** Left the hull with penetration left. */
    public boolean exits() {
        return walk.through();
    }

    /**
     * Speed on leaving: {@code speed * (residual / penetration)^(1 / penExponent)}, the inverse of
     * {@code pen * (v / v0)^penExponent}. 0 when stopped.
     */
    public float exitSpeed(float speed, float penExponent) {
        float residual = walk.residualPen();
        if (!(residual > 0) || !(penetration > 0)) {
            return 0.0f;
        }
        float fraction = Math.min(residual / penetration, 1.0f);
        return speed * (penExponent == 1.0f ? fraction : (float) Math.pow(fraction, 1.0 / penExponent));
    }
}
