package com.wf.wflib.artifact.api;

import com.wf.wflib.core.api.Hook;
import com.wf.wflib.core.api.WFSystems;
import com.wf.wflib.damage.api.WFDamageTypes;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;

import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.function.BiConsumer;

/**
 * Artefacts at work: each worn in a Curios {@code artifact} slot scales the hits of its kinds by
 * {@code 1 - immunity} (GAMMA stacks them the same way, one after another), gives health (or drains it), stamina (with
 * WFWeight) and wound closing (with a wound mod) over time, keeps psy off (a stress mod) and radiation
 * ({@link #shieldDose}). Without Curios nothing is worn.
 */
public final class Artifacts {
    /**
     * How worn artefacts give health back (or, negative, drain it). A health mod that clamps vanilla healing
     * replaces it with its own, as it claims armour ({@code ArmorResolution.PLAYER_RESOLUTION}).
     */
    public static final Hook<BiConsumer<ServerPlayer, Float>> HEALER = Hook.of("artifact healer", (player, hp) -> {
        if (hp > 0.0F) {
            player.heal(hp);
        } else if (hp < 0.0F) {
            player.hurt(player.damageSources().starve(), -hp);
        }
    });

    /**
     * How worn artefacts close wounds (negative: open them): called once a second with their summed bleeding_restore
     * per real second. A mod with wounds claims it; without one there is nothing to close.
     */
    public static final Hook<BiConsumer<ServerPlayer, Float>> CLOTTER = Hook.of("artifact clotter", (player, rate) -> {
    });

    private Artifacts() {
    }

    public static void register() {
        // Before NORMAL: a wound mod turns the hit into wounds there, so the cut must already be in the amount.
        NeoForge.EVENT_BUS.addListener(EventPriority.HIGH, Artifacts::onIncomingDamage);
        NeoForge.EVENT_BUS.addListener(Artifacts::onPlayerTick);
        NeoForge.EVENT_BUS.addListener(EventPriority.HIGHEST, Artifacts::onJoin);
    }

    /** Fresh drops swap inline; {@link ArtifactItem#createEntity}'s swap lands a task later (kept for disk loads). */
    private static void onJoin(EntityJoinLevelEvent event) {
        if (event.loadedFromDisk() || !(event.getLevel() instanceof ServerLevel level)
                || event.getEntity().getClass() != ItemEntity.class) {
            return;
        }
        ItemEntity item = (ItemEntity) event.getEntity();
        if (item.getItem().getItem() instanceof ArtifactItem) {
            event.setCanceled(true);
            level.addFreshEntity(ZoneArtifact.dropped(level, item, item.getItem()));
        }
    }

    /**
     * Once a second: worn artefacts' health back or drained ({@code health_restore_speed}), wounds closed, radiation
     * dosed or cleared ({@code radiation_restore_speed}), hunger ({@code satiety_restore_speed} as exhaustion).
     */
    private static void onPlayerTick(net.neoforged.neoforge.event.tick.PlayerTickEvent.Post e) {
        Player player = e.getEntity();
        if (!(e.getEntity() instanceof ServerPlayer server) || player.tickCount % 20 != 0 || player.isDeadOrDying()) {
            return;
        }
        float heal = 0.0F;
        float clot = 0.0F;
        float rad = 0.0F;
        float food = 0.0F;
        for (Artifact a : wornArtifacts(player)) {
            heal += a.healthPerSecond(player.getMaxHealth());
            clot += a.bleedingPerSecond();
            rad += a.radiationPerSecond();
            food += a.foodPerSecond();
        }
        if (heal != 0.0F) {
            HEALER.get().accept(server, heal);
        }
        if (clot != 0.0F) {
            CLOTTER.get().accept(server, clot);
        }
        if (rad != 0.0F && WFSystems.enabled("hazard")) {
            com.wf.wflib.hazard.api.LivingHazardProps.incrementRadiation(player, rad);
        }
        if (food < 0.0F) {
            player.causeFoodExhaustion(-food * 4.0F);
        }
    }

