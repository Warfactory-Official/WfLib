package com.wf.wflib.missile;

import com.wf.wflib.core.api.WFRegistry;
import com.wf.wflib.damage.api.ThreatKind;
import com.wf.wflib.cam.api.CameraSpec;
import com.wf.wflib.missile.api.FlightStages;
import com.wf.wflib.missile.api.MissileConfig;
import com.wf.wflib.missile.api.MissileEntity;
import com.wf.wflib.missile.api.MissileModels;
import com.wf.wflib.missile.api.MissilePreset;
import com.wf.wflib.missile.api.MissilePresets;
import com.wf.wflib.missile.api.MissileSimConfig;
import com.wf.wflib.missile.api.SeekerMode;
import com.wf.wflib.round.api.VehicleOrdnance;
import com.wf.wflib.warhead.api.AgentOrangeWarhead;
import com.wf.wflib.warhead.api.FireWarhead;
import com.wf.wflib.warhead.api.GasWarhead;
import com.wf.wflib.warhead.api.RecursiveFrag;
import com.wf.wflib.warhead.api.TorpedoWarhead;
import com.wf.wflib.warhead.api.Warheads;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;

/** WFLib's own missile presets: defined at startup, live only once requested ({@link com.wf.wflib.core.api.WFLibContent}). */
public final class MissileCatalogue {
    private static final WFRegistry<MissilePreset> PRESETS = WFRegistry.create("missile", "missile catalogue");

    private MissileCatalogue() {
    }

    public static Collection<MissilePreset> all() {
        return PRESETS.values();
    }

    @Nullable
    public static MissilePreset get(ResourceLocation id) {
        return PRESETS.get(id);
    }

    private static ResourceLocation rl(String path) {
        return MissilePresets.rl(path);
    }

    private static void add(MissilePreset preset) {
        PRESETS.register(preset.id(), preset);
    }

