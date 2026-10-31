package com.wf.wflib.grenade;

import com.wf.wflib.core.api.WFSystem;
import com.wf.wflib.grenade.api.GrenadeWarheads;
import com.wf.wflib.grenade.api.ThrownCharge;
import com.wf.wflib.grenade.client.Flashbang;
import com.wf.wflib.grenade.client.GrenadeClient;
import com.wf.wflib.grenade.debug.GrenadeCommands;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.registries.DeferredRegister;
import com.wf.wflib.core.api.WFLib;

/** Holders named through {@link com.wf.wflib.grenade.api.Grenades}. */
public final class GrenadeSystem implements WFSystem {
    private static final DeferredRegister<EntityType<?>> ENTITIES =
            DeferredRegister.create(Registries.ENTITY_TYPE, WFLib.MODID);
    private static final DeferredRegister<SoundEvent> SOUNDS =
            DeferredRegister.create(Registries.SOUND_EVENT, WFLib.MODID);

    @Override
    public String id() {
        return "grenade";
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
        active = true;
        ENTITIES.register("thrown_charge", () -> EntityType.Builder.<ThrownCharge>of(ThrownCharge::new, MobCategory.MISC)
                .sized(0.15f, 0.15f).clientTrackingRange(8).updateInterval(1).noSummon().build("thrown_charge"));
        for (String path : new String[] {"bounce", "pin", "spoon", "flashbang", "flashbang_far", "thermite_ignite",
                "thermite_loop", "smoke_hiss", "tinnitus"}) {
            ResourceLocation id = ResourceLocation.fromNamespaceAndPath(WFLib.MODID, "grenade." + path);
            SOUNDS.register("grenade." + path, () -> SoundEvent.createVariableRangeEvent(id));
        }
        ENTITIES.register(modBus);
        SOUNDS.register(modBus);
        GrenadeWarheads.define();
        modBus.addListener(RegisterPayloadHandlersEvent.class, GrenadeSystem::registerPayloads);
        NeoForge.EVENT_BUS.addListener(RegisterCommandsEvent.class, GrenadeCommands::onRegisterCommands);
    }

    @Override
    public void registerClient(IEventBus modBus) {
        GrenadeClient.register(modBus);
    }

    private static void registerPayloads(RegisterPayloadHandlersEvent event) {
        event.registrar("grenade-1").playToClient(FlashPacket.TYPE, FlashPacket.STREAM_CODEC,
                (pkt, ctx) -> ctx.enqueueWork(() -> Flashbang.receive(pkt)));
    }
}
