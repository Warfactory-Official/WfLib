package com.wf.wflib.hazard.type;

import com.wf.wflib.hazard.HazardConfig;
import com.wf.wflib.radiation.api.RadiationConfig;
import com.wf.wflib.hazard.api.IHazardModifier;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

import java.util.List;
import com.wf.wflib.hazard.api.IHazardType;

public class HazardTypeHydroactive implements IHazardType {

    @Override
    public void onUpdate(LivingEntity target, double level, ItemStack stack) {
        if (HazardConfig.get().disableHydro()) return;
        Level world = target.level();
        BlockPos pos = target.blockPosition();
        boolean wet = target.isInWaterOrRain()
                || (world.getBiome(pos).value().hasPrecipitation()
                && world.isRaining()
                && world.canSeeSky(pos));
        if (wet && stack.getCount() > 0) {
            stack.setCount(0);
            world.explode(null, target.getX(), target.getEyeY(), target.getZ(),
                    (float) level, false, Level.ExplosionInteraction.TNT);
        }
    }

    @Override
    public void updateEntity(ItemEntity item, double level) {
        if (HazardConfig.get().disableHydro()) return;
        if (item.isInWaterOrRain()) {
            item.discard();
            item.level().explode(null, item.getX(), item.getY() + item.getBbHeight() * 0.5,
                    item.getZ(), (float) level, false, Level.ExplosionInteraction.TNT);
        }
    }

    @Override
    public void addHazardInformation(Player player, List<Component> list, double level, ItemStack stack,
                                     List<IHazardModifier> modifiers) {
        list.add(Component.literal("[").append(Component.translatable("trait.hydro")).append("]")
                          .withStyle(ChatFormatting.RED));
    }
}
