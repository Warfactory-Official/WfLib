package com.wf.wflib.mixin;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import com.wf.wflib.aef.nuke.util.GenerationSection;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunkSection;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Always counts; NTM-next swaps the increment in only while a blast holds tracking. */
@Mixin(LevelChunkSection.class)
public abstract class MixinLevelChunkSection implements GenerationSection {
    @Unique
    private long wflib$generation;

    @Override
    public long wflib$generation() {
        return wflib$generation;
    }

    @ModifyReturnValue(method = "setBlockState(IIILnet/minecraft/world/level/block/state/BlockState;Z)Lnet/minecraft/world/level/block/state/BlockState;",
            at = @At("RETURN"))
    private BlockState wflib$blockChanged(BlockState previous, int x, int y, int z, BlockState state, boolean useLocks) {
        if (previous != state) wflib$generation++;
        return previous;
    }

    @Inject(method = {"read", "readBiomes", "fillBiomesFromNoise"}, at = @At("RETURN"))
    private void wflib$paletteChanged(CallbackInfo ci) {
        wflib$generation++;
    }
}
