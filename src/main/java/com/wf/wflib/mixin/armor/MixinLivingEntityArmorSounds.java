package com.wf.wflib.mixin.armor;

import com.wf.wflib.armor.ArmorSystem;
import com.wf.wflib.armor.api.ArmorSounds;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** {@link ArmorSounds.Cue#JUMP} / {@link ArmorSounds.Cue#LAND}. */
@Mixin(LivingEntity.class)
public abstract class MixinLivingEntityArmorSounds {

    @Inject(method = "jumpFromGround", at = @At("HEAD"))
    private void wflib$armorJump(CallbackInfo ci) {
        if (ArmorSystem.active()) {
            ArmorSounds.playMovement((LivingEntity) (Object) this, ArmorSounds.Cue.JUMP);
        }
    }

    @Inject(method = "causeFallDamage", at = @At("HEAD"))
    private void wflib$armorLand(float fallDistance, float multiplier, DamageSource source,
                                 CallbackInfoReturnable<Boolean> cir) {
        if (ArmorSystem.active() && fallDistance > ArmorSounds.MIN_LAND_FALL) {
            ArmorSounds.playMovement((LivingEntity) (Object) this, ArmorSounds.Cue.LAND);
        }
    }
}
