package com.wf.wflib.mixin.radiation;

import com.mojang.datafixers.util.Either;
import com.wf.wflib.radiation.RadiationCarrier;
import com.wf.wflib.radiation.RadiationSystem;
import com.wf.wflib.radiation.RadiationSystemNT;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.storage.IOWorker;
import net.minecraft.world.level.chunk.storage.RegionStorageInfo;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Optional;

/** Radiation sidecar read on the IO thread, riding the chunk tag. */
@Mixin(IOWorker.class)
public abstract class MixinIOWorker {

    @Shadow
    public abstract RegionStorageInfo storageInfo();

    @Inject(method = "lambda$loadAsync$4", at = @At("RETURN"))
    private void wflib$readRadiationSidecar(ChunkPos pos,
                                             CallbackInfoReturnable<Either<Optional<CompoundTag>, Exception>> cir) {
        if (!RadiationSystem.active()) return;
        RegionStorageInfo info = this.storageInfo();
        if (!"chunk".equals(info.type())) return;
        Optional<CompoundTag> result = cir.getReturnValue().left().orElse(Optional.empty());
        if (result.isEmpty()) return;
        byte[] rad = RadiationSystemNT.readSidecar(info.dimension(), pos);
        if (rad != null) result.get().putByteArray(RadiationCarrier.NBT_KEY, rad);
    }
}
