package com.wf.wflib.artifact;

import com.wf.wflib.damage.WFDamageTypes;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;

import java.util.List;
import java.util.function.BiConsumer;

/**
 * Artefacts at work: each worn in a Curios {@code artifact} slot scales the hits of its kinds by
 * {@code 1 - immunity} (GAMMA stacks them the same way, one after another), gives health (or drains it), stamina (with
 * WFWeight) and wound closing (with WFMedical) over time, and keeps psy off (WFMedical's stress). Without Curios
 * nothing is worn.
 */
public final class Artifacts {
    /**
     * How worn artefacts give health back (or, negative, drain it). A health mod that clamps vanilla healing
     * (WFMedical) replaces it with its own, as it claims armour ({@code ArmorSystem.claimPlayerResolution}).
     */
    public static BiConsumer<ServerPlayer, Float> healer = (player, hp) -> {
        if (hp > 0.0F) {
            player.heal(hp);
        } else if (hp < 0.0F) {
            player.hurt(player.damageSources().starve(), -hp);
        }
    };

    /**
     * How worn artefacts close wounds: called once a second with their summed bleeding_restore per real second. A
     * mod with wounds (WFMedical) claims it; without one there is nothing to close.
     */
    public static BiConsumer<ServerPlayer, Float> clotter = (player, rate) -> {
    };

    private Artifacts() {
    }

    public static void register() {
        // Before NORMAL: WFMedical turns the hit into wounds there, so the cut must already be in the amount.
        NeoForge.EVENT_BUS.addListener(EventPriority.HIGH, Artifacts::onIncomingDamage);
        NeoForge.EVENT_BUS.addListener(Artifacts::onPlayerTick);
        if (ModList.get().isLoaded("wfweight")) {
            com.wf.wflib.compat.wfweight.ArtifactStamina.register();
        }
    }

    /** Once a second: worn artefacts' health back or drained ({@code health_restore_speed}), wounds closed. */
    private static void onPlayerTick(net.neoforged.neoforge.event.tick.PlayerTickEvent.Post e) {
        Player player = e.getEntity();
        if (!(e.getEntity() instanceof ServerPlayer server) || player.tickCount % 20 != 0 || player.isDeadOrDying()) {
            return;
        }
        float heal = 0.0F;
        float clot = 0.0F;
        for (ItemStack stack : worn(player)) {
            if (stack.getItem() instanceof ArtifactItem item) {
                heal += item.artifact().healthPerSecond(player.getMaxHealth());
                clot += item.artifact().bleedingPerSecond();
            }
        }
        if (heal != 0.0F) {
            healer.accept(server, heal);
        }
        if (clot > 0.0F) {
            clotter.accept(server, clot);
        }
    }

    /**
     * The share of psy (here: stress) the worn artefacts keep off {@code player}, stacked as GAMMA stacks them;
     * negative makes it worse. WFMedical scales stress gain by {@code 1 - psyProtection}.
     */
    public static float psyProtection(Player player) {
        float pass = 1.0F;
        for (ItemStack stack : worn(player)) {
            if (stack.getItem() instanceof ArtifactItem item) {
                pass *= 1.0F - item.artifact().immunity(Artifact.Hit.PSY);
            }
        }
        return 1.0F - Math.max(0.0F, pass);
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
        Artifact.Hit hit = hit(source);
        return hit == null ? 0.0F : artifact.immunity(hit);
    }

    /** STALKER's hit kind for a damage: what an artefact's {@code *_immunity} answers to. */
    @org.jetbrains.annotations.Nullable
    static Artifact.Hit hit(DamageSource source) {
        if (source.is(WFDamageTypes.ELECTRIC) || source.is(DamageTypes.LIGHTNING_BOLT)) {
            return Artifact.Hit.SHOCK;
        }
        if (source.is(DamageTypeTags.IS_FIRE) || source.is(WFDamageTypes.FIRE)) {
            return Artifact.Hit.BURN;
        }
        if (source.is(DamageTypeTags.IS_EXPLOSION) || source.is(WFDamageTypes.EXPLOSIVE)) {
            return Artifact.Hit.EXPLOSION;
        }
        if (source.is(WFDamageTypes.ACID) || source.is(WFDamageTypes.GAS)) {
            return Artifact.Hit.CHEMICAL;
        }
        if (source instanceof com.wf.wflib.round.RoundDamageSource || source.is(DamageTypeTags.IS_PROJECTILE)) {
            return Artifact.Hit.BULLET;
        }
        if (source.is(WFDamageTypes.GRAVITY_ANOMALY) || source.is(DamageTypeTags.IS_FALL)
                || source.is(DamageTypes.FLY_INTO_WALL) || source.is(DamageTypes.FALLING_BLOCK)
                || source.is(DamageTypes.FALLING_ANVIL)) {
            return Artifact.Hit.STRIKE;
        }
        if (source.is(DamageTypes.MOB_ATTACK) || source.is(DamageTypes.MOB_ATTACK_NO_AGGRO)
                || source.is(DamageTypes.PLAYER_ATTACK)) {
            return Artifact.Hit.WOUND;
        }
        return null;
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
