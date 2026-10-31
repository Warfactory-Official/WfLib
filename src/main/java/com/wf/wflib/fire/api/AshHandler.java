package com.wf.wflib.fire.api;

import com.wf.wflib.core.api.WFNetwork;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import com.wf.wflib.fire.FireSystem;

public final class AshHandler {

    private AshHandler() {
    }

    public static void decideGore(LivingEntity entity, DamageSource source) {
        if (source.is(DamageTypeTags.IS_FIRE) || entity.isOnFire()) {
            cremate(entity);
        }
    }


    static void cremate(LivingEntity entity) {
        Level level = entity.level();
        if (level.isClientSide) return;

        float width = entity.getBbWidth();
        float height = entity.getBbHeight();
        int count = Mth.clamp((int) (width * width * height * 25), 5, 50);

        CompoundTag data = new CompoundTag();
        data.putInt("count", count);
        data.putFloat("scale", 0.125F);

        WFNetwork.sendToTracking(level, entity.getX(), entity.getZ(),
                com.wf.wflib.explosion.api.AuxParticles.packet("ashes", entity.getX(), entity.getY() + height * 0.5, entity.getZ(), data));

        // Humanoid-ish things also collapse into a Flywheel-instanced skeleton (see SkeletonBoneEffect).
        if (height >= 0.9F && height <= 3.0F && width <= 1.2F) {
            CompoundTag bones = new CompoundTag();
            bones.putFloat("bodyYaw", entity.yBodyRot);
            bones.putFloat("headYaw", entity.getYHeadRot());
            bones.putFloat("height", height);
            bones.putFloat("brightness", 0.85F);
            WFNetwork.sendToTracking(level, entity.getX(), entity.getZ(),
                    com.wf.wflib.explosion.api.AuxParticles.packet("skeleton", entity.getX(), entity.getY(), entity.getZ(), bones));
        }

        level.playSound(null, entity.getX(), entity.getY(), entity.getZ(),
                FireSystem.FIRE_DISINTEGRATION.get(), SoundSource.PLAYERS,
                2.0F, 0.9F + level.random.nextFloat() * 0.2F);
    }
}
