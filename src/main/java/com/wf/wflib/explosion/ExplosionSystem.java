package com.wf.wflib.explosion;

import com.wf.wflib.core.api.WFSystem;
import com.wf.wflib.explosion.client.ExplosionClient;
import com.wf.wflib.explosion.debug.ExplosionDebug;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.common.world.chunk.RegisterTicketControllersEvent;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import com.wf.wflib.core.api.WFLib;
import com.wf.wflib.explosion.api.EntityNukeTorex;

public final class ExplosionSystem implements WFSystem {
    private static final DeferredRegister<EntityType<?>> ENTITY_TYPES =
            DeferredRegister.create(Registries.ENTITY_TYPE, WFLib.MODID);
    private static final DeferredRegister<SoundEvent> SOUND_EVENTS =
            DeferredRegister.create(Registries.SOUND_EVENT, WFLib.MODID);

    /** Server-side multi-tick ray explosion driver; renders nothing. */
    public static final DeferredHolder<EntityType<?>, EntityType<EntityNukeExplosionMK5>> NUKE_EXPLOSION =
            ENTITY_TYPES.register("nuke_explosion", () -> EntityType.Builder.<EntityNukeExplosionMK5>of(EntityNukeExplosionMK5::new, MobCategory.MISC)
                    .sized(1.0F, 1.0F)
                    .build("nuke_explosion")
            );

    /** Toroidal-convection mushroom cloud effect; stationary. */
    public static final DeferredHolder<EntityType<?>, EntityType<EntityNukeTorex>> NUKE_TOREX =
            ENTITY_TYPES.register("torex", () -> EntityType.Builder.<EntityNukeTorex>of(EntityNukeTorex::new, MobCategory.MISC)
                    .noSave()
                    .fireImmune()
                    .sized(20F, 40F)
                    .clientTrackingRange(64)
                    .updateInterval(Integer.MAX_VALUE)
                    .build("torex")
            );

    public static final DeferredHolder<SoundEvent, SoundEvent> NUCLEAR_EXPLOSION = SOUND_EVENTS.register(
            "weapon.nuclear_explosion", () -> SoundEvent.createVariableRangeEvent(
                    ResourceLocation.fromNamespaceAndPath(WFLib.MODID, "weapon.nuclear_explosion")));

    @Override
    public String id() {
        return "explosion";
    }

    @Override
    public boolean mandatory() {
        return true;
    }

    private static volatile boolean active;

    /** Mixin guard: true once this system registered. */
    public static boolean active() {
        return active;
    }

    @Override
    public void register(IEventBus modBus, ModContainer container) {
        com.wf.wflib.explosion.api.ExplosionSounds.register(modBus);
        active = true;
        ENTITY_TYPES.register(modBus);
        SOUND_EVENTS.register(modBus);
        modBus.addListener(RegisterPayloadHandlersEvent.class, ExplosionSystem::registerPayloads);
        modBus.addListener(RegisterTicketControllersEvent.class,
                event -> event.register(EntityExplosionChunkLoading.CHUNK_TICKET));
        NeoForge.EVENT_BUS.addListener(RegisterCommandsEvent.class, ExplosionDebug::onRegisterCommands);
    }

    @Override
    public void registerClient(IEventBus modBus) {
        ExplosionClient.register(modBus);
    }

    private static void registerPayloads(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar r = event.registrar("explosion-1");
        r.playToClient(ExplosionKnockbackPacket.TYPE, ExplosionKnockbackPacket.STREAM_CODEC,
                (pkt, ctx) -> ctx.enqueueWork(() -> ExplosionClient.handleKnockback(pkt)));
        r.playToClient(ExplosionBlockFXPacket.TYPE, ExplosionBlockFXPacket.STREAM_CODEC,
                (pkt, ctx) -> ctx.enqueueWork(() -> ExplosionClient.handleBlockFX(pkt)));
        r.playToClient(AuxParticlePacket.TYPE, AuxParticlePacket.STREAM_CODEC,
                (pkt, ctx) -> ctx.enqueueWork(() -> ExplosionClient.handleAuxParticle(pkt)));
    }
}
