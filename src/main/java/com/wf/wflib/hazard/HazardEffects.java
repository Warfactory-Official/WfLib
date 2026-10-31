package com.wf.wflib.hazard;

import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.entity.LivingEntity;
import com.wf.wflib.hazard.api.ContaminationUtil;
import com.wf.wflib.hazard.api.LivingHazardProps;

/** NTM HbmPotion radiation subset: radiation, radaway, rad-x, stability. */
final class HazardEffects {

    private HazardEffects() {
    }

    static final class Plain extends MobEffect {
        Plain(MobEffectCategory category, int color) {
            super(category, color);
        }
    }

    static final class Radiation extends MobEffect {
        Radiation() {
            super(MobEffectCategory.HARMFUL, 0x84c128);
        }

        @Override
        public boolean applyEffectTick(LivingEntity entity, int amplifier) {
            ContaminationUtil.contaminate(entity, ContaminationUtil.HazardType.RADIATION,
                    ContaminationUtil.ContaminationType.CREATIVE, (amplifier + 1) * 0.05D);
            return true;
        }

        @Override
        public boolean shouldApplyEffectTickThisTick(int tickCount, int amplifier) {
            return true;
        }
    }

    static final class Radaway extends MobEffect {
        Radaway() {
            super(MobEffectCategory.BENEFICIAL, 0xbb4b00);
        }

        @Override
        public boolean applyEffectTick(LivingEntity entity, int amplifier) {
            if (!entity.level().isClientSide()) LivingHazardProps.incrementRadiation(entity, -(amplifier + 1));
            return true;
        }

        @Override
        public boolean shouldApplyEffectTickThisTick(int tickCount, int amplifier) {
            return true;
        }
    }
}
