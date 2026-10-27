package com.wf.wflib.hazard;

import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

/** NTM {@code ItemGeigerCounter}. HUD: {@code client.GeigerHud}. */
public class GeigerCounterItem extends Item {

    public GeigerCounterItem(Properties props) {
        super(props);
    }

    public static int pickGeiger(double x, RandomSource rand) {
        if (x <= 1e-5) return 0;
        int[] list = new int[3];
        int n = 0;
        if (x < 1) list[n++] = 0;
        if (x < 5) list[n++] = 0;
        if (x < 10) list[n++] = 1;
        if (x > 5 && x < 15) list[n++] = 2;
        if (x > 10 && x < 20) list[n++] = 3;
        if (x > 15 && x < 25) list[n++] = 4;
        if (x > 20 && x < 30) list[n++] = 5;
        if (x > 25) list[n++] = 6;
        return list[rand.nextInt(n)];
    }

    @Override
    public void inventoryTick(ItemStack stack, Level level, Entity owner, int slot, boolean selected) {
        if (level.isClientSide() || !(owner instanceof LivingEntity entity)) return;
        if (!TickPhase.every(entity, 5)) return;
        double x = LivingHazardProps.getRadBuf(entity);
        int idx = x > 1e-5 ? pickGeiger(x, level.getRandom()) : (level.getRandom().nextInt(50) == 0 ? 1 : 0);
        if (idx > 0) {
            level.playSound(null, entity.getX(), entity.getY(), entity.getZ(), HazardContent.GEIGER[idx - 1].get(),
                    SoundSource.PLAYERS, 1.0F, 1.0F);
        }
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        if (!level.isClientSide()) {
            level.playSound(null, player.getX(), player.getY(), player.getZ(), HazardContent.TECH_BOOP.get(),
                    SoundSource.PLAYERS, 1.0F, 1.0F);
            ContaminationUtil.printGeigerData(player);
        }
        return InteractionResultHolder.sidedSuccess(player.getItemInHand(hand), level.isClientSide());
    }
}
