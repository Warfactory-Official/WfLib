package com.wf.wflib.colony;

import com.wf.wflib.core.api.ServerTicks;
import com.wf.wflib.core.api.TickOrder;
import net.minecraft.resources.ResourceLocation;
import com.wf.wflib.colony.debug.SwarmBench;
import com.wf.wflib.colony.debug.BenchPlayer;
import com.wf.wflib.colony.glyphid.GlyphidSeparation;
import com.wf.wflib.colony.glyphid.nav.GlyphidBridges;
import com.wf.wflib.colony.glyphid.nav.GlyphidFlowFields;
import com.wf.wflib.colony.glyphid.sim.SimGlyphidTracking;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import com.wf.wflib.colony.debug.SwarmBenchCommand;
import com.wf.wflib.colony.api.ColonyContent;
import com.wf.wflib.colony.api.EntityGlyphid;
import com.wf.wflib.colony.api.SimGlyphidManager;
import com.wf.wflib.colony.client.ColonyClient;
import com.wf.wflib.colony.client.SimGlyphids;
import com.wf.wflib.colony.debug.GlyphidDeaths;
import com.wf.wflib.colony.glyphid.EntityGlyphidBomb;
import com.wf.wflib.colony.glyphid.GlyphidAcidMistEffect;
import com.wf.wflib.colony.glyphid.GlyphidRecon;
import com.wf.wflib.colony.glyphid.GlyphidTracker;
import com.wf.wflib.colony.glyphid.PreyTracker;
import com.wf.wflib.colony.glyphid.SimGlyphidBlast;
import com.wf.wflib.colony.glyphid.caste.EntityGlyphidBehemoth;
import com.wf.wflib.colony.glyphid.caste.EntityGlyphidBlaster;
import com.wf.wflib.colony.glyphid.caste.EntityGlyphidBombardier;
import com.wf.wflib.colony.glyphid.caste.EntityGlyphidBrawler;
import com.wf.wflib.colony.glyphid.caste.EntityGlyphidBrenda;
import com.wf.wflib.colony.glyphid.caste.EntityGlyphidDigger;
import com.wf.wflib.colony.glyphid.caste.EntityGlyphidNuclear;
import com.wf.wflib.colony.glyphid.caste.EntityGlyphidScout;
import com.wf.wflib.colony.api.GlyphidWaypoint;
import com.wf.wflib.core.api.WFSystem;
import com.wf.wflib.core.api.WFSystems;
import com.wf.wflib.colony.debug.EntityDebugDummy;
import com.wf.wflib.explosion.api.BlastDamageHooks;
import com.wf.wflib.mine.api.MineTarget;
import com.wf.wflib.mine.api.MineTrigger;
import com.wf.wflib.mine.api.MineTriggers;
import com.wf.wflib.mist.api.MistEffects;
import com.wf.wflib.mist.api.WFFluids;
import com.wf.wflib.recon.api.ReconTargets;
import com.wf.wflib.recon.api.SignatureProviders;
import com.wf.wflib.sim.api.SimKinds;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.EntityAttributeCreationEvent;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.Set;
import com.wf.wflib.core.api.WFLib;

/** Glyphid colonies: the off-world simulation, the castes that walk out of it, and the nests they leave behind. */
public final class ColonySystem implements WFSystem {
    private static final ResourceLocation GLYPHIDS = ResourceLocation.fromNamespaceAndPath(WFLib.MODID, "glyphids");

    private static volatile boolean active;

    /** Mixin guard: true once this system registered. */
    public static boolean active() {
        return active;
    }

    @Override
    public String id() {
        return "colony";
    }

    @Override
    public Set<String> requires() {
        return Set.of();
    }

