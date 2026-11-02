package com.wf.wflib.mixin.armor;

import com.wf.wflib.armor.ArmorSystem;
import com.wf.wflib.armor.api.ArmorSounds;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** {@link ArmorSounds.Cue#WALK}; hooked on {@code Entity}: {@code Player} overrides {@code playStepSound}. */
@Mixin(Entity.class)
public abstract class MixinEntityArmorStep {

    @Inject(method = "walkingStepSound", at = @At("HEAD"))
    private void wflib$armorStep(BlockPos pos, BlockState state, CallbackInfo ci) {
        if (ArmorSystem.active() && (Object) this instanceof LivingEntity self) {
            ArmorSounds.playMovement(self, ArmorSounds.Cue.WALK);
        }
    }
}
