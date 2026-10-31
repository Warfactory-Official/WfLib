package com.wf.wflib.hazard;

import com.wf.wflib.core.api.WFSystem;
import com.wf.wflib.hazard.api.HazardAssignment;
import com.wf.wflib.hazard.api.HazardContent;
import com.wf.wflib.hazard.api.HazardEntityEffects;
import com.wf.wflib.hazard.api.HazardRules;
import com.wf.wflib.hazard.api.LivingHazardProps;
import com.wf.wflib.hazard.client.GeigerHud;
import com.wf.wflib.hazard.client.HazardClient;
import com.wf.wflib.hazard.client.RadiationScreen;
import com.wf.wflib.hazard.transformer.HazardTransformerPostCustom;
import com.wf.wflib.hazard.transformer.HazardTransformerRadiationContainer;
import com.wf.wflib.hazard.transformer.HazardTransformerRadiationNBT;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Item;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.server.ServerAboutToStartEvent;
import net.neoforged.neoforge.event.tick.EntityTickEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.registries.DataPackRegistryEvent;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

import com.mojang.serialization.Codec;
import com.wf.wflib.core.api.WFLib;

/** Item and entity hazards (NTM hazard port): datapack-assigned rows, transformers, per-player inventory scan, geiger counter. */
public final class HazardSystem implements WFSystem {
    private static volatile boolean active;

    /** Mixin guard: true once this system registered. */
    public static boolean active() {
        return active;
    }

    @Override
    public String id() {
        return "hazard";
    }

    @Override
    public java.util.Set<String> requires() {
        return java.util.Set.of("radiation", "xray");
    }

    @Override
    public void register(IEventBus modBus, ModContainer container) {
        com.wf.wflib.core.api.CreativeTabs.contribute("hazard", 390, out -> out.accept(com.wf.wflib.hazard.api.HazardContent.GEIGER_COUNTER_ITEM.get()));
        active = true;
        container.registerConfig(ModConfig.Type.COMMON, HazardConfig.SPEC, "wflib-hazard.toml");
        modBus.addListener(HazardConfig::onLoad);

        DeferredRegister<DataComponentType<?>> components =
                DeferredRegister.create(Registries.DATA_COMPONENT_TYPE, WFLib.MODID);
        components.register("neutron_activation", () -> DataComponentType.<Float>builder()
                .persistent(Codec.FLOAT).networkSynchronized(ByteBufCodecs.FLOAT).build());
        components.register("haz_radiation", () -> DataComponentType.<Float>builder()
                .persistent(Codec.FLOAT).networkSynchronized(ByteBufCodecs.FLOAT).build());
        components.register(modBus);
        DeferredRegister<AttachmentType<?>> attachments =
                DeferredRegister.create(NeoForgeRegistries.Keys.ATTACHMENT_TYPES, WFLib.MODID);
        attachments.register("hazard_props", () -> AttachmentType.builder(LivingHazardProps::new)
                .serialize(LivingHazardProps.MAP_CODEC.codec(), p -> !p.isDefault()).build());
        attachments.register(modBus);
        DeferredRegister<MobEffect> effects = DeferredRegister.create(Registries.MOB_EFFECT, WFLib.MODID);
        effects.register("radiation", HazardEffects.Radiation::new);
        effects.register("radaway", HazardEffects.Radaway::new);
        effects.register("radx", () -> new HazardEffects.Plain(MobEffectCategory.BENEFICIAL, 0xBB4B00));
        effects.register("stability", () -> new HazardEffects.Plain(MobEffectCategory.BENEFICIAL, 0xD0D0D0));
        effects.register(modBus);
        DeferredRegister<SoundEvent> sounds = DeferredRegister.create(Registries.SOUND_EVENT, WFLib.MODID);
        sounds.register("hazard.cough", () -> sound("hazard.cough"));
        sounds.register("hazard.vomit", () -> sound("hazard.vomit"));
        for (int i = 1; i <= HazardContent.GEIGER.size(); i++) {
            String name = "hazard.geiger" + i;
            sounds.register(name, () -> sound(name));
        }
        sounds.register("hazard.tech_boop", () -> sound("hazard.tech_boop"));
        sounds.register(modBus);
        DeferredRegister<Item> items = DeferredRegister.create(Registries.ITEM, WFLib.MODID);
        items.register("geiger_counter", () -> new GeigerCounterItem(new Item.Properties().stacksTo(1)));
        items.register(modBus);

        modBus.addListener(DataPackRegistryEvent.NewRegistry.class, event ->
                event.dataPackRegistry(HazardAssignment.REGISTRY, HazardAssignment.CODEC, HazardAssignment.CODEC));
        modBus.addListener(RegisterPayloadHandlersEvent.class, event -> event.registrar("hazard-1")
                .playToClient(HazardSyncPacket.TYPE, HazardSyncPacket.STREAM_CODEC,
                        (pkt, ctx) -> ctx.enqueueWork(() -> HazardClient.handleSync(pkt))));

        ResourceLocation builtin = ResourceLocation.fromNamespaceAndPath(WFLib.MODID, "radiation_nbt");
        HazardRules.TRANSFORMERS.register(builtin, 0, new HazardTransformerRadiationNBT());
        HazardRules.TRANSFORMERS.register(ResourceLocation.fromNamespaceAndPath(WFLib.MODID, "radiation_container"), 0,
                new HazardTransformerRadiationContainer());
        HazardRules.TRANSFORMERS.register(ResourceLocation.fromNamespaceAndPath(WFLib.MODID, "post_custom"), 0,
                new HazardTransformerPostCustom());

        // Before radiation's resolved source table, which reads the hazard rows.
        NeoForge.EVENT_BUS.addListener(EventPriority.HIGH, ServerAboutToStartEvent.class,
                event -> HazardRules.applyDataPack(event.getServer().registryAccess()));
        NeoForge.EVENT_BUS.addListener(ServerTickEvent.Post.class, event -> HazardRules.onServerTick(event.getServer()));
        NeoForge.EVENT_BUS.addListener(EntityTickEvent.Post.class, event -> {
            if (event.getEntity().level().isClientSide()) return;
            if (event.getEntity() instanceof LivingEntity living) {
                HazardRules.tickMobEquipment(living);
                HazardEntityEffects.onUpdate(living);
            } else if (event.getEntity() instanceof ItemEntity item) {
                HazardRules.tickDroppedItem(item);
            }
        });
    }

    @Override
    public void registerClient(IEventBus modBus) {
        NeoForge.EVENT_BUS.register(HazardClient.class);
        NeoForge.EVENT_BUS.register(RadiationScreen.class);
        modBus.addListener(FMLClientSetupEvent.class, event -> event.enqueueWork(GeigerHud::register));
    }

    private static SoundEvent sound(String name) {
        return SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath(WFLib.MODID, name));
    }
}
