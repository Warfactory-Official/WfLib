package com.wf.wflib.core;

import com.wf.wflib.core.api.WFLibContent;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.fml.loading.FMLLoader;
import net.neoforged.neoforge.registries.RegisterEvent;

import java.util.List;

@Mod(WFLibMod.MODID)
public class WFLibMod {
    public static final String MODID = "wflib";

    static final List<com.wf.wflib.core.api.WFSystem> SYSTEMS = List.of(
            new com.wf.wflib.sim.SimSystem(),
            new com.wf.wflib.compat.CompatSystem(),
            new com.wf.wflib.fx.FxSystem(),
            new com.wf.wflib.recon.ReconSystem(),
            new com.wf.wflib.damage.DamageSystem(),
            new com.wf.wflib.warhead.WarheadSystem(),
            new com.wf.wflib.explosion.ExplosionSystem(),
            new com.wf.wflib.fire.FireSystem(),
            new com.wf.wflib.mist.MistSystem(),
            new com.wf.wflib.missile.MissileSystem(),
            new com.wf.wflib.round.RoundSystem(),
            new com.wf.wflib.ammo.AmmoSystem(),
            new com.wf.wflib.magazine.MagazineSystem(),
            new com.wf.wflib.barrel.BarrelSystem(),
            new com.wf.wflib.countermeasure.CountermeasureSystem(),
            new com.wf.wflib.laser.LaserSystem(),
            new com.wf.wflib.grenade.GrenadeSystem(),
            new com.wf.wflib.ui.UiSystem(),
            new com.wf.wflib.pda.PdaSystem(),
            new com.wf.wflib.map.MapSystem(),
            new com.wf.wflib.sight.SightSystem(),
            new com.wf.wflib.view.ViewSystem(),
            new com.wf.wflib.particle.ParticleSystem(),
            new com.wf.wflib.audio.AudioSystem(),
            new com.wf.wflib.radio.RadioSystem(),
            new com.wf.wflib.cam.CamSystem(),
            new com.wf.wflib.stream.StreamSystem(),
            new com.wf.wflib.drone.DroneSystem(),
            new com.wf.wflib.probe.ProbeSystem(),
            new com.wf.wflib.nvg.NvgSystem(),
            new com.wf.wflib.xray.XraySystem(),
            new com.wf.wflib.armor.ArmorSystem(),
            new com.wf.wflib.backpack.BackpackSystem(),
            new com.wf.wflib.skin.SkinSystem(),
            new com.wf.wflib.smoking.SmokingSystem(),
            new com.wf.wflib.carry.CarrySystem(),
            new com.wf.wflib.hazard.HazardSystem(),
            new com.wf.wflib.radiation.RadiationSystem(),
            new com.wf.wflib.work.WorkSystem(),
            new com.wf.wflib.industry.IndustrySystem(),
            new com.wf.wflib.build.BuildSystem(),
            new com.wf.wflib.demolition.DemolitionSystem(),
            new com.wf.wflib.mine.MineSystem(),
            new com.wf.wflib.door.DoorSystem(),
            new com.wf.wflib.artifact.ArtifactSystem(),
            new com.wf.wflib.anomaly.AnomalySystem(),
            new com.wf.wflib.colony.ColonySystem(),
            new com.wf.wflib.orbital.OrbitalSystem(),
            new com.wf.wflib.rail.RailSystem(),
            new com.wf.wflib.wiki.WikiSystem(),
            new com.wf.wflib.sway.SwaySystem(),
            new com.wf.wflib.physics.PhysicsSystem(),
            new com.wf.wflib.suppression.SuppressionSystem(),
            new com.wf.wflib.packs.PacksSystem());

