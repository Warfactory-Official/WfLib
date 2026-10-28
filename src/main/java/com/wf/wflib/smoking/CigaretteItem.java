package com.wf.wflib.smoking;

import net.minecraft.client.model.HumanoidModel;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.UseAnim;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.extensions.common.IClientItemExtensions;
import net.neoforged.neoforge.common.NeoForge;

import java.util.function.Consumer;

/** Held to smoke ({@link SmokingTimeline#TICKS}); {@link CigaretteSmokedEvent} on the last tick, one consumed. */
public final class CigaretteItem extends Item {

    public CigaretteItem(Properties properties) {
        super(properties);
    }

    @Override
    public int getUseDuration(ItemStack stack, LivingEntity entity) {
        return SmokingTimeline.TICKS;
    }

    @Override
    public UseAnim getUseAnimation(ItemStack stack) {
        return UseAnim.NONE;
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        player.startUsingItem(hand);
        return InteractionResultHolder.consume(player.getItemInHand(hand));
    }

    @Override
    public void onUseTick(Level level, LivingEntity entity, ItemStack stack, int remaining) {
        int tick = SmokingTimeline.TICKS - remaining;
        if (level instanceof ServerLevel server && tick % 3 == 0 && SmokingTimeline.exhaling(tick)) {
            Vec3 look = entity.getLookAngle();
            Vec3 mouth = entity.getEyePosition().add(look.scale(0.3)).add(0.0, -0.1, 0.0);
            server.sendParticles(ParticleTypes.SMOKE, mouth.x, mouth.y, mouth.z, 3, 0.03, 0.02, 0.03, 0.01);
        }
    }

    @Override
    public void initializeClient(Consumer<IClientItemExtensions> consumer) {
        consumer.accept(new IClientItemExtensions() {
            @Override
            public HumanoidModel.ArmPose getArmPose(LivingEntity entity, InteractionHand hand, ItemStack stack) {
                return entity.isUsingItem() && entity.getUsedItemHand() == hand ? HumanoidModel.ArmPose.SPYGLASS : null;
            }
        });
    }

    @Override
    public ItemStack finishUsingItem(ItemStack stack, Level level, LivingEntity entity) {
        if (entity instanceof ServerPlayer player) {
            NeoForge.EVENT_BUS.post(new CigaretteSmokedEvent(player, stack.copy()));
            if (!player.hasInfiniteMaterials()) {
                stack.shrink(1);
            }
        }
        return stack;
    }
}
