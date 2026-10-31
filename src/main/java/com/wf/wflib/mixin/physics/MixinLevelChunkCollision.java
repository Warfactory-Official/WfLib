package com.wf.wflib.mixin.physics;

import com.wf.wflib.physics.PhysicsSystem;
import com.wf.wflib.physics.api.ChunkCollisionCache;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
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
        if (PhysicsSystem.active()) {
            ChunkCollisionCache.invalidate(this.level, pos);
        }
    }
}
