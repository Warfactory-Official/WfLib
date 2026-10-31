package com.wf.wflib.hazard.type;

import com.wf.wflib.hazard.HazardConfig;
import com.wf.wflib.radiation.api.RadiationConfig;
import com.wf.wflib.hazard.HazardProtection;
import com.wf.wflib.hazard.api.IHazardModifier;
import com.wf.wflib.hazard.api.ContaminationUtil;
import com.wf.wflib.hazard.api.ContaminationUtil.ContaminationType;
import com.wf.wflib.hazard.api.ContaminationUtil.HazardType;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import com.wf.wflib.hazard.api.IHazardType;

public class HazardTypeRadiation implements IHazardType {

    // 1.12.2: rad/s raw below 1M, then M/G suffix.
    public static double getNewValue(double radiation) {
        if (radiation < 1_000_000D) return radiation;
        if (radiation < 1_000_000_000D) return radiation * 0.000001D;
        return radiation * 0.000000001D;
    }

    private static String getSuffixKey(double radiation) {
        if (radiation < 1_000_000D) return null;
        if (radiation < 1_000_000_000D) return "desc.mil";
        return "desc.bil";
    }

    public static void appendRadiationLines(List<Component> list, double perItem, int count) {
        list.add(Component.literal("[").append(Component.translatable("trait.radioactive")).append("]")
                          .withStyle(ChatFormatting.GREEN));
        list.add(radLine(perItem, null));
        if (count > 1) list.add(radLine(perItem * count, "desc.stack"));
    }

    private static Component radLine(double rads, @Nullable String prefixKey) {
        String suffixKey = getSuffixKey(rads);
        String value = " " + (float) (Math.round(getNewValue(rads) * 1000D) / 1000D);
        MutableComponent line = prefixKey == null
                ? Component.literal(value)
                : Component.empty().append(" ").append(Component.translatable(prefixKey)).append(value);
        if (suffixKey != null) line.append(Component.translatable(suffixKey));
        return line.append(" ").append(Component.translatable("desc.rads")).withStyle(ChatFormatting.YELLOW);
    }

    @Override
    public void onUpdate(LivingEntity target, double level, ItemStack stack) {
        boolean reacher = HazardProtection.isHoldingReacher(target);
        level *= stack.getCount();
        if (level <= 0) return;

        double rad = level / 20D;
        if (HazardConfig.get().enable528() && reacher) {
            rad = rad / 49D;
        } else if (reacher) {
            rad = Math.sqrt(rad);
        }

        ContaminationUtil.contaminate(target, HazardType.RADIATION, ContaminationType.CREATIVE, rad * HazardConfig.get().hazardRate());
    }

    @Override
    public void updateEntity(ItemEntity item, double level) {
    }

    @Override
    public void addHazardInformation(Player player, List<Component> list, double level, ItemStack stack,
                                     List<IHazardModifier> modifiers) {
        level = IHazardModifier.evalAllModifiers(stack, player, level, modifiers);
        if (level < 1e-5D) return;
        appendRadiationLines(list, level, stack.getCount());
    }
}