    @Override
    public void register(IEventBus modBus, ModContainer container) {
        com.wf.wflib.core.api.CreativeTabs.contribute("colony", 700, out -> {
            out.accept(ColonyContent.GLYPHID_NEST_ITEM.get());
            out.accept(ColonyContent.GLYPHID_NEST_REINFORCED_ITEM.get());
            out.accept(ColonyContent.GLYPHID_SPAWNER_ITEM.get());
        });
        ServerTicks.LEVEL_POST.register(ResourceLocation.fromNamespaceAndPath("wflib", "colony"), TickOrder.COLONY, ColonyManager::tick);
        ServerTicks.LEVEL_POST.register(ResourceLocation.fromNamespaceAndPath("wflib", "colony_squads"), TickOrder.COLONY_SQUADS, GlyphidSquads::tick);
        ServerTicks.LEVEL_POST.register(ResourceLocation.fromNamespaceAndPath("wflib", "colony_tracking"), TickOrder.COLONY_TRACKING, SimGlyphidTracking::tick);
        ServerTicks.LEVEL_POST.register(ResourceLocation.fromNamespaceAndPath("wflib", "colony_separation"), TickOrder.COLONY_SEPARATION, GlyphidSeparation::tick);
        ServerTicks.LEVEL_POST.register(ResourceLocation.fromNamespaceAndPath("wflib", "colony_bridges"), TickOrder.COLONY_BRIDGES, GlyphidBridges::tick);
        ServerTicks.LEVEL_POST.register(ResourceLocation.fromNamespaceAndPath("wflib", "colony_flow"), TickOrder.COLONY_FLOW, GlyphidFlowFields::tick);
        ServerTicks.STOPPING.register(ResourceLocation.fromNamespaceAndPath("wflib", "colony"), TickOrder.COLONY_STOP, server -> ColonyEvents.shutdown());
        ServerTicks.STOPPING.register(ResourceLocation.fromNamespaceAndPath("wflib", "colony_nav"), TickOrder.COLONY_NAV_STOP, server -> {
            GlyphidBridges.clear();
            GlyphidFlowFields.clear();
        });
        ServerTicks.STOPPING.register(ResourceLocation.fromNamespaceAndPath("wflib", "colony_bench"), TickOrder.BENCH_STOP, server -> BenchPlayer.clear());
        NeoForge.EVENT_BUS.addListener(ServerTickEvent.Post.class, event -> SwarmBench.tick(event.getServer()));
        active = true;
        container.registerConfig(ModConfig.Type.COMMON, ColonyConfig.SPEC, "wflib-colony.toml");
        modBus.addListener(ColonyConfig::onLoad);

        DeferredRegister<EntityType<?>> entities = DeferredRegister.create(Registries.ENTITY_TYPE, WFLib.MODID);
        entities.register("glyphid", () -> EntityType.Builder.<EntityGlyphid>of(EntityGlyphid::new, MobCategory.MONSTER)
                .sized(1.4F, 1.0F).clientTrackingRange(8).updateInterval(1).build("glyphid"));
        entities.register("glyphid_scout", () -> caste(EntityGlyphidScout::new, 1.25F, 0.75F).build("glyphid_scout"));
        entities.register("glyphid_bombardier",
                () -> caste(EntityGlyphidBombardier::new, 1.75F, 1.0F).build("glyphid_bombardier"));
        entities.register("glyphid_blaster",
                () -> caste(EntityGlyphidBlaster::new, 2.0F, 1.125F).build("glyphid_blaster"));
        entities.register("glyphid_brawler",
                () -> caste(EntityGlyphidBrawler::new, 2.0F, 1.125F).build("glyphid_brawler"));
        entities.register("glyphid_digger",
                () -> caste(EntityGlyphidDigger::new, 1.75F, 1.0F).build("glyphid_digger"));
        entities.register("glyphid_behemoth",
                () -> caste(EntityGlyphidBehemoth::new, 2.5F, 1.5F).build("glyphid_behemoth"));
        // Fire-immune: it is a walking bomb, and burning one to death should still leave the fuse running.
        entities.register("glyphid_nuclear",
                () -> caste(EntityGlyphidNuclear::new, 2.5F, 1.75F).fireImmune().build("glyphid_nuclear"));
        entities.register("glyphid_brenda",
                () -> caste(EntityGlyphidBrenda::new, 2.5F, 1.75F).fireImmune().build("glyphid_brenda"));
        // Ordnance dropped by a glyphid on the wing: acid, or a blast for the heavier castes.
        entities.register("glyphid_bomb", () -> EntityType.Builder.<EntityGlyphidBomb>of(EntityGlyphidBomb::new,
                MobCategory.MISC).sized(0.4F, 0.4F).clientTrackingRange(8).updateInterval(2).build("glyphid_bomb"));
        entities.register("glyphid_waypoint", () -> EntityType.Builder.<GlyphidWaypoint>of(GlyphidWaypoint::new,
                MobCategory.MISC).fireImmune().sized(0.1F, 0.1F).clientTrackingRange(48)
                .updateInterval(Integer.MAX_VALUE).build("glyphid_waypoint"));
        entities.register("debug_dummy", () -> EntityType.Builder.<EntityDebugDummy>of(EntityDebugDummy::new,
                MobCategory.MISC).sized(0.6F, 1.8F).clientTrackingRange(8).updateInterval(Integer.MAX_VALUE)
                .build("debug_dummy"));
        entities.register(modBus);
        modBus.addListener(EntityAttributeCreationEvent.class, ColonySystem::registerAttributes);

        DeferredRegister<Block> blocks = DeferredRegister.create(Registries.BLOCK, WFLib.MODID);
        blocks.register("glyphid_nest", () -> new GlyphidNestBlock(BlockBehaviour.Properties.of()
                .mapColor(MapColor.COLOR_ORANGE).strength(0.5f).sound(SoundType.WOOL).noLootTable()));
        blocks.register("glyphid_nest_reinforced", () -> new GlyphidNestBlock(BlockBehaviour.Properties.of()
                .mapColor(MapColor.COLOR_BROWN).strength(2.5f, 30.0f).sound(SoundType.WOOL).noLootTable()));
        blocks.register("glyphid_spawner", () -> new GlyphidSpawnerBlock(BlockBehaviour.Properties.of()
                .mapColor(MapColor.COLOR_ORANGE).strength(0.5f).sound(SoundType.WOOL).noLootTable()));
        blocks.register(modBus);
        DeferredRegister<Item> items = DeferredRegister.create(Registries.ITEM, WFLib.MODID);
        items.register("glyphid_nest",
                () -> new BlockItem(ColonyContent.GLYPHID_NEST.get(), new Item.Properties()));
        items.register("glyphid_nest_reinforced",
                () -> new BlockItem(ColonyContent.GLYPHID_NEST_REINFORCED.get(), new Item.Properties()));
        items.register("glyphid_spawner",
                () -> new BlockItem(ColonyContent.GLYPHID_SPAWNER.get(), new Item.Properties()));
        items.register(modBus);
        DeferredRegister<BlockEntityType<?>> blockEntities =
                DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, WFLib.MODID);
        blockEntities.register("glyphid_spawner", () -> BlockEntityType.Builder.of(GlyphidSpawnerBlockEntity::new,
                ColonyContent.GLYPHID_SPAWNER.get()).build(null));
        blockEntities.register(modBus);

