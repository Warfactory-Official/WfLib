package com.wf.wflib.anomaly.client;

import com.wf.wflib.anomaly.AnomalyBlockEntity;
import com.wf.wflib.anomaly.AnomalyType;
import com.wf.wflib.xray.api.XrScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;

import java.util.HashMap;
import java.util.Map;
import com.wf.wflib.core.api.WFLib;

/**
 * Anomalies colour the screen as the player nears them (X-Ray {@code CZoneEffector}): each zone class's
 * {@code postprocess} section ({@code misc/postprocess.ltx}, GAMMA's) names a {@code .ppe} and the share of the
 * anomaly's radius it starts at ({@code radius_max}) and peaks within ({@code radius_min}); the nearest anomaly of
 * each effect sets its factor.
 */
public final class AnomalyScreen {
    private record Zone(String ppe, float min, float max) {
    }

    private static final Zone GRAVI_MINE = new Zone("gravi_mine", 0.7F, 1.0F);
    private static final Zone ELECTRA_MINE = new Zone("electra_mine", 0.3F, 1.0F);
    private static final Zone THERMAL_MINE = new Zone("thermal_mine", 0.7F, 1.0F);
    private static final Zone STEAM_MINE = new Zone("steam_mine", 0.7F, 1.0F);
    private static final Zone ACIDIC_MINE = new Zone("acidic_mine", 0.7F, 1.0F);
    private static final Zone ORANGE = new Zone("mine_orange", 0.3F, 1.0F);
    private static final Zone FUZZ = new Zone("fuzz", 0.5F, 0.8F);
    private static final Zone RADIATION = new Zone("radiation", 0.3F, 1.0F);
    private static final Zone THERMAL = new Zone("thermal", 0.3F, 1.0F);
    private static final Zone ACIDIC = new Zone("acidic", 0.3F, 1.0F);
    private static final Zone PSI = new Zone("psi", 0.3F, 1.0F);

    /** GAMMA zone sections' {@code postprocess} per type. */
    private static final Map<AnomalyType, Zone> ZONES = Map.ofEntries(
            Map.entry(AnomalyType.VORTEX, GRAVI_MINE), Map.entry(AnomalyType.WHIRLIGIG, GRAVI_MINE),
            Map.entry(AnomalyType.SPRINGBOARD, GRAVI_MINE), Map.entry(AnomalyType.NET, GRAVI_MINE),
            Map.entry(AnomalyType.REBOUNDER, GRAVI_MINE),
            Map.entry(AnomalyType.ELECTRO, ELECTRA_MINE), Map.entry(AnomalyType.FLASH, ELECTRA_MINE),
            Map.entry(AnomalyType.BALL_LIGHTNING, ELECTRA_MINE), Map.entry(AnomalyType.DARKNESS, ELECTRA_MINE),
            Map.entry(AnomalyType.BURNER, THERMAL_MINE), Map.entry(AnomalyType.COMET, THERMAL_MINE),
            Map.entry(AnomalyType.RADAR, THERMAL_MINE),
            Map.entry(AnomalyType.STEAM, STEAM_MINE), Map.entry(AnomalyType.STEAM_VENT, STEAM_MINE),
            Map.entry(AnomalyType.ACID, ACIDIC_MINE), Map.entry(AnomalyType.GHOST, ACIDIC_MINE),
            Map.entry(AnomalyType.GREEN_DRAGON, ACIDIC_MINE),
            Map.entry(AnomalyType.LIQUID_GOLD, ORANGE), Map.entry(AnomalyType.MEPHISTOPHELES, ORANGE),
            Map.entry(AnomalyType.BURNT_FUZZ, FUZZ), Map.entry(AnomalyType.GRABBER, FUZZ),
            Map.entry(AnomalyType.RADIATION_FIELD, RADIATION), Map.entry(AnomalyType.NO_GRAVITY, RADIATION),
            Map.entry(AnomalyType.THERMAL_FIELD, THERMAL), Map.entry(AnomalyType.ACID_FIELD, ACIDIC),
            Map.entry(AnomalyType.PSY_FIELD, PSI));

    private static final java.util.List<Zone> ALL = java.util.List.copyOf(new java.util.HashSet<>(ZONES.values()));
    private static final Map<Zone, Float> FACTOR = new HashMap<>();

    private AnomalyScreen() {
    }

    /** {@code CZoneEffector::Update}: 0 past radius_max, 1 within radius_min, linear between. */
    static float factor(double dist, double radius, float min, float max) {
        double hi = radius * max;
        double lo = radius * min;
        return dist >= hi ? 0.0F : (float) Math.clamp((hi - dist) / (hi - lo), 0.01D, 1.0D);
    }

    @SubscribeEvent
    public static void onTick(ClientTickEvent.Post event) {
        LocalPlayer player = Minecraft.getInstance().player;
        FACTOR.clear();
        if (player != null) {
            for (AnomalyBlockEntity be : AnomalyClient.live()) {
                Zone z = ZONES.get(be.type());
                if (z == null || be.isRemoved() || be.getLevel() != player.level()) {
                    continue;
                }
                float f = factor(be.center().distanceTo(player.getEyePosition()), be.behaviour().radius(), z.min(),
                        z.max()) * be.presence(1.0F);
                if (f > FACTOR.getOrDefault(z, 0.0F)) {
                    FACTOR.put(z, f);
                }
            }
        }
        for (Zone z : ALL) {
            String id = "wflib:anomaly/" + z.ppe();
            Float f = FACTOR.get(z);
            if (f != null && f > 0.0F) {
                XrScreen.post(id, XrScreen.ppe(WFLib.MODID, z.ppe()), true, f);
            } else if (XrScreen.running(id)) {
                XrScreen.stop(id, 2.0F);
            }
        }
    }
}