    static void define() {
        add(MissilePreset.builder(rl("cruise"), MissileModels.rl("v2"), Warheads.rl("standard"))
                .terrainFollow(24.0).cruiseSpeed(1.0).fuel(MissileEntity.FuelType.LIQUID, 1500).mass(1300)
                .build());

        add(MissilePreset.builder(rl("ballistic"), MissileModels.rl("strong"), Warheads.rl("standard"))
                .highAltitude(220.0).cruiseSpeed(1.2).health(60.0f)
                .fuel(MissileEntity.FuelType.SOLID, 2000).mass(4000)
                .build());

        add(MissilePreset.builder(rl("nuclear"), MissileModels.rl("huge"), Warheads.rl("mininuke"))
                .highAltitude(240.0).cruiseSpeed(1.0).health(90.0f)
                .fuel(MissileEntity.FuelType.SOLID, 2500).mass(12000)
                .build());

        add(MissilePreset.builder(rl("fragmentation"), MissileModels.rl("atlas"), Warheads.rl("fragmentation"))
                .highAltitude(200.0).explosionOffset(30.0f).fragmentCount(32)
                .fuel(MissileEntity.FuelType.SOLID, 1500).mass(3000)
                .build());

        add(MissilePreset.builder(rl("cluster"), MissileModels.rl("cluster"), RecursiveFrag.ID)
                .terrainFollow(16.0).cruiseSpeed(1.5).splitDepth(2).fragmentCount(4)
                .explosionOffset(16.0f).fuel(MissileEntity.FuelType.SOLID, 1200).mass(1200)
                .build());

        add(MissilePreset.builder(rl("chemical"), MissileModels.rl("taint"), GasWarhead.ID)
                .highAltitude(250).explosionOffset(5.0f)
                .fuel(MissileEntity.FuelType.LIQUID, 1500).mass(1500)
                .build());

        // Airburst above the canopy: spray falls through it to the ground.
        add(MissilePreset.builder(rl("agent_orange"), MissileModels.rl("taint"), AgentOrangeWarhead.ID)
                .highAltitude(250).explosionOffset(24.0f)
                .fuel(MissileEntity.FuelType.LIQUID, 1500).mass(1500)
                .build());

        add(MissilePreset.builder(rl("incendiary"), MissileModels.rl("v2_incendiary"), FireWarhead.ID)
                .terrainFollow(24.0).cruiseSpeed(1.0)
                .fuel(MissileEntity.FuelType.LIQUID, 1500).mass(1300)
                .build());

        add(MissilePreset.builder(rl("loiter"), MissileModels.rl("shahed"), Warheads.rl("fragmentation"))
                .terrainFollow(30.0).cruiseSpeed(0.8)
                .cruiseStage(FlightStages.rl("loiter")).attackStage(FlightStages.rl("dive"))
                .accel(0.08, 0.15).fuel(MissileEntity.FuelType.LIQUID, 2400)
                .fragmentCount(16).health(15.0f).mass(200)
                .build());

        add(MissilePreset.builder(rl("supersonic"), MissileModels.rl("thermo"), Warheads.rl("standard"))
                .highAltitude(260.0).cruiseSpeed(6.0).health(60.0f)
                .accel(0.4, 0.5).fuel(MissileEntity.FuelType.SOLID, 1600).evasion(0.2f).mass(2500)
                .build());

        add(MissilePreset.builder(rl("supersonic_cruise"), MissileModels.rl("neon"), Warheads.rl("standard"))
                .terrainFollow(30.0).cruiseSpeed(5.0)
                .accel(0.4, 0.5).fuel(MissileEntity.FuelType.LIQUID, 1600).evasion(0.15f).mass(2900)
                .build());

        add(MissilePreset.builder(rl("hypersonic"), MissileModels.rl("neon"), Warheads.rl("standard"))
                .highAltitude(300.0).cruiseSpeed(12.0).health(60.0f)
                .accel(0.8, 0.9).fuel(MissileEntity.FuelType.SOLID, 2000).evasion(0.3f)
                .evasiveManeuver().mass(4000)
                .build());

        add(MissilePreset.builder(rl("marv"), MissileModels.rl("atlas_thermo"), Warheads.rl("standard"))
                .highAltitude(280.0).cruiseSpeed(7.0).turnRate(0.35).health(70.0f)
                .accel(0.5, 0.6).fuel(MissileEntity.FuelType.SOLID, 1800).evasion(0.6f)
                .evasiveManeuver().mass(3500)
                .build());

        add(MissilePreset.builder(rl("stealth"), MissileModels.rl("stealth"), Warheads.rl("standard"))
                .terrainFollow(24.0).cruiseSpeed(1.2).stealth().evasion(0.3f)
                .fuel(MissileEntity.FuelType.LIQUID, 1600).mass(1000)
                .build());

        add(MissilePreset.builder(rl("emp"), MissileModels.rl("stealth"), Warheads.rl("emp"))
                .terrainFollow(24.0).cruiseSpeed(1.2).stealth().evasion(0.3f)
                .fuel(MissileEntity.FuelType.LIQUID, 1600).mass(1000)
                .build());

        add(MissilePreset.builder(rl("tv"), MissileModels.rl("atgm"), Warheads.rl("shaped_charge"))
                .cruiseSpeed(2.5).turnRate(0.12).health(10.0f).accel(0.25, 0.25)
                .fuel(MissileEntity.FuelType.SOLID, 600)
                .ascentStage(FlightStages.rl("air_launch")).attackStage(FlightStages.rl("direct"))
                .seeker(CameraSpec.TV_SEEKER).mass(45)
                .build());

        // MLRS pod rounds (M142/M270): GPS to the point, steep terminal dive.
        add(MissilePreset.builder(rl("gmlrs"), MissileModels.rl("guided_rocket"), Warheads.rl("standard"))
                .highAltitude(200.0).cruiseSpeed(6.0).turnRate(0.25).health(20.0f)
                .accel(0.5, 0.6).fuel(MissileEntity.FuelType.SOLID, 1400).mass(307)
                .build());

        add(MissilePreset.builder(rl("gmlrs_aw"), MissileModels.rl("guided_rocket"),
                        Warheads.rl("fragmentation"))
                .highAltitude(200.0).cruiseSpeed(6.0).turnRate(0.25).health(20.0f)
                .accel(0.5, 0.6).fuel(MissileEntity.FuelType.SOLID, 1400)
                .explosionOffset(8.0f).fragmentCount(48).mass(307)
                .build());

        // Single-round pod (M142/M270): longer reach, larger unitary charge.
        add(MissilePreset.builder(rl("atacms"), MissileModels.rl("neon"), Warheads.rl("standard"))
                .highAltitude(300.0).cruiseSpeed(9.0).turnRate(0.25).health(40.0f)
                .accel(0.6, 0.7).fuel(MissileEntity.FuelType.SOLID, 2400).mass(1670)
                .build());

        add(MissilePreset.builder(rl("torpedo"), MissileModels.rl("micro"), TorpedoWarhead.ID)
                .torpedo().highAltitude(8.0).clearance(4.0).cruiseSpeed(0.35).turnRate(0.1)
                .health(20.0f).accel(0.05, 0.05).fuel(MissileEntity.FuelType.SOLID, 1400).mass(1600)
                .build());

        add(MissilePreset.builder(rl("torpedo_bottom"), MissileModels.rl("micro"), TorpedoWarhead.ID)
                .torpedo().terrainFollow(3.0).cruiseSpeed(0.25).turnRate(0.1)
                .health(20.0f).accel(0.04, 0.04).fuel(MissileEntity.FuelType.SOLID, 2000).mass(1500)
                .build());

        add(MissilePreset.builder(rl("torpedo_fast"), MissileModels.rl("micro"), TorpedoWarhead.ID)
                .torpedo().highAltitude(5.0).clearance(3.0).cruiseSpeed(0.9).turnRate(0.06)
                .health(12.0f).accel(0.15, 0.15).fuel(MissileEntity.FuelType.SOLID, 500).mass(800)
                .build());

        add(MissilePreset.builder(rl("interceptor"), MissileModels.rl("abm"), Warheads.rl("interceptor"))
                .highAltitude(200.0)
                .cruiseSpeed(MissileSimConfig.INTERCEPTOR_ENTITY_SPEED)
                .turnRate(MissileSimConfig.INTERCEPTOR_TURN_RATE)
                .health(20.0f)
                .interceptor(MissileConfig.defaultInterceptChance())
                .accel(0.6, 0.6).fuel(MissileEntity.FuelType.SOLID, 600).mass(450)
                .build());

        add(MissilePreset.builder(rl("interceptor_supersonic"), MissileModels.rl("abm"), Warheads.rl("interceptor"))
                .highAltitude(220.0)
                .cruiseSpeed(MissileSimConfig.INTERCEPTOR_SUPERSONIC_ENTITY_SPEED)
                .turnRate(MissileSimConfig.INTERCEPTOR_SUPERSONIC_TURN_RATE)
                .health(20.0f)
                .interceptor(MissileConfig.defaultInterceptChance())
                .accel(0.9, 0.9).fuel(MissileEntity.FuelType.SOLID, 700).mass(900)
                .build());

        add(MissilePreset.builder(rl("interceptor_hypersonic"), MissileModels.rl("abm"), Warheads.rl("interceptor"))
                .highAltitude(260.0)
                .cruiseSpeed(18.0)
                .turnRate(0.45)
                .health(20.0f)
                .interceptor(MissileConfig.defaultInterceptChance())
                .accel(1.3, 1.3).fuel(MissileEntity.FuelType.SOLID, 800).mass(1400)
                .build());

        add(VehicleOrdnance.missile(rl("fim_92"), MissileModels.rl("guided_rocket"), ThreatKind.HE, 70, 99, 8, 2,
                10.1, 1616, 600, 0.0505, 18, 6, SeekerMode.INFRARED, 30));
        // 9K32M Strela-2M: ~60% of the FIM-92's cruise, 1.15 kg warhead, softer turn, narrower seeker.
        add(VehicleOrdnance.missile(rl("9m32m"), MissileModels.rl("guided_rocket"), ThreatKind.HE, 72, 80, 6, 2,
                9.15, 605, 600, 0.05, 12, 6, SeekerMode.INFRARED, 20));
        // FGM-148 Javelin: soft launch, top attack (70 deg dive), tandem HEAT.
        add(VehicleOrdnance.topAttack(rl("fgm_148"), MissileModels.rl("atgm"), ThreatKind.TANDEM_HEAT, 127, 150,
                3, 11.8, 384, 400, 0.06, 30, 3, SeekerMode.INFRARED, 20, 70));
    }
}
