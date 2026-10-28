package com.wf.wflib.anomaly;

import com.mojang.brigadier.context.CommandContext;
import com.wf.wflib.WFLib;
import io.netty.buffer.ByteBuf;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.entity.living.LivingDropsEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * Anomaly plumbing: op {@code /wflib anomalies [on|off]} (flips {@link AnomalyConfig#enabled}, saved to the world's
 * config, pushed to clients), {@code /wflib anomalies site <tier> [motif [ruin]]} (a site near you, for trying them),
 * site worldgen, and the drops of torn bodies.
 */
public final class Anomalies {
    private Anomalies() {
    }

    public static void register(IEventBus modBus) {
        modBus.addListener(Anomalies::onPayloads);
        com.wf.wflib.anomaly.site.AnomalySites.register(modBus);
        NeoForge.EVENT_BUS.addListener(Anomalies::onCommands);
        NeoForge.EVENT_BUS.addListener(Anomalies::onDrops);
        NeoForge.EVENT_BUS.addListener(Anomalies::onIncomingDamage);
    }

    /** Rebounder ({@code zone_mine_sphere}): a round or arrow at someone inside one keeps 0-50% of its damage. */
    private static void onIncomingDamage(LivingIncomingDamageEvent e) {
        net.minecraft.world.damagesource.DamageSource source = e.getSource();
        if (!AnomalyConfig.enabled || !(source instanceof com.wf.wflib.round.RoundDamageSource
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
        if (e.getSource().is(com.wf.wflib.damage.WFDamageTypes.GRAVITY_ANOMALY)) {
            for (net.minecraft.world.entity.item.ItemEntity drop : e.getDrops()) {
                drop.setDeltaMovement(((MincerTuning) AnomalyType.VORTEX.behaviour()).launch(drop.getRandom()).scale(0.6D));
            }
        }
    }

    private static void onPayloads(RegisterPayloadHandlersEvent e) {
        e.registrar(WFLib.MODID).versioned("anomalies-1")
                .playToClient(Switch.TYPE, Switch.STREAM_CODEC, Switch::handle);
    }

    private static void onCommands(RegisterCommandsEvent e) {
        e.getDispatcher().register(Commands.literal("wflib").then(Commands.literal("anomalies")
                .requires(s -> s.hasPermission(2))
                .executes(c -> {
                    c.getSource().sendSuccess(() -> Component.literal("Anomalies are "
                            + (AnomalyConfig.enabled ? "on" : "off")), false);
                    return AnomalyConfig.enabled ? 1 : 0;
                })
                .then(Commands.literal("on").executes(c -> set(c, true)))
                .then(Commands.literal("off").executes(c -> set(c, false)))
                .then(Commands.literal("site").then(Commands.argument("tier",
                                com.mojang.brigadier.arguments.IntegerArgumentType.integer(1,
                                        com.wf.wflib.anomaly.site.AnomalyMix.TIERS))
                        .executes(c -> site(c, null, null))
                        .then(Commands.argument("motif", com.mojang.brigadier.arguments.StringArgumentType.word())
                                .suggests((c, b) -> suggest(b, com.wf.wflib.anomaly.site.SitePlan.Motif.values()))
                                .executes(c -> site(c, word(c, "motif"), null))
                                .then(Commands.argument("ruin", com.mojang.brigadier.arguments.StringArgumentType.word())
                                        .suggests((c, b) -> suggest(b, com.wf.wflib.anomaly.site.SitePlan.Overlay.values()))
                                        .executes(c -> site(c, word(c, "motif"), word(c, "ruin")))))))));
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
        com.wf.wflib.anomaly.site.SitePlan.Motif m;
        com.wf.wflib.anomaly.site.SitePlan.Overlay o;
        try {
            m = named(com.wf.wflib.anomaly.site.SitePlan.Motif.class, motif);
            o = named(com.wf.wflib.anomaly.site.SitePlan.Overlay.class, ruin);
        } catch (IllegalArgumentException e) {
            c.getSource().sendFailure(Component.literal("No motif/ruin " + motif + " " + ruin));
            return 0;
        }
        var made = com.wf.wflib.anomaly.site.AnomalySites.place(c.getSource().getLevel(),
                net.minecraft.core.BlockPos.containing(c.getSource().getPosition()), tier, m, o, 128);
        if (made == null) {
            c.getSource().sendFailure(Component.literal("No ground within 128 blocks takes a site (water or too steep)"));
            return 0;
        }
        c.getSource().sendSuccess(() -> Component.literal(String.format(java.util.Locale.ROOT,
                "Tier %d %s%s at %d %d, radius %d: %d anomalies, %d artefacts%s", tier,
                made.plan().motif().name().toLowerCase(java.util.Locale.ROOT),
                made.plan().overlay() == null ? "" : " + " + made.plan().overlay().name().toLowerCase(java.util.Locale.ROOT),
                made.centre().getX(), made.centre().getZ(), made.plan().radius(), made.anomalies(), made.artefacts(),
                AnomalyConfig.enabled ? "" : " (anomalies are off: inert)")), true);
        return made.anomalies();
    }

    private static int set(CommandContext<CommandSourceStack> c, boolean on) {
        AnomalyConfig.set(on);
        PacketDistributor.sendToAllPlayers(new Switch(on));
        c.getSource().sendSuccess(() -> Component.literal("Anomalies " + (on ? "on" : "off")
                + " (saved to this world's wflib-anomalies-server.toml)"), true);
        return 1;
    }

    /** S2C: the server's {@link AnomalyConfig#enabled} changed. */
    public record Switch(boolean on) implements CustomPacketPayload {
        public static final Type<Switch> TYPE =
                new Type<>(ResourceLocation.fromNamespaceAndPath(WFLib.MODID, "anomalies_switch"));
        public static final StreamCodec<ByteBuf, Switch> STREAM_CODEC =
                ByteBufCodecs.BOOL.map(Switch::new, Switch::on);

        @Override
        public Type<Switch> type() {
            return TYPE;
        }

        void handle(IPayloadContext ctx) {
            ctx.enqueueWork(() -> AnomalyConfig.enabled = on);
        }
    }
}
