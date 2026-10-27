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
 * config, pushed to clients) and the drops of torn bodies.
 */
public final class Anomalies {
    private Anomalies() {
    }

    public static void register(IEventBus modBus) {
        modBus.addListener(Anomalies::onPayloads);
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
                .then(Commands.literal("off").executes(c -> set(c, false)))));
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
