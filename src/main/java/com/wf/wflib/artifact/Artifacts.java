package com.wf.wflib.artifact;

import com.wf.wflib.damage.WFDamageTypes;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;

import java.util.List;

/**
 * Artefacts at work: each worn in a Curios {@code artifact} slot scales the hits of its kinds by
 * {@code 1 - immunity} (GAMMA stacks them the same way, one after another). Without Curios nothing is worn.
 */
public final class Artifacts {
    private Artifacts() {
    }

    public static void register() {
        NeoForge.EVENT_BUS.addListener(Artifacts::onIncomingDamage);
    }

    private static void onIncomingDamage(LivingIncomingDamageEvent e) {
        if (!(e.getEntity() instanceof Player player) || player.level().isClientSide) {
            return;
        }
        float scale = 1.0F;
        for (ItemStack stack : worn(player)) {
            if (stack.getItem() instanceof ArtifactItem item) {
                scale *= 1.0F - immunity(item.artifact(), e.getSource());
            }
        }
        if (scale != 1.0F) {
            e.setAmount(e.getAmount() * Math.max(0.0F, scale));
        }
    }

    /** The share of {@code source} this artefact takes away (negative: adds). */
    static float immunity(Artifact artifact, DamageSource source) {
        if (source.is(WFDamageTypes.ELECTRIC) || source.is(DamageTypes.LIGHTNING_BOLT)) {
            return artifact.shock();
        }
        if (source.is(DamageTypeTags.IS_FIRE)) {
            return artifact.burn();
        }
        if (source.is(WFDamageTypes.GRAVITY_ANOMALY) || source.is(DamageTypes.MOB_ATTACK)
                || source.is(DamageTypes.MOB_ATTACK_NO_AGGRO) || source.is(DamageTypes.PLAYER_ATTACK)) {
            return artifact.strike();
        }
        return 0.0F;
    }

    /** Artefacts in {@code player}'s Curios {@code artifact} slots; none without Curios. */
    public static List<ItemStack> worn(Player player) {
        return ModList.get().isLoaded("curios") ? Loaded.worn(player) : List.of();
    }

    /** Optional linkage boundary: only this class references Curios. */
    private static final class Loaded {
        static List<ItemStack> worn(Player player) {
            return top.theillusivec4.curios.api.CuriosApi.getCuriosInventory(player)
                    .map(inventory -> inventory.findCurios("artifact").stream()
                            .map(top.theillusivec4.curios.api.SlotResult::stack).toList())
                    .orElse(List.of());
        }
    }
}
