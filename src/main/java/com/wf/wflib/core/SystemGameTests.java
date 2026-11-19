package com.wf.wflib.core;

import com.wf.wflib.ammo.gametest.AmmoGameTest;
import com.wf.wflib.anomaly.gametest.CaveFieldGameTest;
import com.wf.wflib.anomaly.gametest.RebounderGameTest;
import com.wf.wflib.anomaly.gametest.SiteLayoutGameTest;
import com.wf.wflib.armor.gametest.ArmorGameTest;
import com.wf.wflib.armor.gametest.ArmorLabGameTest;
import com.wf.wflib.armor.gametest.DressGameTest;
import com.wf.wflib.armor.gametest.ExoGameTest;
import com.wf.wflib.artifact.gametest.ArtifactGameTest;
import com.wf.wflib.artifact.gametest.BoltGameTest;
import com.wf.wflib.carry.gametest.CarryGameTest;
import com.wf.wflib.compat.gametest.ItemMassGameTest;
import com.wf.wflib.compat.gametest.WeightLabGameTest;
import com.wf.wflib.core.api.WFSystems;
import com.wf.wflib.drone.gametest.DroneGameTest;
import com.wf.wflib.drone.gametest.PdaDronesGameTest;
import com.wf.wflib.drone.gametest.StrappedExplosiveGameTest;
import com.wf.wflib.fire.gametest.FireVisualsGameTest;
import com.wf.wflib.grenade.gametest.GrenadeGameTest;
import com.wf.wflib.labs.LabServeGameTest;
import com.wf.wflib.magazine.gametest.MagazineGameTest;
import com.wf.wflib.map.gametest.PdaMarkerGameTest;
import com.wf.wflib.mine.gametest.NavalMineGameTest;
import com.wf.wflib.missile.gametest.AirLaunchGameTest;
import com.wf.wflib.missile.gametest.DirectStageGameTest;
import com.wf.wflib.missile.gametest.DownedGameTest;
import com.wf.wflib.missile.gametest.GlideGameTest;
import com.wf.wflib.missile.gametest.MissileLabGameTest;
import com.wf.wflib.missile.gametest.SeekerDecoyGameTest;
import com.wf.wflib.missile.gametest.TorpedoGameTest;
import com.wf.wflib.missile.gametest.TvGuidanceGameTest;
import com.wf.wflib.nvg.gametest.NvgGameTest;
import com.wf.wflib.orbital.gametest.PdaSatellitesGameTest;
import com.wf.wflib.packs.gametest.PacksGameTest;
import com.wf.wflib.pda.gametest.PdaCarriedGameTest;
import com.wf.wflib.pda.gametest.PdaTerminalGameTest;
import com.wf.wflib.pda.gametest.PdaUplinkGameTest;
import com.wf.wflib.physics.gametest.DeckGameTest;
import com.wf.wflib.physics.gametest.PhysicsGameTest;
import com.wf.wflib.physics.gametest.StepGameTest;
import com.wf.wflib.radiation.RadiationFieldGameTest;
import com.wf.wflib.radiation.RadiationSimGameTest;
import com.wf.wflib.radiation.gametest.HazardGameTest;
import com.wf.wflib.radiation.gametest.NeutronActivationGameTest;
import com.wf.wflib.ragdoll.gametest.RagdollGameTest;
import com.wf.wflib.recon.gametest.PdaTelemetryGameTest;
import com.wf.wflib.round.api.RoundSweepBench;
import com.wf.wflib.round.gametest.BlockDamageGameTest;
import com.wf.wflib.round.gametest.BlockPenGameTest;
import com.wf.wflib.round.gametest.ImpactEffectGameTest;
import com.wf.wflib.round.gametest.PenTableGameTest;
import com.wf.wflib.round.gametest.RicochetGameTest;
import com.wf.wflib.round.gametest.RocketGameTest;
import com.wf.wflib.round.gametest.RoundGameTest;
import com.wf.wflib.round.gametest.RoundHitGameTest;
import com.wf.wflib.round.gametest.RoundLabGameTest;
import com.wf.wflib.round.gametest.RoundThroughGameTest;
import com.wf.wflib.round.gametest.VirtualFlightBench;
import com.wf.wflib.round.gametest.VirtualFlightGameTest;
import com.wf.wflib.sim.gametest.SimRegistryGameTest;
import com.wf.wflib.soldier.DeathGameTest;
import com.wf.wflib.soldier.ForceGameTest;
import com.wf.wflib.soldier.InvestigateGameTest;
import com.wf.wflib.soldier.LightGameTest;
import com.wf.wflib.soldier.VoiceGameTest;
import com.wf.wflib.testkit.gametest.ForcedChunksGameTest;
import com.wf.wflib.warhead.gametest.AgentOrangeGameTest;
import com.wf.wflib.warhead.gametest.BlastAhfGameTest;
import com.wf.wflib.warhead.gametest.BlastTargetGameTest;
import com.wf.wflib.warhead.gametest.ClusterSlopeGameTest;
import com.wf.wflib.warhead.gametest.ShapedChargeGameTest;
import com.wf.wflib.warhead.gametest.ThermobaricGameTest;
import net.neoforged.neoforge.event.RegisterGameTestsEvent;

