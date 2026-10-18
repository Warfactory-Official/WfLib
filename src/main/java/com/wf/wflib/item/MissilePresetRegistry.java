package com.wf.wflib.item;

import com.wf.wflib.MissileEntity;
import com.wf.wflib.MissileModels;
import com.wf.wflib.WFLib;
import com.wf.wflib.drone.cam.CameraSpec;
import com.wf.wflib.flight.FlightStageRegistry;
import com.wf.wflib.sim.MissileSimConfig;
import com.wf.wflib.warhead.AgentOrangeWarhead;
import com.wf.wflib.warhead.FireWarhead;
import com.wf.wflib.warhead.GasWarhead;
import com.wf.wflib.warhead.RecursiveFrag;
import com.wf.wflib.warhead.TorpedoWarhead;
import com.wf.wflib.warhead.WarheadRegistry;
import com.wf.wflib.api.ThreatKind;
import com.wf.wflib.missile.SeekerMode;
import com.wf.wflib.ordnance.VehicleOrdnance;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Launch-ready {@link MissilePreset}s, keyed by {@link ResourceLocation}. Catalogue entries live once requested
 * ({@link com.wf.wflib.content.WFLibContent}). Own registration before freeze => live + item; after => live, no item,
 * catalogue warhead pulled. Copy-on-write: any thread; reads live only.
 */
public final class MissilePresetRegistry {

    private static final ResourceLocation DEFAULT_ID = rl("cruise");
    private static volatile Map<ResourceLocation, MissilePreset> presets = Map.of();
    private static final List<MissilePreset> OWN = new ArrayList<>();
    private static volatile List<MissilePreset> withItems = List.of();
    private static boolean frozen;

    private static final Map<ResourceLocation, MissilePreset> CATALOGUE = new LinkedHashMap<>();

    static {
        defineDefaults();
    }

    private MissilePresetRegistry() {
    }

    public static ResourceLocation rl(String path) {
        return ResourceLocation.fromNamespaceAndPath(WFLib.MODID, path);
    }

    public static ResourceLocation parse(String id) {
        if (id == null || id.isEmpty()) {
            return DEFAULT_ID;
        }
        ResourceLocation parsed = id.indexOf(':') >= 0 ? ResourceLocation.tryParse(id) : rl(id);
        return parsed != null ? parsed : DEFAULT_ID;
    }

    public static synchronized void register(MissilePreset preset) {
        if (CATALOGUE.containsKey(preset.id())) {
            throw new IllegalStateException("missile preset " + preset.id() + ": WFLib catalogue entry, request it");
        }
        put(preset);
        if (frozen) {
            WarheadRegistry.require(preset.warheadId());
        } else {
            OWN.add(preset);
        }
    }

    private static void put(MissilePreset preset) {
        Map<ResourceLocation, MissilePreset> next = new LinkedHashMap<>(presets);
        next.put(preset.id(), preset);
        presets = Collections.unmodifiableMap(next);
    }

    /** {@code requested} catalogue entries + own pre-freeze registrations => live with item. Once. */
    public static synchronized void freeze(Set<ResourceLocation> requested) {
        if (frozen) {
            throw new IllegalStateException("missile presets frozen twice");
        }
        List<MissilePreset> items = new ArrayList<>();
        CATALOGUE.values().stream().filter(p -> requested.contains(p.id())).forEach(p -> {
            put(p);
            items.add(p);
        });
        items.addAll(OWN);
        withItems = List.copyOf(items);
        frozen = true;
    }

    public static MissilePreset get(ResourceLocation id) {
        return presets.get(id);
    }

    public static boolean exists(ResourceLocation id) {
        return presets.containsKey(id);
    }

    public static Collection<MissilePreset> all() {
        return presets.values();
    }

    public static Set<ResourceLocation> ids() {
        return presets.keySet();
    }

    /** Live presets with a {@link MissileItem}, registration order. */
    public static List<MissilePreset> withItems() {
        return withItems;
    }

    public static Collection<MissilePreset> catalogue() {
        return Collections.unmodifiableCollection(CATALOGUE.values());
    }

    /** Live or not. */
    @Nullable
    public static MissilePreset catalogueEntry(ResourceLocation id) {
        return CATALOGUE.get(id);
    }

    public static ResourceLocation defaultId() {
        return DEFAULT_ID;
    }

    private static void define(MissilePreset preset) {
        CATALOGUE.put(preset.id(), preset);
    }

