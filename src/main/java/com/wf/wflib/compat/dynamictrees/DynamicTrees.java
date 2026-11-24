package com.wf.wflib.compat.dynamictrees;

import com.dtteam.dynamictrees.block.branch.BranchBlock;
import com.dtteam.dynamictrees.entity.FallingTreeEntity;
import com.wf.wflib.explosion.api.ExplosionAEF;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.fml.ModList;

/**
 * Soft Dynamic Trees bridge. Branch in a blast: Dynamic Trees' own explosion path ({@code EXPLODE} falling tree) instead of the plain removal
 * ({@code VOID}: tips over, no physics), launched away from the centre by blast size.
 */
public final class DynamicTrees {

    private static final boolean LOADED = ModList.get() != null && ModList.get().isLoaded("dynamictrees");
    /** Launch speed per unit of blast size, blocks/tick. */
    private static final double SPEED_PER_SIZE = 0.06;
    private static final double MAX_SPEED = 1.5;
    private static final double LIFT = 0.15;
    private static final double SEARCH = 12.0;

    private DynamicTrees() {
    }

    public static boolean loaded() {
        return LOADED;
    }

    public static boolean isBranch(BlockState state) {
        return LOADED && Impl.isBranch(state);
    }

    /** Trees felled this tick near {@code around} get a shove along {@code dir} (unit). */
    public static void shoveFresh(Level level, AABB around, Vec3 dir) {
        if (LOADED) {
            Impl.shoveFresh(level, around, dir);
        }
    }

    /** @return handled: the tree is gone, skip the plain removal */
    public static boolean hit(BlockState state, Level level, BlockPos pos, ExplosionAEF explosion) {
        return LOADED && Impl.hit(state, level, pos, explosion);
    }

    private static final class Impl {
        static boolean isBranch(BlockState state) {
            return state.getBlock() instanceof BranchBlock;
        }

        static void shoveFresh(Level level, AABB around, Vec3 dir) {
            for (FallingTreeEntity tree : level.getEntitiesOfClass(FallingTreeEntity.class, around, e -> e.tickCount == 0)) {
                tree.push(dir.x, dir.y, dir.z);
            }
        }

        static boolean hit(BlockState state, Level level, BlockPos pos, ExplosionAEF explosion) {
            if (!(state.getBlock() instanceof BranchBlock)) {
                return false;
            }
            state.onExplosionHit(level, pos, explosion.compat, (stack, at) -> {
            });
            Vec3 centre = explosion.compat.center();
            for (FallingTreeEntity tree : level.getEntitiesOfClass(FallingTreeEntity.class,
                    new AABB(pos).inflate(SEARCH), e -> e.tickCount == 0)) {
                Vec3 away = tree.position().subtract(centre);
                double d = away.length();
                double reach = Math.max(explosion.size, 1.0f) * 2.0;
                if (d > reach) {
                    continue;
                }
                Vec3 dir = d < 1.0e-3 ? new Vec3(0, 1, 0) : away.scale(1.0 / d);
                double speed = Mth.clamp(explosion.size * SPEED_PER_SIZE * (1.0 - 0.5 * d / reach), 0.2, MAX_SPEED);
                tree.setDeltaMovement(dir.x * speed, dir.y * speed + LIFT, dir.z * speed);
            }
            return !(level.getBlockState(pos).getBlock() instanceof BranchBlock);
        }
    }
}
