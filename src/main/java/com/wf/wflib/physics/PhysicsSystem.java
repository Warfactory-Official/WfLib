package com.wf.wflib.physics;

import com.wf.wflib.core.api.ServerTicks;
import com.wf.wflib.core.api.TickOrder;
import com.wf.wflib.core.api.WFLib;
import com.wf.wflib.core.api.WFSystem;
import com.wf.wflib.physics.collision.ChunkCollisionCacheEvents;
import com.wf.wflib.physics.predict.PredictionAcks;
import com.wf.wflib.physics.predict.PredictionState;
import com.wf.wflib.physics.step.RigidStep;
import com.wf.wflib.physics.api.CarrierDecks;
import com.wf.wflib.physics.api.DeckRejoin;
import com.wf.wflib.physics.api.ServerDeckRejoin;
import com.wf.wflib.physics.api.TickClock;
import com.wf.wflib.physics.api.event.PhysicsLevelUnloadEvent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;

/**
 * Rigid-body engine: convex hulls, chunk collision snapshots, narrowphase (Java + native), solver, per-level step;
 * decks (entities and bodies carried in a carrier's frame); operator-side prediction.
 */
public final class PhysicsSystem implements WFSystem {
    private static volatile boolean active;

    /** Mixin guard: true once this system registered. */
    public static boolean active() {
        return active;
    }

    @Override
    public String id() {
        return "physics";
    }

    @Override
    public void register(IEventBus modBus, ModContainer container) {
        active = true;
        container.registerConfig(ModConfig.Type.COMMON, PhysicsConfig.SPEC, "wflib-physics.toml");
        modBus.addListener(PhysicsConfig::onLoad);
        NeoForge.EVENT_BUS.register(ChunkCollisionCacheEvents.class);
        NeoForge.EVENT_BUS.register(RigidStep.class);
        ServerTicks.LEVEL_POST.register(ResourceLocation.fromNamespaceAndPath(WFLib.MODID, "physics"), TickOrder.PHYSICS,
                RigidStep::tick);
        NeoForge.EVENT_BUS.addListener(PhysicsLevelUnloadEvent.class, event -> CarrierDecks.forget(event.level()));
        NeoForge.EVENT_BUS.addListener(PlayerEvent.PlayerLoggedInEvent.class, event -> {
            if (event.getEntity() instanceof ServerPlayer player) {
                DeckRejoin.onLoggedIn(player);
            }
        });
        ServerTicks.LEVEL_POST.register(ResourceLocation.fromNamespaceAndPath(WFLib.MODID, "prediction"),
                TickOrder.PREDICTION, PredictionAcks::sendDue);
        modBus.addListener(RegisterPayloadHandlersEvent.class, event -> event.registrar("physics-3")
                .playToClient(ServerDeckRejoin.TYPE, ServerDeckRejoin.STREAM_CODEC, ServerDeckRejoin::handle)
                .playToClient(PredictionState.TYPE, PredictionState.STREAM_CODEC, PredictionState::handle));
    }

    @Override
    public void registerClient(IEventBus modBus) {
        NeoForge.EVENT_BUS.addListener(ClientTickEvent.Pre.class, event -> TickClock.advanceClient());
    }
}
