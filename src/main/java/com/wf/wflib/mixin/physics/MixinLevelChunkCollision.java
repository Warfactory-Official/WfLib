package com.wf.wflib.mixin.physics;

import com.wf.wflib.physics.PhysicsSystem;
import com.wf.wflib.physics.api.ChunkCollisionCache;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.EmptyBlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Hooked here, not NeoForge block events: those miss piston/explosion/fluid/worldgen writes. */
@Mixin(LevelChunk.class)
public abstract class MixinLevelChunkCollision {

    @Shadow
    @Final
    Level level;

    @Inject(method = "setBlockState", at = @At("HEAD"))
    private void wflib$invalidateCollisionSnapshot(BlockPos pos, BlockState state, boolean isMoving,
                                                   CallbackInfoReturnable<BlockState> cir) {
        if (PhysicsSystem.active() && collisionMayDiffer(((LevelChunk) (Object) this).getBlockState(pos), state)) {
            ChunkCollisionCache.invalidate(this.level, pos);
        }
    }

    /** Unknown => true: a missed invalidation leaves vehicles on stale geometry. */
    @Unique
    private static boolean collisionMayDiffer(BlockState old, BlockState state) {
        if (old == state) {
            return false;
        }
        if (old.hasBlockEntity() || state.hasBlockEntity()
                || old.hasOffsetFunction() || state.hasOffsetFunction()
                || old.getBlock().hasDynamicShape() || state.getBlock().hasDynamicShape()) {
            return true;
        }
        return old.getCollisionShape(EmptyBlockGetter.INSTANCE, BlockPos.ZERO)
                != state.getCollisionShape(EmptyBlockGetter.INSTANCE, BlockPos.ZERO);
    }
}