    private static void defineDefaults() {
        define(MissilePreset.builder(rl("cruise"), MissileModels.rl("v2"), WarheadRegistry.rl("standard"))
                .terrainFollow(24.0).cruiseSpeed(1.0).fuel(MissileEntity.FuelType.LIQUID, 1500).build());

        define(MissilePreset.builder(rl("ballistic"), MissileModels.rl("strong"), WarheadRegistry.rl("standard"))
                .highAltitude(220.0).cruiseSpeed(1.2).health(60.0f)
                .fuel(MissileEntity.FuelType.SOLID, 2000).build());

        define(MissilePreset.builder(rl("nuclear"), MissileModels.rl("huge"), WarheadRegistry.rl("mininuke"))
                .highAltitude(240.0).cruiseSpeed(1.0).health(90.0f)
                .fuel(MissileEntity.FuelType.SOLID, 2500).build());

        define(MissilePreset.builder(rl("fragmentation"), MissileModels.rl("atlas"), WarheadRegistry.rl("fragmentation"))
                .highAltitude(200.0).explosionOffset(30.0f).fragmentCount(32)
                .fuel(MissileEntity.FuelType.SOLID, 1500).build());

        define(MissilePreset.builder(rl("cluster"), MissileModels.rl("cluster"), RecursiveFrag.ID)
                .terrainFollow(16.0).cruiseSpeed(1.5).splitDepth(2).fragmentCount(4)
                .explosionOffset(16.0f).fuel(MissileEntity.FuelType.SOLID, 1200).build());

        define(MissilePreset.builder(rl("chemical"), MissileModels.rl("taint"), GasWarhead.ID)
                .highAltitude(250).explosionOffset(5.0f)
                .fuel(MissileEntity.FuelType.LIQUID, 1500).build());

        // Airburst above the canopy: spray falls through it to the ground.
        define(MissilePreset.builder(rl("agent_orange"), MissileModels.rl("taint"), AgentOrangeWarhead.ID)
                .highAltitude(250).explosionOffset(24.0f)
                .fuel(MissileEntity.FuelType.LIQUID, 1500).build());

        define(MissilePreset.builder(rl("incendiary"), MissileModels.rl("v2_incendiary"), FireWarhead.ID)
                .terrainFollow(24.0).cruiseSpeed(1.0)
                .fuel(MissileEntity.FuelType.LIQUID, 1500).build());

        define(MissilePreset.builder(rl("loiter"), MissileModels.rl("shahed"), WarheadRegistry.rl("fragmentation"))
                .terrainFollow(30.0).cruiseSpeed(0.8)
                .cruiseStage(FlightStageRegistry.rl("loiter")).attackStage(FlightStageRegistry.rl("dive"))
                .accel(0.08, 0.15).fuel(MissileEntity.FuelType.LIQUID, 2400)
                .fragmentCount(16).health(15.0f).build());

        define(MissilePreset.builder(rl("supersonic"), MissileModels.rl("thermo"), WarheadRegistry.rl("standard"))
                .highAltitude(260.0).cruiseSpeed(6.0).health(60.0f)
                .accel(0.4, 0.5).fuel(MissileEntity.FuelType.SOLID, 1600).evasion(0.2f).build());

        define(MissilePreset.builder(rl("supersonic_cruise"), MissileModels.rl("neon"), WarheadRegistry.rl("standard"))
                .terrainFollow(30.0).cruiseSpeed(5.0)
                .accel(0.4, 0.5).fuel(MissileEntity.FuelType.LIQUID, 1600).evasion(0.15f).build());

        define(MissilePreset.builder(rl("hypersonic"), MissileModels.rl("neon"), WarheadRegistry.rl("standard"))
                .highAltitude(300.0).cruiseSpeed(12.0).health(60.0f)
                .accel(0.8, 0.9).fuel(MissileEntity.FuelType.SOLID, 2000).evasion(0.3f)
                .evasiveManeuver().build());

        define(MissilePreset.builder(rl("marv"), MissileModels.rl("atlas_thermo"), WarheadRegistry.rl("standard"))
                .highAltitude(280.0).cruiseSpeed(7.0).turnRate(0.35).health(70.0f)
                .accel(0.5, 0.6).fuel(MissileEntity.FuelType.SOLID, 1800).evasion(0.6f)
                .evasiveManeuver().build());

        define(MissilePreset.builder(rl("stealth"), MissileModels.rl("stealth"), WarheadRegistry.rl("standard"))
                .terrainFollow(24.0).cruiseSpeed(1.2).stealth().evasion(0.3f)
                .fuel(MissileEntity.FuelType.LIQUID, 1600).build());

        define(MissilePreset.builder(rl("emp"), MissileModels.rl("stealth"), WarheadRegistry.rl("emp"))
                .terrainFollow(24.0).cruiseSpeed(1.2).stealth().evasion(0.3f)
                .fuel(MissileEntity.FuelType.LIQUID, 1600).build());

        define(MissilePreset.builder(rl("tv"), MissileModels.rl("atgm"), WarheadRegistry.rl("shaped_charge"))
                .cruiseSpeed(2.5).turnRate(0.12).health(10.0f).accel(0.25, 0.25)
                .fuel(MissileEntity.FuelType.SOLID, 600)
                .ascentStage(FlightStageRegistry.rl("air_launch")).attackStage(FlightStageRegistry.rl("direct"))
                .seeker(CameraSpec.TV_SEEKER).build());

        // MLRS pod rounds (M142/M270): GPS to the point, steep terminal dive.
        define(MissilePreset.builder(rl("gmlrs"), MissileModels.rl("guided_rocket"), WarheadRegistry.rl("standard"))
                .highAltitude(200.0).cruiseSpeed(6.0).turnRate(0.25).health(20.0f)
                .accel(0.5, 0.6).fuel(MissileEntity.FuelType.SOLID, 1400).build());

        define(MissilePreset.builder(rl("gmlrs_aw"), MissileModels.rl("guided_rocket"),
                        WarheadRegistry.rl("fragmentation"))
                .highAltitude(200.0).cruiseSpeed(6.0).turnRate(0.25).health(20.0f)
                .accel(0.5, 0.6).fuel(MissileEntity.FuelType.SOLID, 1400)
                .explosionOffset(8.0f).fragmentCount(48).build());

        // Single-round pod (M142/M270): longer reach, larger unitary charge.
        define(MissilePreset.builder(rl("atacms"), MissileModels.rl("neon"), WarheadRegistry.rl("standard"))
                .highAltitude(300.0).cruiseSpeed(9.0).turnRate(0.25).health(40.0f)
                .accel(0.6, 0.7).fuel(MissileEntity.FuelType.SOLID, 2400).build());

        define(MissilePreset.builder(rl("torpedo"), MissileModels.rl("micro"), TorpedoWarhead.ID)
                .torpedo().highAltitude(8.0).clearance(4.0).cruiseSpeed(0.35).turnRate(0.1)
                .health(20.0f).accel(0.05, 0.05).fuel(MissileEntity.FuelType.SOLID, 1400).build());

        define(MissilePreset.builder(rl("torpedo_bottom"), MissileModels.rl("micro"), TorpedoWarhead.ID)
                .torpedo().terrainFollow(3.0).cruiseSpeed(0.25).turnRate(0.1)
                .health(20.0f).accel(0.04, 0.04).fuel(MissileEntity.FuelType.SOLID, 2000).build());

        define(MissilePreset.builder(rl("torpedo_fast"), MissileModels.rl("micro"), TorpedoWarhead.ID)
                .torpedo().highAltitude(5.0).clearance(3.0).cruiseSpeed(0.9).turnRate(0.06)
                .health(12.0f).accel(0.15, 0.15).fuel(MissileEntity.FuelType.SOLID, 500).build());

        define(MissilePreset.builder(rl("interceptor"), MissileModels.rl("abm"), WarheadRegistry.rl("interceptor"))
                .highAltitude(200.0)
                .cruiseSpeed(MissileSimConfig.INTERCEPTOR_ENTITY_SPEED)
                .turnRate(MissileSimConfig.INTERCEPTOR_TURN_RATE)
                .health(20.0f)
                .interceptor(MissileSimConfig.DEFAULT_INTERCEPT_CHANCE)
                .accel(0.6, 0.6).fuel(MissileEntity.FuelType.SOLID, 600).build());

        define(MissilePreset.builder(rl("interceptor_supersonic"), MissileModels.rl("abm"), WarheadRegistry.rl("interceptor"))
                .highAltitude(220.0)
                .cruiseSpeed(MissileSimConfig.INTERCEPTOR_SUPERSONIC_ENTITY_SPEED)
                .turnRate(MissileSimConfig.INTERCEPTOR_SUPERSONIC_TURN_RATE)
                .health(20.0f)
                .interceptor(MissileSimConfig.DEFAULT_INTERCEPT_CHANCE)
                .accel(0.9, 0.9).fuel(MissileEntity.FuelType.SOLID, 700).build());

        define(MissilePreset.builder(rl("interceptor_hypersonic"), MissileModels.rl("abm"), WarheadRegistry.rl("interceptor"))
                .highAltitude(260.0)
                .cruiseSpeed(18.0)
                .turnRate(0.45)
                .health(20.0f)
                .interceptor(MissileSimConfig.DEFAULT_INTERCEPT_CHANCE)
                .accel(1.3, 1.3).fuel(MissileEntity.FuelType.SOLID, 800).build());

        define(VehicleOrdnance.missile(rl("fim_92"), MissileModels.rl("guided_rocket"), ThreatKind.HE, 70, 99, 8, 2,
                10.1, 1616, 600, 0.0505, 18, 6, SeekerMode.INFRARED, 30));
    }
}
