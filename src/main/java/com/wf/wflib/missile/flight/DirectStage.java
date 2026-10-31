package com.wf.wflib.missile.flight;

import com.wf.wflib.missile.api.MissileEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;
import com.wf.wflib.missile.api.FlightContext;
import com.wf.wflib.missile.api.FlightStage;

/**
 * Pure pursuit on the aim point, no terrain profile: a line-of-sight round (TV, ATGM). No seeker track and aim point
 * passed => straight on (sky aim point = sight line end: pursuit looped around it). Aim point in a collidable block
 * never latches: straight on can parallel the face forever (45 degree slope, leaf spawned beside its aim point).
 */
public final class DirectStage implements FlightStage {

    public static final DirectStage INSTANCE = new DirectStage();

    private DirectStage() {
    }

    @Override
    public Vec3 guide(MissileEntity missile, FlightContext ctx) {
        Vec3 los = ctx.target().subtract(ctx.position());
        double length = los.length();
        Vec3 v = missile.getDeltaMovement();
        if (length < 1.0E-3) {
            return v;
        }
        if (missile.seeker().getDesignatedTargetId() == null && !aimInsideBlock(missile, ctx.target())
                && missile.flight().passedAimPoint(los, v)) {
            double speed = v.length();
            return speed < 1.0E-6 ? v : v.scale(missile.flight().getCruiseSpeed() / speed);
        }
        return los.scale(missile.flight().getCruiseSpeed() / length);
    }

    private static boolean aimInsideBlock(MissileEntity missile, Vec3 target) {
        BlockPos pos = BlockPos.containing(target);
        return missile.level().isLoaded(pos) && !missile.level().getBlockState(pos).getCollisionShape(missile.level(), pos).isEmpty();
    }

    @Override
    public String id() {
        return "direct";
    }
}
