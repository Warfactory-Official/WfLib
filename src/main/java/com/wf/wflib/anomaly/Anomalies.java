package com.wf.wflib.anomaly;

import com.mojang.brigadier.context.CommandContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
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
        net.minecraft.world.damagesource.DamageSource source = e.getSource();
        if (!(source instanceof com.wf.wflib.round.api.RoundDamageSource
                || source.is(net.minecraft.tags.DamageTypeTags.IS_PROJECTILE))) {
            return;
        }
        AnomalyBlockEntity shield = AnomalyBlockEntity.shielding(e.getEntity());
        if (shield != null) {
            e.setAmount(e.getAmount() * e.getEntity().getRandom().nextFloat() * 0.5F);
            if (shield.type().sounds().hit() != null) {
                e.getEntity().level().playSound(null, e.getEntity().blockPosition(),
                        shield.type().sounds().hit().get(), net.minecraft.sounds.SoundSource.HOSTILE, 0.9F, 1.0F);
            }
        }
    }

    /** What is left of a torn body flies every which way, as the body itself would have. */
    private static void onDrops(LivingDropsEvent e) {
        if (e.getSource().is(com.wf.wflib.damage.api.WFDamageTypes.GRAVITY_ANOMALY)) {
            for (net.minecraft.world.entity.item.ItemEntity drop : e.getDrops()) {
                drop.setDeltaMovement(((MincerTuning) AnomalyType.VORTEX.behaviour()).launch(drop.getRandom()).scale(0.6D));
            }
        }
    }

    private static void onCommands(RegisterCommandsEvent e) {
        e.getDispatcher().register(Commands.literal("wflib").then(Commands.literal("anomalies")
                .requires(s -> s.hasPermission(2))
                .then(Commands.literal("site").then(Commands.argument("tier",
                                com.mojang.brigadier.arguments.IntegerArgumentType.integer(1,
                                        com.wf.wflib.anomaly.api.AnomalyMix.TIERS))
                        .executes(c -> site(c, null, null))
                        .then(Commands.argument("motif", com.mojang.brigadier.arguments.StringArgumentType.word())
                                .suggests((c, b) -> suggest(b, com.wf.wflib.anomaly.api.SitePlan.Motif.values()))
                                .executes(c -> site(c, word(c, "motif"), null))
                                .then(Commands.argument("ruin", com.mojang.brigadier.arguments.StringArgumentType.word())
                                        .suggests((c, b) -> suggest(b, com.wf.wflib.anomaly.api.SitePlan.Overlay.values()))
                                        .executes(c -> site(c, word(c, "motif"), word(c, "ruin")))))))
                .then(Commands.literal("cave")
                        .then(Commands.literal("slots").executes(Anomalies::caveSlots))
                        .then(Commands.argument("tier", com.mojang.brigadier.arguments.IntegerArgumentType.integer(1,
                                        com.wf.wflib.anomaly.api.AnomalyMix.TIERS))
                                .executes(c -> cave(c, 2))
                                .then(Commands.argument("artefacts",
                                                com.mojang.brigadier.arguments.IntegerArgumentType.integer(0, 64))
                                        .executes(c -> cave(c, com.mojang.brigadier.arguments.IntegerArgumentType
                                                .getInteger(c, "artefacts"))))))));
    }

    /** A cave field round the player, floor under their feet. */
    private static int cave(CommandContext<CommandSourceStack> c, int artefacts) {
        int tier = com.mojang.brigadier.arguments.IntegerArgumentType.getInteger(c, "tier");
        var made = com.wf.wflib.anomaly.api.AnomalyCaves.place(c.getSource().getLevel(),
                net.minecraft.core.BlockPos.containing(c.getSource().getPosition()), tier, artefacts);
        c.getSource().sendSuccess(() -> Component.literal(String.format(java.util.Locale.ROOT,
                "Tier %d cave field, radius %d: %d anomalies, %d artefacts", tier, made.plan().radius(),
                made.anomalies(), made.artefacts())), true);
        return made.anomalies();
    }

    /** The world's cave allocation: totals, and the five slots nearest the caller. */
    private static int caveSlots(CommandContext<CommandSourceStack> c) {
        var slots = com.wf.wflib.anomaly.api.AnomalyCaves.ALLOCATION.get().slots();
        int total = slots.stream().mapToInt(com.wf.wflib.anomaly.api.AnomalyCaves.Slot::artefacts).sum();
        c.getSource().sendSuccess(() -> Component.literal(slots.size() + " cave fields, " + total
                + " artefacts in the world"), false);
        net.minecraft.world.phys.Vec3 at = c.getSource().getPosition();
        slots.stream().sorted(java.util.Comparator.comparingDouble(s ->
                        Math.hypot(s.chunkX() * 16 + 8 - at.x, s.chunkZ() * 16 + 8 - at.z))).limit(5)
                .forEach(s -> c.getSource().sendSuccess(() -> Component.literal(String.format(java.util.Locale.ROOT,
                        "#%d tier %d, %d artefacts, start %d %d", s.index(), s.tier(), s.artefacts(),
                        s.chunkX() * 16 + 8, s.chunkZ() * 16 + 8)), false));
        return slots.size();
    }

    private static String word(CommandContext<CommandSourceStack> c, String name) {
        return com.mojang.brigadier.arguments.StringArgumentType.getString(c, name);
    }

    private static java.util.concurrent.CompletableFuture<com.mojang.brigadier.suggestion.Suggestions> suggest(
            com.mojang.brigadier.suggestion.SuggestionsBuilder b, Enum<?>[] values) {
        return net.minecraft.commands.SharedSuggestionProvider.suggest(java.util.Arrays.stream(values)
                .map(e -> e.name().toLowerCase(java.util.Locale.ROOT)), b);
    }

    /** {@code any} matches nothing: picked as worldgen would. */
    private static <E extends Enum<E>> E named(Class<E> type, String name) {
        if (name == null || name.equals("any") || name.equals("none")) {
            return null;
        }
        return Enum.valueOf(type, name.toUpperCase(java.util.Locale.ROOT));
    }

    private static int site(CommandContext<CommandSourceStack> c, String motif, String ruin) {
        int tier = com.mojang.brigadier.arguments.IntegerArgumentType.getInteger(c, "tier");
        com.wf.wflib.anomaly.api.SitePlan.Motif m;
        com.wf.wflib.anomaly.api.SitePlan.Overlay o;
        try {
            m = named(com.wf.wflib.anomaly.api.SitePlan.Motif.class, motif);
            o = named(com.wf.wflib.anomaly.api.SitePlan.Overlay.class, ruin);
        } catch (IllegalArgumentException e) {
            c.getSource().sendFailure(Component.literal("No motif/ruin " + motif + " " + ruin));
            return 0;
        }
        var made = com.wf.wflib.anomaly.api.AnomalySites.place(c.getSource().getLevel(),
                net.minecraft.core.BlockPos.containing(c.getSource().getPosition()), tier, m, o, 128);
        if (made == null) {
            c.getSource().sendFailure(Component.literal("No ground within 128 blocks takes a site (water or too steep)"));
            return 0;
        }
        c.getSource().sendSuccess(() -> Component.literal(String.format(java.util.Locale.ROOT,
                "Tier %d %s%s at %d %d, radius %d: %d anomalies, %d artefacts", tier,
                made.plan().motif().name().toLowerCase(java.util.Locale.ROOT),
                made.plan().overlay() == null ? "" : " + " + made.plan().overlay().name().toLowerCase(java.util.Locale.ROOT),
                made.centre().getX(), made.centre().getZ(), made.plan().radius(), made.anomalies(), made.artefacts())), true);
        return made.anomalies();
    }
}
