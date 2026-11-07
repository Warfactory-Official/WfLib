package com.wf.wflib.ragdoll;

import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.norwood.ahf.hook.AhfHooks;
import com.wf.wflib.core.api.ServerTicks;
import com.wf.wflib.core.api.TickOrder;
import com.wf.wflib.core.api.WFLib;
import com.wf.wflib.core.api.WFSystem;
import com.wf.wflib.ragdoll.api.Ragdolls;
import com.wf.wflib.ragdoll.client.RagdollClient;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;

import java.util.Set;

/** Player ragdolls: XPBD server sim -> AHF rig + jointed body through {@link AhfHooks.RigPose}. */
public final class RagdollSystem implements WFSystem {
    private static volatile boolean active;

    public static boolean active() {
        return active;
    }

    @Override
    public String id() {
        return "ragdoll";
    }

    @Override
    public Set<String> requires() {
        return Set.of("physics");
    }

    @Override
    public void register(IEventBus modBus, ModContainer container) {
        active = true;
        AhfHooks.addRigPose(RagdollStore.HOOK);
        ServerTicks.LEVEL_POST.register(ResourceLocation.fromNamespaceAndPath(WFLib.MODID, "ragdoll"), TickOrder.RAGDOLL,
                RagdollServer::tick);
        ServerTicks.STOPPING.register(ResourceLocation.fromNamespaceAndPath(WFLib.MODID, "ragdoll"), TickOrder.RAGDOLL_STOP,
                server -> RagdollServer.stopped());
        NeoForge.EVENT_BUS.addListener(EventPriority.LOWEST, true, LivingIncomingDamageEvent.class,
                RagdollServer::onIncomingDamage);
        NeoForge.EVENT_BUS.addListener(PlayerEvent.StartTracking.class, RagdollServer::onStartTracking);
        NeoForge.EVENT_BUS.addListener(PlayerEvent.PlayerRespawnEvent.class, RagdollServer::onRespawn);
        NeoForge.EVENT_BUS.addListener(RegisterCommandsEvent.class, RagdollSystem::commands);
        modBus.addListener(RegisterPayloadHandlersEvent.class, event -> {
            var r = event.registrar("ragdoll-1");
            r.playToClient(RagdollPackets.Pose.TYPE, RagdollPackets.Pose.STREAM_CODEC, RagdollPackets.Pose::handle);
            r.playToClient(RagdollPackets.Stop.TYPE, RagdollPackets.Stop.STREAM_CODEC, RagdollPackets.Stop::handle);
        });
    }

    @Override
    public void registerClient(IEventBus modBus) {
        NeoForge.EVENT_BUS.addListener(ClientTickEvent.Pre.class, RagdollClient::onTick);
        NeoForge.EVENT_BUS.addListener(ClientPlayerNetworkEvent.LoggingOut.class, RagdollClient::onLogout);
    }

    private static void commands(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("wflib").requires(s -> s.hasPermission(2))
                .then(Commands.literal("ragdoll")
                        .then(Commands.argument("player", EntityArgument.player())
                                .then(Commands.literal("on").executes(c -> {
                                    Ragdolls.set(EntityArgument.getPlayer(c, "player"), true);
                                    return 1;
                                }))
                                .then(Commands.literal("off").executes(c -> {
                                    Ragdolls.set(EntityArgument.getPlayer(c, "player"), false);
                                    return 1;
                                }))
                                .then(Commands.literal("blast")
                                        .then(Commands.argument("speed", DoubleArgumentType.doubleArg(0, 50)).executes(c -> {
                                            ServerPlayer p = EntityArgument.getPlayer(c, "player");
                                            Vec3 v = p.getLookAngle().multiply(-1, 0, -1).normalize().add(0, 0.35, 0)
                                                    .normalize().scale(DoubleArgumentType.getDouble(c, "speed"));
                                            Ragdolls.blast(p, v);
                                            return 1;
                                        })))
                                .then(Commands.literal("hard").executes(c -> {
                                    var sim = RagdollServer.simOf(EntityArgument.getPlayer(c, "player"));
                                    if (sim != null) {
                                        sim.softLimits = false;
                                    }
                                    return 1;
                                })))));
    }
}
