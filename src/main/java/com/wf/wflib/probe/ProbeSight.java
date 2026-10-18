package com.wf.wflib.probe;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/** Block line of sight, both sides: client node filter, server action gate. */
public final class ProbeSight {

    /** A block hit this close short of the point does not block (surface points, thin edges). */
    public static final double SLACK = 0.25;
    /** Box sample points pulled this share in from the corners. */
    private static final double INSET = 0.1;

    private ProbeSight() {
    }

    /** {@code eye -> at} crosses a block other than {@code target}. */
    public static boolean blocked(Level level, Entity viewer, Vec3 eye, Vec3 at, @Nullable BlockPos target) {
        BlockHitResult hit = level.clip(new ClipContext(eye, at, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE,
                viewer));
        return hit.getType() == HitResult.Type.BLOCK && !hit.getBlockPos().equals(target)
                && hit.getLocation().distanceTo(eye) < eye.distanceTo(at) - SLACK;
    }

    /** Centre or an inset corner of {@code entity}'s box unblocked from {@code viewer}'s eye. */
    public static boolean sees(Entity viewer, Entity entity) {
        AABB box = entity.getBoundingBox();
        return anyClear(viewer, box.deflate(box.getXsize() * INSET, box.getYsize() * INSET, box.getZsize() * INSET), null);
    }

    /** Centre or an inset corner of block {@code pos} unblocked by any other block. */
    public static boolean sees(Entity viewer, BlockPos pos) {
        return anyClear(viewer, new AABB(pos).deflate(INSET), pos);
    }

    private static boolean anyClear(Entity viewer, AABB box, @Nullable BlockPos target) {
        Vec3 eye = viewer.getEyePosition();
        Level level = viewer.level();
        if (!blocked(level, viewer, eye, box.getCenter(), target)) {
            return true;
        }
        for (int i = 0; i < 8; i++) {
            Vec3 corner = new Vec3((i & 1) == 0 ? box.minX : box.maxX, (i & 2) == 0 ? box.minY : box.maxY,
                    (i & 4) == 0 ? box.minZ : box.maxZ);
            if (!blocked(level, viewer, eye, corner, target)) {
                return true;
            }
        }
        return false;
    }
}