import java.util.List;
import java.util.Map;
import net.neoforged.fml.ModList;

/** Gametest classes of a system register only while it is on; no {@code @GameTestHolder}, which NeoForge scans regardless. */
public final class SystemGameTests {
    private static final Map<String, List<Class<?>>> BY_SYSTEM = Map.ofEntries(
            Map.entry("labs", List.of(LabServeGameTest.class)),
            Map.entry("sim", List.of(SimRegistryGameTest.class)),
            Map.entry("physics", List.of(PhysicsGameTest.class,
                    StepGameTest.class,
                    DeckGameTest.class)),
            Map.entry("round", List.of(
                    RoundGameTest.class,
                    BlockDamageGameTest.class,
                    BlockPenGameTest.class,
                    PenTableGameTest.class,
                    VirtualFlightBench.class,
                    VirtualFlightGameTest.class,
                    ImpactEffectGameTest.class,
                    RicochetGameTest.class,
                    RocketGameTest.class,
                    RoundHitGameTest.class,
                    RoundThroughGameTest.class,
                    RoundLabGameTest.class,
                    RoundSweepBench.class)),
            Map.entry("missile", List.of(
                    SeekerDecoyGameTest.class,
                    DownedGameTest.class,
                    DirectStageGameTest.class,
                    AirLaunchGameTest.class,
                    TorpedoGameTest.class,
                    TvGuidanceGameTest.class,
                    GlideGameTest.class,
                    MissileLabGameTest.class)),
            Map.entry("warhead", List.of(
                    AgentOrangeGameTest.class,
                    ThermobaricGameTest.class,
                    ShapedChargeGameTest.class,
                    BlastTargetGameTest.class,
                    ClusterSlopeGameTest.class,
                    BlastAhfGameTest.class)),
            Map.entry("grenade", List.of(GrenadeGameTest.class)),
            Map.entry("fire", List.of(FireVisualsGameTest.class)),
            Map.entry("ammo", List.of(AmmoGameTest.class)),
            Map.entry("magazine", List.of(MagazineGameTest.class)),
            Map.entry("pda", List.of(
                    PdaCarriedGameTest.class,
                    PdaTerminalGameTest.class,
                    PdaUplinkGameTest.class)),
            Map.entry("drone", List.of(
                    DroneGameTest.class,
                    PdaDronesGameTest.class,
                    StrappedExplosiveGameTest.class)),
            Map.entry("recon", List.of(PdaTelemetryGameTest.class)),
            Map.entry("nvg", List.of(NvgGameTest.class)),
            Map.entry("map", List.of(PdaMarkerGameTest.class)),
            Map.entry("orbital", List.of(PdaSatellitesGameTest.class)),
            Map.entry("anomaly", List.of(
                    CaveFieldGameTest.class,
                    RebounderGameTest.class,
                    SiteLayoutGameTest.class,
                    BoltGameTest.class)),
            Map.entry("artifact", List.of(ArtifactGameTest.class)),
            Map.entry("mine", List.of(NavalMineGameTest.class)),
            Map.entry("compat", List.of(ItemMassGameTest.class)),
            Map.entry("armor", List.of(ArmorGameTest.class,
                    ExoGameTest.class,
                    DressGameTest.class,
                    ArmorLabGameTest.class)),
            Map.entry("soldier", List.of(VoiceGameTest.class, LightGameTest.class,
                    InvestigateGameTest.class, DeathGameTest.class,
                    ForceGameTest.class)),
            Map.entry("packs", List.of(PacksGameTest.class)),
            Map.entry("carry", List.of(CarryGameTest.class)),
            Map.entry("ragdoll", List.of(RagdollGameTest.class)),
            Map.entry("radiation", List.of(
                    HazardGameTest.class,
                    NeutronActivationGameTest.class,
                    RadiationFieldGameTest.class,
                    RadiationSimGameTest.class)));

    private SystemGameTests() {}

    public static void register(RegisterGameTestsEvent event) {
        event.register(ForcedChunksGameTest.class);
        if (ModList.get().isLoaded("wfweight") && WFSystems.enabled("labs") && WFSystems.enabled("armor")) {
            event.register(WeightLabGameTest.class);
        }
        BY_SYSTEM.forEach((system, classes) -> {
            if (WFSystems.enabled(system)) {
                classes.forEach(event::register);
            }
        });
    }
}
