package com.wf.wflib.item;

import com.wf.wflib.WFLib;
import com.wf.wflib.ammo.CartridgeItem;
import com.wf.wflib.kinetic.KineticShellItem;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;

import java.util.Map;

/**
 * Empty unit mass (kg) of wflib items. Presets carry their own mass (missile, mine, shell, calibre); the rest is
 * {@link #TABLE}, keyed by item path, blocks included. 0 = none known. Contents are added by the wfweight provider.
 */
public final class ItemMasses {
    /** Ammo can / drum / pod, devices, worn gear, placed blocks. */
    static final Map<String, Double> TABLE = Map.ofEntries(
            Map.entry("ammo_can_7_62x51", 1.9), Map.entry("ammo_can_12_7x99", 3.0),
            Map.entry("ammo_can_12_7x108", 3.0), Map.entry("ammo_can_40x53", 2.5), Map.entry("drum_20x102", 20.0),
            Map.entry("pod_gmlrs", 450.0), Map.entry("pod_atacms", 300.0),
            Map.entry("spare_barrel", 11.0),
            Map.entry("smoke_grenade", 0.5), Map.entry("aps_grenade", 1.2), Map.entry("flare_cartridge", 0.3),
            Map.entry("decoy_launcher", 2.5), Map.entry("portable_jammer", 5.0), Map.entry("loudspeaker", 6.0),
            Map.entry("camera_linker", 0.3), Map.entry("detonator", 0.5), Map.entry("door_key", 0.05),
            Map.entry("door_lock", 0.8), Map.entry("grid_key", 0.05), Map.entry("geiger_counter", 0.4),
            Map.entry("pda", 0.4), Map.entry("kerosene_bucket", 4.2),
            Map.entry("nvg_gen1", 2.0), Map.entry("nvg_pvs7", 0.68), Map.entry("nvg_pvs14", 0.4),
            Map.entry("nvg_pvs31", 0.85), Map.entry("nvg_gpnvg18", 0.5),
            Map.entry("plate_carrier", 2.0), Map.entry("ceramic_plate", 2.0), Map.entry("steel_plate", 3.2),
            Map.entry("combat_helmet", 1.4), Map.entry("hazmat_suit", 4.0),
            Map.entry("mw_6b47", 1.5), Map.entry("mw_bearsuit", 3.0), Map.entry("mw_bearpants", 1.5),
            Map.entry("mw_uniform", 1.5), Map.entry("mw_exo", 12.0), Map.entry("mw_ghillie", 3.0),

            Map.entry("mining_charge", 5.0), Map.entry("deep_mining_charge", 12.0),
            Map.entry("missile_listener_debug", 1.0), Map.entry("drone_net", 8.0), Map.entry("drone_pad", 40.0),
            Map.entry("cargo_shuttle", 60.0), Map.entry("landing_pad", 120.0), Map.entry("orbital_jammer", 150.0),
            Map.entry("missile_dispenser", 400.0), Map.entry("turret_interceptor", 900.0),
            Map.entry("turret_interceptor_supersonic", 1400.0), Map.entry("glyphid_nest", 200.0),
            Map.entry("glyphid_nest_reinforced", 400.0), Map.entry("glyphid_spawner", 100.0),
            Map.entry("radar_surveillance", 800.0), Map.entry("radar_scope", 25.0), Map.entry("camera_monitor", 6.0),
            Map.entry("security_camera", 3.0), Map.entry("recon_hub", 60.0), Map.entry("grid_power_cell", 80.0),
            Map.entry("probe_radar", 25.0), Map.entry("probe_seismic", 15.0), Map.entry("probe_sonar", 20.0),
            Map.entry("probe_thermal", 12.0), Map.entry("probe_weather", 10.0),
            Map.entry("gravity_anomaly", 10.0), Map.entry("whirligig", 15.0), Map.entry("springboard", 20.0),
            Map.entry("electro", 10.0), Map.entry("burner", 10.0), Map.entry("acid", 10.0),
            Map.entry("steam", 10.0), Map.entry("flash", 10.0), Map.entry("ghost", 10.0), Map.entry("net", 10.0),
            Map.entry("ball_lightning", 10.0), Map.entry("liquid_gold", 10.0), Map.entry("green_dragon", 10.0),
            Map.entry("rebounder", 15.0), Map.entry("mephistopheles", 15.0), Map.entry("burnt_fuzz", 10.0),
            Map.entry("grabber", 10.0), Map.entry("comet", 10.0), Map.entry("radiation_field", 10.0),
            Map.entry("thermal_field", 10.0), Map.entry("acid_field", 10.0), Map.entry("no_gravity", 10.0),
            Map.entry("fire_door", 150.0), Map.entry("cargo_door", 400.0), Map.entry("blast_door", 1200.0),
            Map.entry("large_vehicle_door", 2000.0), Map.entry("qe_containment", 500.0),
            Map.entry("qe_sliding_door", 350.0), Map.entry("round_airlock_door", 300.0),
            Map.entry("secure_access_door", 120.0), Map.entry("silo_hatch", 3000.0),
            Map.entry("silo_hatch_large", 6000.0), Map.entry("sliding_blast_door", 1500.0),
            Map.entry("sliding_seal_door", 250.0), Map.entry("transition_seal", 200.0),
            Map.entry("vault_door", 2500.0), Map.entry("water_door", 180.0));
    private ItemMasses() {
    }

    public static double unit(Item item) {
        return switch (item) {
            case CartridgeItem c -> c.calibre().massKg();
            case KineticShellItem k -> k.preset().mass();
            case MissileItem m -> m.preset().massKg();
            case MineItem m -> m.preset().massKg();
            default -> {
                ResourceLocation id = BuiltInRegistries.ITEM.getKey(item);
                yield id.getNamespace().equals(WFLib.MODID) ? TABLE.getOrDefault(id.getPath(), 0.0) : 0;
            }
        };
    }
}
