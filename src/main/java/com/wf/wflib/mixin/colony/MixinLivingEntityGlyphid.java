package com.wf.wflib.mixin.colony;

import com.wf.wflib.colony.ColonySystem;
import com.wf.wflib.colony.api.EntityGlyphid;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Skips vanilla's per-tick equipment diff for bare glyphids. */
@Mixin(LivingEntity.class)
public abstract class MixinLivingEntityGlyphid {

    @Inject(method = "detectEquipmentUpdates", at = @At("HEAD"), cancellable = true)
    private void wflib$skipBareEquipmentScan(CallbackInfo ci) {
        if (ColonySystem.active() && (Object) this instanceof EntityGlyphid glyphid && glyphid.equipmentEmpty()) {
            ci.cancel();
        }
    }
}
