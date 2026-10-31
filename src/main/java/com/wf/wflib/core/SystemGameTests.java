package com.wf.wflib.core;

import com.wf.wflib.core.api.WFSystems;
import net.neoforged.neoforge.event.RegisterGameTestsEvent;

import java.util.List;
import java.util.Map;

/** Gametest classes of a system register only while it is on; no {@code @GameTestHolder}, which NeoForge scans regardless. */
public final class SystemGameTests {
    private static final Map<String, List<Class<?>>> BY_SYSTEM = Map.ofEntries(
            Map.entry("sim", List.of(com.wf.wflib.sim.gametest.SimRegistryGameTest.class)),
            Map.entry("physics", List.of(com.wf.wflib.physics.gametest.PhysicsGameTest.class,
                    com.wf.wflib.physics.gametest.StepGameTest.class,
                    com.wf.wflib.physics.gametest.DeckGameTest.class)),
            Map.entry("round", List.of(
                    com.wf.wflib.round.gametest.RoundGameTest.class,
                    com.wf.wflib.round.gametest.BlockDamageGameTest.class,
                    com.wf.wflib.round.gametest.BlockPenGameTest.class,
                    com.wf.wflib.round.gametest.PenTableGameTest.class,
                    com.wf.wflib.round.gametest.VirtualFlightBench.class,
                    com.wf.wflib.round.gametest.VirtualFlightGameTest.class,
                    com.wf.wflib.round.gametest.ImpactEffectGameTest.class,
                    com.wf.wflib.round.gametest.RicochetGameTest.class,
                    com.wf.wflib.round.gametest.RocketGameTest.class,
                    com.wf.wflib.round.gametest.RoundHitGameTest.class,
                    com.wf.wflib.round.api.RoundSweepBench.class)),
            Map.entry("missile", List.of(
                    com.wf.wflib.missile.gametest.SeekerDecoyGameTest.class,
                    com.wf.wflib.missile.gametest.DownedGameTest.class,
                    com.wf.wflib.missile.gametest.DirectStageGameTest.class,
                    com.wf.wflib.missile.gametest.AirLaunchGameTest.class,
                    com.wf.wflib.missile.gametest.TorpedoGameTest.class,
                    com.wf.wflib.missile.gametest.TvGuidanceGameTest.class,
                    com.wf.wflib.missile.gametest.GlideGameTest.class)),
            Map.entry("warhead", List.of(
                    com.wf.wflib.warhead.gametest.AgentOrangeGameTest.class,
                    com.wf.wflib.warhead.gametest.ThermobaricGameTest.class,
                    com.wf.wflib.warhead.gametest.ShapedChargeGameTest.class,
                    com.wf.wflib.warhead.gametest.ClusterSlopeGameTest.class,
                    com.wf.wflib.warhead.gametest.BlastAhfGameTest.class)),
            Map.entry("grenade", List.of(com.wf.wflib.grenade.gametest.GrenadeGameTest.class)),
            Map.entry("fire", List.of(com.wf.wflib.fire.gametest.FireVisualsGameTest.class)),
            Map.entry("ammo", List.of(com.wf.wflib.ammo.gametest.AmmoGameTest.class)),
            Map.entry("magazine", List.of(com.wf.wflib.magazine.gametest.MagazineGameTest.class)),
            Map.entry("pda", List.of(
                    com.wf.wflib.pda.gametest.PdaCarriedGameTest.class,
                    com.wf.wflib.pda.gametest.PdaTerminalGameTest.class,
                    com.wf.wflib.pda.gametest.PdaUplinkGameTest.class)),
            Map.entry("drone", List.of(
                    com.wf.wflib.drone.gametest.DroneGameTest.class,
                    com.wf.wflib.drone.gametest.PdaDronesGameTest.class,
                    com.wf.wflib.drone.gametest.StrappedExplosiveGameTest.class)),
            Map.entry("recon", List.of(com.wf.wflib.recon.gametest.PdaTelemetryGameTest.class)),
            Map.entry("nvg", List.of(com.wf.wflib.nvg.gametest.NvgGameTest.class)),
            Map.entry("map", List.of(com.wf.wflib.map.gametest.PdaMarkerGameTest.class)),
            Map.entry("orbital", List.of(com.wf.wflib.orbital.gametest.PdaSatellitesGameTest.class)),
            Map.entry("anomaly", List.of(
                    com.wf.wflib.anomaly.gametest.CaveFieldGameTest.class,
                    com.wf.wflib.anomaly.gametest.RebounderGameTest.class,
                    com.wf.wflib.anomaly.gametest.SiteLayoutGameTest.class,
                    com.wf.wflib.artifact.gametest.BoltGameTest.class)),
            Map.entry("artifact", List.of(com.wf.wflib.artifact.gametest.ArtifactGameTest.class)),
            Map.entry("mine", List.of(com.wf.wflib.mine.gametest.NavalMineGameTest.class)),
            Map.entry("compat", List.of(com.wf.wflib.compat.gametest.ItemMassGameTest.class)),
            Map.entry("armor", List.of(com.wf.wflib.armor.gametest.ArmorGameTest.class)),
            Map.entry("packs", List.of(com.wf.wflib.packs.gametest.PacksGameTest.class)),
            Map.entry("carry", List.of(com.wf.wflib.carry.gametest.CarryGameTest.class)),
            Map.entry("radiation", List.of(
                    com.wf.wflib.radiation.gametest.HazardGameTest.class,
                    com.wf.wflib.radiation.gametest.NeutronActivationGameTest.class,
                    com.wf.wflib.radiation.RadiationFieldGameTest.class,
                    com.wf.wflib.radiation.RadiationSimGameTest.class)));

    private SystemGameTests() {}

    public static void register(RegisterGameTestsEvent event) {
        event.register(com.wf.wflib.testkit.gametest.ForcedChunksGameTest.class);
        BY_SYSTEM.forEach((system, classes) -> {
            if (WFSystems.enabled(system)) {
                classes.forEach(event::register);
            }
        });
    }
}