    public WFLibMod(IEventBus modEventBus, ModContainer modContainer) {
        net.neoforged.neoforge.common.NeoForge.EVENT_BUS.register(com.wf.wflib.core.api.ServerTicks.Listeners.class);
        com.wf.wflib.core.api.ServerTicks.STARTING.register(
                net.minecraft.resources.ResourceLocation.fromNamespaceAndPath(MODID, "workers"),
                com.wf.wflib.core.api.TickOrder.WORKERS_START, server -> com.wf.wflib.core.api.WorkerPool.startup());
        com.wf.wflib.core.api.ServerTicks.STOPPING.register(
                net.minecraft.resources.ResourceLocation.fromNamespaceAndPath(MODID, "workers"),
                com.wf.wflib.core.api.TickOrder.WORKERS_STOP, server -> com.wf.wflib.core.api.WorkerPool.shutdown());
        com.wf.wflib.core.api.WFSystems.define(MODID, modEventBus, modContainer, SYSTEMS);
        modEventBus.addListener(com.wf.wflib.core.SystemsNetwork::registerPayloads);
        modEventBus.addListener(com.wf.wflib.core.SystemsNetwork::registerTasks);
        net.neoforged.neoforge.registries.DeferredRegister<com.mojang.serialization.MapCodec<? extends net.neoforged.neoforge.common.conditions.ICondition>> conditions =
                net.neoforged.neoforge.registries.DeferredRegister.create(
                        net.neoforged.neoforge.registries.NeoForgeRegistries.Keys.CONDITION_CODECS, MODID);
        conditions.register("system", () -> com.wf.wflib.core.SystemCondition.CODEC);
        conditions.register(modEventBus);

        com.wf.wflib.core.WFCreativeTabs.register(modEventBus);

        if (List.of(System.getProperty("neoforge.enabledGameTestNamespaces", "").split(",")).contains(MODID)
                || FMLLoader.launcherHandlerName().startsWith("forgejunit")) {
            com.wf.wflib.core.api.WFSystems.require(MODID, "anomaly");
            WFLibContent.everything(MODID + " tests");
            com.wf.wflib.anomaly.gametest.RebounderGameTest.register();
            modEventBus.addListener(com.wf.wflib.core.SystemGameTests::register);
            if (com.wf.wflib.core.api.WFSystems.enabled("round")) {
                com.wf.wflib.round.gametest.RoundGameTest.register();
                com.wf.wflib.round.gametest.BlockDamageGameTest.register();
                com.wf.wflib.round.gametest.BlockPenGameTest.register();
                com.wf.wflib.round.gametest.PenTableGameTest.register();
                com.wf.wflib.round.gametest.VirtualFlightBench.register();
                com.wf.wflib.round.gametest.ImpactEffectGameTest.register();
                com.wf.wflib.round.gametest.RicochetGameTest.register();
                com.wf.wflib.round.gametest.RocketGameTest.register();
                com.wf.wflib.round.gametest.RoundHitGameTest.register();
                com.wf.wflib.round.gametest.VirtualFlightGameTest.register();
            }
            if (com.wf.wflib.core.api.WFSystems.enabled("packs")) {
                com.wf.wflib.packs.gametest.PacksGameTest.register();
            }
            com.wf.wflib.pda.gametest.PdaUplinkGameTest.register();
            if (com.wf.wflib.core.api.WFSystems.enabled("grenade")) {
                com.wf.wflib.grenade.gametest.GrenadeGameTest.register();
            }
        }
        modEventBus.addListener(EventPriority.HIGHEST, RegisterEvent.class, e -> {
            com.wf.wflib.core.api.WFSystems.close();
            WFLibContent.freeze();
        });
        modEventBus.addListener(net.neoforged.fml.event.lifecycle.FMLLoadCompleteEvent.class,
                e -> e.enqueueWork(WFLibContent::loadComplete));
        modEventBus.addListener(net.neoforged.fml.event.lifecycle.FMLLoadCompleteEvent.class,
                e -> e.enqueueWork(() -> {
                    com.wf.wflib.core.api.WFRegistry.freezeAll();
                    com.wf.wflib.core.api.Hook.freezeAll();
                }));
        modEventBus.addListener(com.wf.wflib.core.api.ViewRange::onLoad);
        modContainer.registerConfig(ModConfig.Type.COMMON, com.wf.wflib.core.api.ViewRange.SPEC, "wflib-core.toml");
    }
}
