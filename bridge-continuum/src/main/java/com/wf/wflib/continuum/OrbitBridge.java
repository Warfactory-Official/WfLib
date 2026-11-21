package com.wf.wflib.continuum;

import com.hbm.saveddata.SatelliteSavedData;
import com.hbm.saveddata.satellites.SatelliteType;
import com.hbm.saveddata.satellites.XSatelliteRegistry;
import com.wf.wflib.orbital.api.OrbitElements;
import com.wf.wflib.orbital.OrbitalData;
import com.wf.wflib.orbital.api.SatPayloads;
import com.wf.wflib.orbital.api.SatSpec;
import com.wf.wflib.orbital.api.Satellite;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Continuum's satellites in WFLib's sky (docs, 3.8). When one reaches orbit, a WFLib satellite of the matching payload is put up beside it, called {@code NTM-<frequency>}:
 * it is in the catalogue the PDA satellites app lists, a recon scope sees it, a hunter can be sent after it. Continuum's satellite keeps doing its own job (its data,
 * its commands); when it is gone, from a death-ray strike or a de-orbit, the mirror comes down with it.
 */
final class OrbitBridge {
    private static final String PREFIX = "NTM-";

    private OrbitBridge() {
    }

    static void install() {
        XSatelliteRegistry.ORBITED.register((level, stack, freq, sat) -> mirror(level, freq, sat.type()));
        NeoForge.EVENT_BUS.addListener(OrbitBridge::onTick);
        ContinuumBridge.LOG.info("Orbit: Continuum satellites are mirrored into WFLib's orbital catalogue");
    }

    /** Continuum's type to what it is for in WFLib's terms. */
    static ResourceLocation payloadFor(SatelliteType type) {
        return switch (type.name().toLowerCase(Locale.ROOT)) {
            case "relay" -> SatPayloads.RELAY;
            case "radar" -> SatPayloads.RECON_RADAR;
            case "miner", "lunar_miner" -> SatPayloads.MINER;
            case "laser", "precision_laser" -> SatPayloads.LASER;
            case "mapper", "scanner", "detector", "ray_scan", "horizons", "resonator" -> SatPayloads.IMAGERY;
            default -> SatPayloads.INERT;
        };
    }

    static String callsign(int freq) {
        return PREFIX + freq;
    }

    static Satellite mirror(ServerLevel level, int freq, SatelliteType type) {
        OrbitalData data = OrbitalData.get(level);
        Satellite existing = data.byCallsign(callsign(freq));
        if (existing != null) return existing;
        // Continuum's launch does not say where, so the track is the world's own: through spawn, heading by frequency.
        var spawn = level.getSharedSpawnPos();
        double heading = Math.floorMod(freq, 360) * Math.PI / 180.0;
        SatSpec spec = SatSpec.of(payloadFor(type)).callsign(callsign(freq));
        return data.add(spec, OrbitElements.leo(level.getGameTime(), spawn.getX(), spawn.getZ(), heading));
    }

    private static void onTick(ServerTickEvent.Post event) {
        if (event.getServer().getTickCount() % 200 != 0) return;
        for (ServerLevel level : event.getServer().getAllLevels()) {
            OrbitalData data = OrbitalData.get(level);
            if (data.isEmpty()) continue;
            List<Satellite> gone = new ArrayList<>();
            for (Satellite sat : data.all()) {
                String callsign = sat.callsign();
                if (!callsign.startsWith(PREFIX)) continue;
                int freq;
                try {
                    freq = Integer.parseInt(callsign.substring(PREFIX.length()));
                } catch (NumberFormatException e) {
                    continue;
                }
                if (SatelliteSavedData.get(level).sats.get(freq) == null) gone.add(sat);
            }
            for (Satellite sat : gone) data.remove(sat.satId());
        }
    }
}
