package com.wf.wflib.core;

import com.mojang.serialization.MapCodec;
import com.wf.wflib.ammo.AmmoSystem;
import com.wf.wflib.anomaly.AnomalySystem;
import com.wf.wflib.anomaly.gametest.RebounderGameTest;
import com.wf.wflib.armor.ArmorSystem;
import com.wf.wflib.artifact.ArtifactSystem;
import com.wf.wflib.audio.AudioSystem;
import com.wf.wflib.backpack.BackpackSystem;
import com.wf.wflib.barrel.BarrelSystem;
import com.wf.wflib.build.BuildSystem;
import com.wf.wflib.cam.CamSystem;
import com.wf.wflib.carry.CarrySystem;
import com.wf.wflib.colony.ColonySystem;
import com.wf.wflib.compat.CompatSystem;
import com.wf.wflib.core.api.Hook;
import com.wf.wflib.core.api.ServerTicks;
import com.wf.wflib.core.api.TickOrder;
import com.wf.wflib.core.api.ViewRange;
import com.wf.wflib.core.api.WFLibContent;
import com.wf.wflib.core.api.WFRegistry;
import com.wf.wflib.core.api.WFSystem;
import com.wf.wflib.core.api.WFSystems;
import com.wf.wflib.core.api.WorkerPool;
import com.wf.wflib.corpse.CorpseSystem;
import com.wf.wflib.countermeasure.CountermeasureSystem;
import com.wf.wflib.damage.DamageSystem;
import com.wf.wflib.demolition.DemolitionSystem;
import com.wf.wflib.door.DoorSystem;
import com.wf.wflib.drone.DroneSystem;
import com.wf.wflib.explosion.ExplosionSystem;
import com.wf.wflib.fire.FireSystem;
import com.wf.wflib.fx.FxSystem;
import com.wf.wflib.grenade.GrenadeSystem;
import com.wf.wflib.grenade.gametest.GrenadeGameTest;
import com.wf.wflib.hazard.HazardSystem;
import com.wf.wflib.industry.IndustrySystem;
import com.wf.wflib.labs.LabsSystem;
import com.wf.wflib.laser.LaserSystem;
import com.wf.wflib.magazine.MagazineSystem;
import com.wf.wflib.map.MapSystem;
import com.wf.wflib.mine.MineSystem;
import com.wf.wflib.missile.MissileSystem;
import com.wf.wflib.mist.MistSystem;
import com.wf.wflib.nvg.NvgSystem;
import com.wf.wflib.orbital.OrbitalSystem;
import com.wf.wflib.packs.PacksSystem;
import com.wf.wflib.packs.gametest.PacksGameTest;
import com.wf.wflib.particle.ParticleSystem;
import com.wf.wflib.pda.PdaSystem;
import com.wf.wflib.pda.gametest.PdaUplinkGameTest;
import com.wf.wflib.photo.PhotoSystem;
import com.wf.wflib.physics.PhysicsSystem;
import com.wf.wflib.probe.ProbeSystem;
import com.wf.wflib.radiation.RadiationSystem;
import com.wf.wflib.radio.RadioSystem;
import com.wf.wflib.ragdoll.RagdollSystem;
import com.wf.wflib.rail.RailSystem;
import com.wf.wflib.recon.ReconSystem;
import com.wf.wflib.round.RoundSystem;
import com.wf.wflib.round.gametest.BlockDamageGameTest;
import com.wf.wflib.round.gametest.BlockPenGameTest;
import com.wf.wflib.round.gametest.ImpactEffectGameTest;
import com.wf.wflib.round.gametest.PenTableGameTest;
import com.wf.wflib.round.gametest.RicochetGameTest;
import com.wf.wflib.round.gametest.RocketGameTest;
import com.wf.wflib.round.gametest.RoundGameTest;
import com.wf.wflib.round.gametest.RoundHitGameTest;
import com.wf.wflib.round.gametest.RoundThroughGameTest;
import com.wf.wflib.round.gametest.VirtualFlightBench;
import com.wf.wflib.round.gametest.VirtualFlightGameTest;
import com.wf.wflib.sight.SightSystem;
import com.wf.wflib.sim.SimSystem;
import com.wf.wflib.skin.SkinSystem;
import com.wf.wflib.smoking.SmokingSystem;
import com.wf.wflib.soldier.SoldierSystem;
import com.wf.wflib.stream.StreamSystem;
import com.wf.wflib.suppression.SuppressionSystem;
import com.wf.wflib.sway.SwaySystem;
import com.wf.wflib.ui.UiSystem;
import com.wf.wflib.view.ViewSystem;
import com.wf.wflib.warhead.WarheadSystem;
import com.wf.wflib.wiki.WikiSystem;
import com.wf.wflib.work.WorkSystem;
import com.wf.wflib.xray.XraySystem;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.fml.loading.FMLLoader;
import net.neoforged.neoforge.registries.RegisterEvent;

