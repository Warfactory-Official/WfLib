package com.wf.wflib.hazard.type;

import com.wf.wflib.radiation.api.RadiationConfig;
import com.wf.wflib.radiation.api.RadiationField;
import com.wf.wflib.hazard.api.IHazardModifier;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

import java.util.List;
import com.wf.wflib.hazard.api.IHazardType;

// 1.12.2: CE-derived; no 1.7 type contaminates from a dropped item - HazardTypeRadiation.java:48's
// updateEntity is empty, so this is not a split of RADIATION. Zero rows is deliberate where CE declares them
// (CE HazardRegistry.java:219,221,223-226,479,481). CE's BlockClean tile wear and ExplosionNukeGeneric.waste
// transmute (CE :46-47,57) are left out; the saturation term is bespoke, CE :54 calling unsaturated incrementRad.
public class HazardTypeContaminating implements IHazardType {

    private static final int MAX_RADIUS = 500;

    private static int computeRadius(double level) {
        return (int) Math.min(Math.sqrt(level) + 0.5D, MAX_RADIUS);
    }

    @Override
    public void onUpdate(LivingEntity target, double level, ItemStack stack) {
    }

    @Override
    public void updateEntity(ItemEntity item, double level) {
        if (!RadiationConfig.get().enableContaminationOnGround()) return;
        Level world = item.level();
        if (!(world instanceof ServerLevel server)) return;
        if (!item.onGround()) return;

        int radius = computeRadius(level);
        if (radius > 1) {
            BlockPos pos = item.blockPosition();
            RadiationField.source(server, pos, level, level * 1024D);
        }
        item.discard();
    }

    @Override
    public void addHazardInformation(Player player, List<Component> list, double level, ItemStack stack,
                                     List<IHazardModifier> modifiers) {
        if (!RadiationConfig.get().enableContaminationOnGround()) return;
        int radius = computeRadius(level);
        if (radius > 1) {
            list.add(Component.literal("[").append(Component.translatable("trait.contaminating")).append("]")
                              .withStyle(ChatFormatting.DARK_GREEN));
            list.add(Component.literal(" ").append(Component.translatable("trait.contaminating.radius", radius))
                              .withStyle(ChatFormatting.GREEN));
        }
    }
}
