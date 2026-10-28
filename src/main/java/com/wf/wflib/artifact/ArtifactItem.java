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

    /** The lowest detector rank that finds it (1 Echo, 2 Bear, 3 Veles). */
    public int detectRank() {
        return artifact.detectRank();
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> lines, TooltipFlag flag) {
        for (Artifact.Hit hit : Artifact.Hit.values()) {
            line(lines, hit.name().toLowerCase(java.util.Locale.ROOT), artifact.immunity(hit));
        }
        if (artifact.health() != 0.0F) {
            lines.add(Component.translatable("item.wflib.artifact.health",
                            String.format(java.util.Locale.ROOT, "%+.1f", artifact.healthPerSecond(20.0F) * 60.0F))
                    .withStyle(artifact.health() > 0.0F ? ChatFormatting.GREEN : ChatFormatting.RED));
        }
        if (artifact.power() != 0.0F && net.neoforged.fml.ModList.get().isLoaded("wfweight")) {
            lines.add(Component.translatable("item.wflib.artifact.stamina",
                            String.format(java.util.Locale.ROOT, "%+.1f%%", artifact.staminaPerTick() * 2000.0D))
                    .withStyle(artifact.power() > 0.0F ? ChatFormatting.GREEN : ChatFormatting.RED));
        }
        if (artifact.bleeding() > 0.0F && net.neoforged.fml.ModList.get().isLoaded("wfmedical")) {
            lines.add(Component.translatable("item.wflib.artifact.bleeding").withStyle(ChatFormatting.GREEN));
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