import java.util.List;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.fml.event.lifecycle.FMLLoadCompleteEvent;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.common.conditions.ICondition;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

@Mod(WFLibMod.MODID)
public class WFLibMod {
    public static final String MODID = "wflib";

    static final List<WFSystem> SYSTEMS = List.of(
            new SimSystem(),
            new SoldierSystem(),
            new CompatSystem(),
            new FxSystem(),
            new ReconSystem(),
            new DamageSystem(),
            new WarheadSystem(),
            new ExplosionSystem(),
            new FireSystem(),
            new MistSystem(),
            new MissileSystem(),
            new RoundSystem(),
            new AmmoSystem(),
            new MagazineSystem(),
            new BarrelSystem(),
            new CountermeasureSystem(),
            new LaserSystem(),
            new GrenadeSystem(),
            new UiSystem(),
            new PdaSystem(),
            new MapSystem(),
            new SightSystem(),
            new ViewSystem(),
            new ParticleSystem(),
            new AudioSystem(),
            new RadioSystem(),
            new CamSystem(),
            new StreamSystem(),
            new DroneSystem(),
            new ProbeSystem(),
            new NvgSystem(),
            new XraySystem(),
            new ArmorSystem(),
            new BackpackSystem(),
            new SkinSystem(),
            new SmokingSystem(),
            new CarrySystem(),
            new RagdollSystem(),
            new CorpseSystem(),
            new HazardSystem(),
            new RadiationSystem(),
            new WorkSystem(),
            new IndustrySystem(),
            new BuildSystem(),
            new DemolitionSystem(),
            new MineSystem(),
            new DoorSystem(),
            new ArtifactSystem(),
            new AnomalySystem(),
            new ColonySystem(),
            new OrbitalSystem(),
            new RailSystem(),
            new WikiSystem(),
            new SwaySystem(),
            new PhysicsSystem(),
            new SuppressionSystem(),
            new PacksSystem(),
            new LabsSystem(),
            new PhotoSystem());

    public WFLibMod(IEventBus modEventBus, ModContainer modContainer) {
        NeoForge.EVENT_BUS.register(ServerTicks.Listeners.class);
        ServerTicks.STARTING.register(
                ResourceLocation.fromNamespaceAndPath(MODID, "workers"),
                TickOrder.WORKERS_START, server -> WorkerPool.startup());
        ServerTicks.STOPPING.register(
                ResourceLocation.fromNamespaceAndPath(MODID, "workers"),
                TickOrder.WORKERS_STOP, server -> WorkerPool.shutdown());
        WFSystems.define(MODID, modEventBus, modContainer, SYSTEMS);
        if (LabsSystem.headlessRequested() && !FMLEnvironment.production) {
            WFLibContent.everything(MODID + " labs");
        }
        modEventBus.addListener(SystemsNetwork::registerPayloads);
        modEventBus.addListener(SystemsNetwork::registerTasks);
        DeferredRegister<MapCodec<? extends ICondition>> conditions =
                DeferredRegister.create(
                        NeoForgeRegistries.Keys.CONDITION_CODECS, MODID);
        conditions.register("system", () -> SystemCondition.CODEC);
        conditions.register(modEventBus);

        WFCreativeTabs.register(modEventBus);

        if (List.of(System.getProperty("neoforge.enabledGameTestNamespaces", "").split(",")).contains(MODID)
                || FMLLoader.launcherHandlerName().startsWith("forgejunit")) {
            WFSystems.require(MODID, "anomaly");
            WFLibContent.everything(MODID + " tests");
            RebounderGameTest.register();
            modEventBus.addListener(SystemGameTests::register);
            if (WFSystems.enabled("round")) {
                RoundGameTest.register();
                BlockDamageGameTest.register();
                BlockPenGameTest.register();
                PenTableGameTest.register();
                VirtualFlightBench.register();
                ImpactEffectGameTest.register();
                RicochetGameTest.register();
                RocketGameTest.register();
                RoundHitGameTest.register();
                RoundThroughGameTest.register();
                VirtualFlightGameTest.register();
            }
            if (WFSystems.enabled("packs")) {
                PacksGameTest.register();
            }
            PdaUplinkGameTest.register();
            if (WFSystems.enabled("grenade")) {
                GrenadeGameTest.register();
            }
        }
        modEventBus.addListener(EventPriority.HIGHEST, RegisterEvent.class, e -> {
            WFSystems.close();
            WFLibContent.freeze();
        });
        modEventBus.addListener(FMLLoadCompleteEvent.class,
                e -> e.enqueueWork(WFLibContent::loadComplete));
        modEventBus.addListener(FMLLoadCompleteEvent.class,
                e -> e.enqueueWork(() -> {
                    WFRegistry.freezeAll();
                    Hook.freezeAll();
                }));
        modEventBus.addListener(ViewRange::onLoad);
        modContainer.registerConfig(ModConfig.Type.COMMON, ViewRange.SPEC, "wflib-core.toml");
    }
}