    /**
     * The share of psy (here: stress) the worn artefacts keep off {@code player}, stacked as GAMMA stacks them;
     * negative makes it worse. A stress mod scales gain by {@code 1 - psyProtection}.
     */
    public static float psyProtection(Player player) {
        return 1.0F - damageScale(wornArtifacts(player), Artifact.Hit.PSY);
    }

    /** What is left of a hit of {@code hit} through {@code worn}: each takes its share in turn; never below 0. */
    static float damageScale(List<Artifact> worn, @Nullable Artifact.Hit hit) {
        float scale = 1.0F;
        if (hit != null) {
            for (Artifact a : worn) {
                scale *= 1.0F - a.immunity(hit);
            }
        }
        return Math.max(0.0F, scale);
    }

    private static void onIncomingDamage(LivingIncomingDamageEvent e) {
        if (!(e.getEntity() instanceof Player player) || player.level().isClientSide) {
            return;
        }
        float scale = damageScale(wornArtifacts(player), hit(e.getSource()));
        if (scale != 1.0F) {
            e.setAmount(e.getAmount() * scale);
        }
    }

    /** Server tick a player's shield budget was last refilled on, and what is left of it (RAD). */
    private static final Map<Player, double[]> SHIELD = new WeakHashMap<>();

    /**
     * A radiation dose (RAD) through {@code player}'s worn artefacts: their {@code radiation_immunity} is a budget of
     * {@link Artifact#radShieldPerSecond} a second, spent a tick at a time across every dose that tick; a negative
     * one adds its share to the first dose of each tick instead.
     */
    public static double shieldDose(Player player, double dose) {
        if (dose <= 0.0D) {
            return dose;
        }
        List<Artifact> worn = wornArtifacts(player);
        if (worn.isEmpty()) {
            return dose;
        }
        long tick = player.level().getGameTime();
        double[] budget = SHIELD.computeIfAbsent(player, p -> new double[] {Long.MIN_VALUE, 0.0D});
        if (budget[0] != tick) {
            budget[0] = tick;
            budget[1] = radShieldPerSecond(worn) / 20.0D;
        }
        double[] out = shield(dose, budget[1]);
        budget[1] = out[1];
        return out[0];
    }

    /** {@code {dose left, budget left}}: a positive budget soaks the dose up; a negative one is added once. */
    static double[] shield(double dose, double budget) {
        if (budget < 0.0D) {
            return new double[] {dose - budget, 0.0D};
        }
        double taken = Math.min(dose, budget);
        return new double[] {dose - taken, budget - taken};
    }

    static float radShieldPerSecond(List<Artifact> worn) {
        float sum = 0.0F;
        for (Artifact a : worn) {
            sum += a.radShieldPerSecond();
        }
        return sum;
    }

    /** STALKER's hit kind for a damage: what an artefact's {@code *_immunity} answers to. */
    @Nullable
    public static Artifact.Hit hit(DamageSource source) {
        if (source.is(WFDamageTypes.ELECTRIC) || source.is(DamageTypes.LIGHTNING_BOLT)) {
            return Artifact.Hit.SHOCK;
        }
        if (source.is(DamageTypeTags.IS_FIRE) || source.is(WFDamageTypes.FIRE)) {
            return Artifact.Hit.BURN;
        }
        if (source.is(DamageTypeTags.IS_EXPLOSION) || source.is(WFDamageTypes.EXPLOSIVE)) {
            return Artifact.Hit.EXPLOSION;
        }
        if (source.is(WFDamageTypes.PSY)) {
            return Artifact.Hit.PSY;
        }
        if (source.is(WFDamageTypes.ACID) || source.is(WFDamageTypes.GAS)) {
            return Artifact.Hit.CHEMICAL;
        }
        if (source instanceof com.wf.wflib.round.api.RoundDamageSource || source.is(DamageTypeTags.IS_PROJECTILE)) {
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

    public static List<Artifact> wornArtifacts(Player player) {
        List<ItemStack> worn = worn(player);
        if (worn.isEmpty()) {
            return List.of();
        }
        List<Artifact> out = new java.util.ArrayList<>(worn.size());
        for (ItemStack stack : worn) {
            if (stack.getItem() instanceof ArtifactItem item) {
                out.add(item.artifact());
            }
        }
        return out;
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
