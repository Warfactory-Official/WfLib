package com.wf.wflib.mixin.physics;

import com.wf.wflib.physics.step.TessellateBridge;
import net.minecraft.server.level.ServerLevel;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Region tick end on stock Tessellate: after the region's entity (+ block entity) phase, inside its region scope.
 * Gated on mod {@code tessellate}.
 */
@Mixin(targets = "org.texboobcat.tessellate.region.LevelRegionIndex", remap = false)
public abstract class MixinLevelRegionIndexTickEnd {

    @Shadow
    @Final
    private ServerLevel level;

    @Inject(method = "tickFullRegion", at = @At(value = "INVOKE", shift = At.Shift.AFTER, target =
            "Lorg/texboobcat/tessellate/region/LevelRegionIndex$RegionalLevelAccess;"
                    + "tessellate$tickRegionalBlockEntities(Lorg/texboobcat/tessellate/region/Region;Ljava/util/List;)V"))
    private void wflib$tickEndFull(CallbackInfo ci) {
        TessellateBridge.regionTickEnd(this.level);
    }

    /** Unbound thread (null region) => bridge sees region -1 and skips. */
    @Inject(method = "tickOneRegion", at = @At(value = "INVOKE", shift = At.Shift.AFTER, target =
            "Lorg/texboobcat/tessellate/region/PhaseStats;measure("
                    + "Lorg/texboobcat/tessellate/region/PhaseStats$Phase;Ljava/lang/Runnable;)V"))
    private void wflib$tickEndOne(CallbackInfo ci) {
        TessellateBridge.regionTickEnd(this.level);
    }
}
