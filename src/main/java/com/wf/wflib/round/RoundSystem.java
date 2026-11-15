package com.wf.wflib.round;

import com.wf.wflib.core.api.CreativeTabs;
import com.wf.wflib.core.api.WFLibContent;
import com.wf.wflib.core.api.WFSystem;
import com.wf.wflib.round.api.ImpactEffects;
import com.wf.wflib.round.api.KineticPreset;
import com.wf.wflib.round.api.KineticPresets;
import com.wf.wflib.round.api.KineticShellItem;
import com.wf.wflib.round.api.RocketEntity;
import com.wf.wflib.round.api.Rounds;
import com.wf.wflib.round.client.RoundClient;
import com.wf.wflib.round.debug.RoundCommands;
import com.wf.wflib.round.effect.BuiltinEffects;
import com.wf.wflib.sim.api.SimKinds;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.item.Item;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.server.ServerStartingEvent;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.RegisterEvent;
import com.wf.wflib.core.api.WFLib;
import com.wf.wflib.core.api.WFSystems;
import com.wf.wflib.labs.api.LabContents;
import com.wf.wflib.labs.api.LabProducers;
import com.wf.wflib.labs.api.ScenarioStages;
import com.wf.wflib.round.api.RoundContent;
import com.wf.wflib.round.api.client.ClientRounds;
import com.wf.wflib.round.lab.CoverBlocksProducer;
import com.wf.wflib.round.lab.CoverProducer;
import com.wf.wflib.round.lab.CoverStage;
import com.wf.wflib.round.lab.RoundFlightProducer;
import com.wf.wflib.round.lab.RoundLabContent;
import net.neoforged.neoforge.registries.DeferredHolder;

/** Holders named through {@link com.wf.wflib.round.api.RoundContent}. */
public final class RoundSystem implements WFSystem {
    private static final DeferredRegister<EntityType<?>> ENTITY_TYPES =
            DeferredRegister.create(Registries.ENTITY_TYPE, WFLib.MODID);

    @Override
    public String id() {
        return "round";
    }

    @Override
    public boolean mandatory() {
        return true;
    }

    @Override
    public void register(IEventBus modBus, ModContainer container) {
        CreativeTabs.contribute("round", 300, out -> RoundContent.shellItems().stream()
                .map(DeferredHolder::get)
                .filter(i -> i.preset().id().getNamespace().equals(WFLib.MODID))
                .forEach(out::accept));
        ENTITY_TYPES.register("rocket", () -> EntityType.Builder.<RocketEntity>of(RocketEntity::new, MobCategory.MISC)
                .sized(0.4F, 0.4F)
                .clientTrackingRange(16)
                .updateInterval(1)
                .noSave()
                .noSummon()
                .build("rocket"));
        ENTITY_TYPES.register(modBus);
        modBus.addListener(RoundConfig::onLoad);
        container.registerConfig(ModConfig.Type.COMMON, RoundConfig.SPEC, "wflib-round.toml");
        KineticCatalogue.define();
        BuiltinEffects.register();
        SimKinds.register(Rounds.KIND);
        modBus.addListener(EventPriority.NORMAL, RegisterEvent.class, RoundSystem::registerItems);
        modBus.addListener(RegisterPayloadHandlersEvent.class, RoundSystem::registerPayloads);
        NeoForge.EVENT_BUS.addListener(ServerStartingEvent.class,
                event -> ImpactEffects.check(KineticPresets.all(), event.getServer().registryAccess()));
        NeoForge.EVENT_BUS.addListener(RegisterCommandsEvent.class, RoundCommands::onRegisterCommands);
        if (WFSystems.enabled("labs")) {
            LabProducers.register(new RoundFlightProducer());
            LabProducers.register(new CoverProducer());
            LabProducers.register(new CoverBlocksProducer());
            LabContents.register(new RoundLabContent());
            ScenarioStages.register(new CoverStage());
        }
    }

    @Override
    public void registerClient(IEventBus modBus) {
        RoundClient.register(modBus);
    }

    private static void registerItems(RegisterEvent event) {
        if (!event.getRegistryKey().equals(Registries.ITEM)) {
            return;
        }
        WFLibContent.freeze();
        event.register(Registries.ITEM, helper -> {
            for (KineticPreset preset : KineticPresets.withItems()) {
                helper.register(WFLibContent.itemId(preset.id(), preset.itemPath()),
                        new KineticShellItem(preset, new Item.Properties().stacksTo(preset.stackSize())));
            }
        });
    }

    private static void registerPayloads(RegisterPayloadHandlersEvent event) {
        event.registrar("round-7").playToClient(RoundPacket.TYPE, RoundPacket.STREAM_CODEC,
                (pkt, ctx) -> ctx.enqueueWork(() -> ClientRounds.accept(
                        pkt.spawns(), pkt.ends(), pkt.pierces(), pkt.ricochets())));
    }
}