        modBus.addListener(RegisterPayloadHandlersEvent.class, event -> event.registrar("colony-1")
                .playToClient(SimGlyphidSyncPacket.TYPE, SimGlyphidSyncPacket.STREAM_CODEC,
                        (pkt, ctx) -> ctx.enqueueWork(() -> SimGlyphids.accept(pkt))));

        SimKinds.register(SimGlyphidManager.KIND);
        ReconTargets.SOURCES.register(ResourceLocation.fromNamespaceAndPath(WFLib.MODID, "glyphid"), 0,
                new GlyphidRecon.Loaded());
        ReconTargets.SOURCES.register(ResourceLocation.fromNamespaceAndPath(WFLib.MODID, "sim_glyphid"), 0,
                new GlyphidRecon.Simulated());
        SignatureProviders.PROVIDERS.register(ResourceLocation.fromNamespaceAndPath(WFLib.MODID, "glyphid"), 0,
                new GlyphidRecon.Signatures());
        BlastDamageHooks.HOOKS.register(ResourceLocation.fromNamespaceAndPath(WFLib.MODID, "sim_glyphid"), 0,
                SimGlyphidBlast::damage);
        MistEffects.register(WFFluids.GLYPHID_ACID.getId(), new GlyphidAcidMistEffect());
        if (WFSystems.enabled("mine")) {
            MineTriggers.register(GLYPHIDS, MineTrigger.standard(MineTarget.ofClass(EntityGlyphid.class)));
        }

        NeoForge.EVENT_BUS.register(ColonyEvents.class);
        NeoForge.EVENT_BUS.register(GlyphidTracker.class);
        NeoForge.EVENT_BUS.register(PreyTracker.class);
        NeoForge.EVENT_BUS.register(GlyphidDeaths.class);
        NeoForge.EVENT_BUS.addListener(RegisterCommandsEvent.class, ColonyCommands::onRegisterCommands);
        NeoForge.EVENT_BUS.addListener(RegisterCommandsEvent.class, SwarmBenchCommand::onRegisterCommands);
    }

    @Override
    public void registerClient(IEventBus modBus) {
        ColonyClient.register(modBus);
    }

    private static <T extends EntityGlyphid> EntityType.Builder<T> caste(EntityType.EntityFactory<T> factory,
                                                                        float width, float height) {
        return EntityType.Builder.of(factory, MobCategory.MONSTER)
                .sized(width, height)
                .clientTrackingRange(8)
                .updateInterval(1);
    }

    /** Living entities need an attribute supplier registered before anything can spawn one. */
    private static void registerAttributes(EntityAttributeCreationEvent event) {
        event.put(ColonyContent.GLYPHID.get(), EntityGlyphid.createAttributes().build());
        event.put(ColonyContent.GLYPHID_SCOUT.get(), EntityGlyphidScout.createAttributes().build());
        event.put(ColonyContent.GLYPHID_BOMBARDIER.get(), EntityGlyphidBombardier.createAttributes().build());
        event.put(ColonyContent.GLYPHID_BLASTER.get(), EntityGlyphidBlaster.createAttributes().build());
        event.put(ColonyContent.GLYPHID_BRAWLER.get(), EntityGlyphidBrawler.createAttributes().build());
        event.put(ColonyContent.GLYPHID_DIGGER.get(), EntityGlyphidDigger.createAttributes().build());
        event.put(ColonyContent.GLYPHID_BEHEMOTH.get(), EntityGlyphidBehemoth.createAttributes().build());
        event.put(ColonyContent.GLYPHID_NUCLEAR.get(), EntityGlyphidNuclear.createAttributes().build());
        event.put(ColonyContent.GLYPHID_BRENDA.get(), EntityGlyphidBrenda.createAttributes().build());
        event.put(ColonyContent.DEBUG_DUMMY.get(), EntityDebugDummy.createAttributes().build());
    }
}
