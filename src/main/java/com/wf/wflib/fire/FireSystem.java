package com.wf.wflib.fire;

import com.wf.wflib.core.api.WFSystem;
import com.wf.wflib.fire.api.WFFireData;
import com.wf.wflib.fire.client.FireClient;
import com.wf.wflib.fire.debug.FireDebug;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;
import com.wf.wflib.core.api.WFLib;
import com.wf.wflib.fire.api.FireLingeringEntity;

public final class FireSystem implements WFSystem {
    private static final DeferredRegister<EntityType<?>> ENTITY_TYPES =
            DeferredRegister.create(Registries.ENTITY_TYPE, WFLib.MODID);
    private static final DeferredRegister<SoundEvent> SOUND_EVENTS =
            DeferredRegister.create(Registries.SOUND_EVENT, WFLib.MODID);
    private static final DeferredRegister<AttachmentType<?>> ATTACHMENT_TYPES =
            DeferredRegister.create(NeoForgeRegistries.Keys.ATTACHMENT_TYPES, WFLib.MODID);

    public static final DeferredHolder<EntityType<?>, EntityType<FireLingeringEntity>> FIRE_LINGERING =
            ENTITY_TYPES.register("fire_lingering", () -> EntityType.Builder.of(FireLingeringEntity::new, MobCategory.MISC)
                    .sized(1.0F, 1.0F)
                    .clientTrackingRange(10)
                    .updateInterval(Integer.MAX_VALUE)
                    .fireImmune()
                    .noSave()
                    .noSummon()
                    .build("fire_lingering")
            );

    public static final DeferredHolder<SoundEvent, SoundEvent> FIRE_DISINTEGRATION = SOUND_EVENTS.register(
            "weapon.fire.disintegration", () -> SoundEvent.createVariableRangeEvent(
                    ResourceLocation.fromNamespaceAndPath(WFLib.MODID, "weapon.fire.disintegration")));

    public static final DeferredHolder<AttachmentType<?>, AttachmentType<WFFireData>> FIRE_DATA =
            ATTACHMENT_TYPES.register("fire", () ->
                    AttachmentType.serializable(WFFireData::new).copyOnDeath().build());

    @Override
    public String id() {
        return "fire";
    }

    @Override
    public boolean mandatory() {
        return true;
    }

    @Override
    public void register(IEventBus modBus, ModContainer container) {
        ENTITY_TYPES.register(modBus);
        SOUND_EVENTS.register(modBus);
        ATTACHMENT_TYPES.register(modBus);
        NeoForge.EVENT_BUS.addListener(RegisterCommandsEvent.class, FireDebug::onRegisterCommands);
    }

    @Override
    public void registerClient(IEventBus modBus) {
        FireClient.register(modBus);
    }
}
