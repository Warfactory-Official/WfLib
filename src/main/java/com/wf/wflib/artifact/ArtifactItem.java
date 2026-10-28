package com.wf.wflib.artifact;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;

import java.util.List;

/** An artefact: works worn in a Curios {@code artifact} slot (STALKER's belt); held, it floats over the palm. */
public class ArtifactItem extends Item {
    private final Artifact artifact;

    public ArtifactItem(Artifact artifact, Properties properties) {
        super(properties);
        this.artifact = artifact;
    }

    public Artifact artifact() {
        return artifact;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> lines, TooltipFlag flag) {
        line(lines, "shock", artifact.shock());
        line(lines, "burn", artifact.burn());
        line(lines, "strike", artifact.strike());
        if (artifact.healthRestore() > 0.0F) {
            lines.add(Component.translatable("item.wflib.artifact.health",
                            String.format(java.util.Locale.ROOT, "%.1f", artifact.healthPerSecond(20.0F) * 60.0F))
                    .withStyle(ChatFormatting.GREEN));
        }
        if (artifact.powerRestore() != 0.0F && net.neoforged.fml.ModList.get().isLoaded("wfweight")) {
            lines.add(Component.translatable("item.wflib.artifact.stamina",
                            String.format(java.util.Locale.ROOT, "%+.1f%%", artifact.staminaPerTick() * 2000.0D))
                    .withStyle(artifact.powerRestore() > 0.0F ? ChatFormatting.GREEN : ChatFormatting.RED));
        }
        lines.add(Component.translatable("item.wflib.artifact.worn").withStyle(ChatFormatting.DARK_GRAY));
    }

    private static void line(List<Component> lines, String key, float immunity) {
        if (immunity != 0.0F) {
            lines.add(Component.translatable("item.wflib.artifact." + key,
                            String.format(java.util.Locale.ROOT, "%+.0f%%", immunity * 100.0F))
                    .withStyle(immunity > 0.0F ? ChatFormatting.GREEN : ChatFormatting.RED));
        }
    }
}
