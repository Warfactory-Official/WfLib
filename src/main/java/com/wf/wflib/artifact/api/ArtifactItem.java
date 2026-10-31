package com.wf.wflib.artifact.api;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;


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

    /** Dropped or thrown out: the artefact itself, rolling ({@link ZoneArtifact}), not an item entity. */
    @Override
    public boolean hasCustomEntity(ItemStack stack) {
        return true;
    }

    @Override
    public Entity createEntity(Level level, Entity location, ItemStack stack) {
        return ZoneArtifact.dropped(level, location, stack);
    }

    /** The lowest detector rank that finds it (1 Echo, 2 Bear, 3 Veles). */
    int detectRank() {
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
        if (artifact.bleeding() != 0.0F && Artifacts.CLOTTER.owner() != null) {
            lines.add(artifact.bleeding() > 0.0F
                    ? Component.translatable("item.wflib.artifact.bleeding").withStyle(ChatFormatting.GREEN)
                    : Component.translatable("item.wflib.artifact.thins").withStyle(ChatFormatting.RED));
        }
        if (artifact.radShield() != 0.0F) {
            lines.add(Component.translatable("item.wflib.artifact.radiation",
                            String.format(java.util.Locale.ROOT, "%+.1f", artifact.radShieldPerSecond()))
                    .withStyle(artifact.radShield() > 0.0F ? ChatFormatting.GREEN : ChatFormatting.RED));
        }
        if (artifact.radiation() != 0.0F) {
            lines.add(Component.translatable("item.wflib.artifact.irradiates",
                            String.format(java.util.Locale.ROOT, "%+.1f", artifact.radiationPerSecond()))
                    .withStyle(artifact.radiation() > 0.0F ? ChatFormatting.RED : ChatFormatting.GREEN));
        }
        if (artifact.carry() != 0.0F && net.neoforged.fml.ModList.get().isLoaded("wfweight")) {
            lines.add(Component.translatable("item.wflib.artifact.carry",
                            String.format(java.util.Locale.ROOT, "%+.0f", artifact.carry()))
                    .withStyle(artifact.carry() > 0.0F ? ChatFormatting.GREEN : ChatFormatting.RED));
        }
        if (artifact.satiety() < 0.0F) {
            lines.add(Component.translatable("item.wflib.artifact.starves").withStyle(ChatFormatting.RED));
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
