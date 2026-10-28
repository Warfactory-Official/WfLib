package com.wf.wflib.artifact.detect;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;

import java.util.List;
import java.util.Locale;

/** An artefact detector: held in either hand it listens ({@link Detection}); artefacts near enough show. */
public class DetectorItem extends Item {
    private final DetectorTier tier;

    public DetectorItem(DetectorTier tier, Properties properties) {
        super(properties);
        this.tier = tier;
    }

    public DetectorTier tier() {
        return tier;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> lines, TooltipFlag flag) {
        lines.add(Component.translatable("item.wflib.detector.range", (int) tier.radius,
                String.format(Locale.ROOT, "%.0f", tier.reveal)).withStyle(ChatFormatting.GRAY));
        lines.add(Component.translatable("item.wflib.detector.display." + tier.display.name().toLowerCase(Locale.ROOT))
                .withStyle(ChatFormatting.DARK_GRAY));
    }
}
