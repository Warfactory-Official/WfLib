package com.wf.wflib.anomaly;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import com.wf.wflib.anomaly.api.AnomalyCaves;
import com.wf.wflib.anomaly.api.AnomalyMix;
import com.wf.wflib.anomaly.api.AnomalySites;
import com.wf.wflib.anomaly.api.SitePlan;
import com.wf.wflib.damage.api.WFDamageTypes;
import com.wf.wflib.round.api.RoundDamageSource;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Locale;
import java.util.concurrent.CompletableFuture;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.entity.living.LivingDropsEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;

/**
 * Anomaly plumbing: op {@code /wflib anomalies site <tier> [motif [ruin]]} (a site near you, for trying them),
 * {@code cave}, the rebounder's shield and the drops of torn bodies.
 */
public final class Anomalies {
    private Anomalies() {
    }

    static void register() {
        NeoForge.EVENT_BUS.addListener(Anomalies::onCommands);
        NeoForge.EVENT_BUS.addListener(Anomalies::onDrops);
        NeoForge.EVENT_BUS.addListener(Anomalies::onIncomingDamage);
    }

    /** Rebounder ({@code zone_mine_sphere}): a round or arrow at someone inside one keeps 0-50% of its damage. */
    private static void onIncomingDamage(LivingIncomingDamageEvent e) {
        DamageSource source = e.getSource();
        if (!(source instanceof RoundDamageSource
                || source.is(DamageTypeTags.IS_PROJECTILE))) {
            return;
        }
        AnomalyBlockEntity shield = AnomalyBlockEntity.shielding(e.getEntity());
        if (shield != null) {
            e.setAmount(e.getAmount() * e.getEntity().getRandom().nextFloat() * 0.5F);
            if (shield.type().sounds().hit() != null) {
                e.getEntity().level().playSound(null, e.getEntity().blockPosition(),
                        shield.type().sounds().hit().get(), SoundSource.HOSTILE, 0.9F, 1.0F);
            }
        }
    }

    /** What is left of a torn body flies every which way, as the body itself would have. */
    private static void onDrops(LivingDropsEvent e) {
        if (e.getSource().is(WFDamageTypes.GRAVITY_ANOMALY)) {
            for (ItemEntity drop : e.getDrops()) {
                drop.setDeltaMovement(((MincerTuning) AnomalyType.VORTEX.behaviour()).launch(drop.getRandom()).scale(0.6D));
            }
        }
    }

    private static void onCommands(RegisterCommandsEvent e) {
        e.getDispatcher().register(Commands.literal("wflib").then(Commands.literal("anomalies")
                .requires(s -> s.hasPermission(2))
                .then(Commands.literal("site").then(Commands.argument("tier",
                                IntegerArgumentType.integer(1,
                                        AnomalyMix.TIERS))
                        .executes(c -> site(c, null, null))
                        .then(Commands.argument("motif", StringArgumentType.word())
                                .suggests((c, b) -> suggest(b, SitePlan.Motif.values()))
                                .executes(c -> site(c, word(c, "motif"), null))
                                .then(Commands.argument("ruin", StringArgumentType.word())
                                        .suggests((c, b) -> suggest(b, SitePlan.Overlay.values()))
                                        .executes(c -> site(c, word(c, "motif"), word(c, "ruin")))))))
                .then(Commands.literal("cave")
                        .then(Commands.literal("slots").executes(Anomalies::caveSlots))
                        .then(Commands.argument("tier", IntegerArgumentType.integer(1,
                                        AnomalyMix.TIERS))
                                .executes(c -> cave(c, 2))
                                .then(Commands.argument("artefacts",
                                                IntegerArgumentType.integer(0, 64))
                                        .executes(c -> cave(c, IntegerArgumentType
                                                .getInteger(c, "artefacts"))))))));
    }

    /** A cave field round the player, floor under their feet. */
    private static int cave(CommandContext<CommandSourceStack> c, int artefacts) {
        int tier = IntegerArgumentType.getInteger(c, "tier");
        var made = AnomalyCaves.place(c.getSource().getLevel(),
                BlockPos.containing(c.getSource().getPosition()), tier, artefacts);
        c.getSource().sendSuccess(() -> Component.literal(String.format(Locale.ROOT,
                "Tier %d cave field, radius %d: %d anomalies, %d artefacts", tier, made.plan().radius(),
                made.anomalies(), made.artefacts())), true);
        return made.anomalies();
    }

    /** The world's cave allocation: totals, and the five slots nearest the caller. */
    private static int caveSlots(CommandContext<CommandSourceStack> c) {
        var slots = AnomalyCaves.ALLOCATION.get().slots();
        int total = slots.stream().mapToInt(AnomalyCaves.Slot::artefacts).sum();
        c.getSource().sendSuccess(() -> Component.literal(slots.size() + " cave fields, " + total
                + " artefacts in the world"), false);
        Vec3 at = c.getSource().getPosition();
        slots.stream().sorted(Comparator.comparingDouble(s ->
                        Math.hypot(s.chunkX() * 16 + 8 - at.x, s.chunkZ() * 16 + 8 - at.z))).limit(5)
                .forEach(s -> c.getSource().sendSuccess(() -> Component.literal(String.format(Locale.ROOT,
                        "#%d tier %d, %d artefacts, start %d %d", s.index(), s.tier(), s.artefacts(),
                        s.chunkX() * 16 + 8, s.chunkZ() * 16 + 8)), false));
        return slots.size();
    }

    private static String word(CommandContext<CommandSourceStack> c, String name) {
        return StringArgumentType.getString(c, name);
    }

    private static CompletableFuture<Suggestions> suggest(
            SuggestionsBuilder b, Enum<?>[] values) {
        return SharedSuggestionProvider.suggest(Arrays.stream(values)
                .map(e -> e.name().toLowerCase(Locale.ROOT)), b);
    }

    /** {@code any} matches nothing: picked as worldgen would. */
    private static <E extends Enum<E>> E named(Class<E> type, String name) {
        if (name == null || name.equals("any") || name.equals("none")) {
            return null;
        }
        return Enum.valueOf(type, name.toUpperCase(Locale.ROOT));
    }

    private static int site(CommandContext<CommandSourceStack> c, String motif, String ruin) {
        int tier = IntegerArgumentType.getInteger(c, "tier");
        SitePlan.Motif m;
        SitePlan.Overlay o;
        try {
            m = named(SitePlan.Motif.class, motif);
            o = named(SitePlan.Overlay.class, ruin);
        } catch (IllegalArgumentException e) {
            c.getSource().sendFailure(Component.literal("No motif/ruin " + motif + " " + ruin));
            return 0;
        }
        var made = AnomalySites.place(c.getSource().getLevel(),
                BlockPos.containing(c.getSource().getPosition()), tier, m, o, 128);
        if (made == null) {
            c.getSource().sendFailure(Component.literal("No ground within 128 blocks takes a site (water or too steep)"));
            return 0;
        }
        c.getSource().sendSuccess(() -> Component.literal(String.format(Locale.ROOT,
                "Tier %d %s%s at %d %d, radius %d: %d anomalies, %d artefacts", tier,
                made.plan().motif().name().toLowerCase(Locale.ROOT),
                made.plan().overlay() == null ? "" : " + " + made.plan().overlay().name().toLowerCase(Locale.ROOT),
                made.centre().getX(), made.centre().getZ(), made.plan().radius(), made.anomalies(), made.artefacts())), true);
        return made.anomalies();
    }
}
