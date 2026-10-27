package com.wf.wflib.mixin;

import com.wf.wflib.radiation.RadiationSystemNT;
import net.minecraft.server.MinecraftServer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Sidecar follows save-all / autosave. */
@Mixin(MinecraftServer.class)
public abstract class MixinMinecraftServerRadiation {

    @Inject(method = "saveEverything", at = @At("RETURN"))
    private void wflib$flushRadiationSidecar(boolean suppressLog, boolean flush, boolean forced,
                                             CallbackInfoReturnable<Boolean> cir) {
        if (cir.getReturnValue()) RadiationSystemNT.onServerSave((MinecraftServer) (Object) this, flush);
    }
}
