package com.wf.wflib.continuum;

import com.hbm.api.event.HbmEvents;
import com.hbm.saveddata.satellites.XSatelliteRegistry;
import com.wf.wflib.pda.api.PdaAlert;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Continuum's events as PDA alerts (docs, 3.10): a missile launch, a missile impact, a large explosion, a satellite reaching orbit and a dose of radiation reach a
 * carrier's PDA as WFLib alerts, so the PDA's alert feed tells you what Continuum's own radar blocks and chat messages do separately. A carrier hears what is
 * in range: launches and blasts within {@link #HEARING} blocks, everything about their own body at once.
 */
final class PdaBridge {
    /** Blocks a launch, an impact or a large explosion is heard at. */
    static final double HEARING = 3000.0;
    /** Explosions under this size are not worth an alert. */
    static final float LARGE_BLAST = 20.0F;
    private static final Map<UUID, Long> LAST_DOSE = new ConcurrentHashMap<>();

    private PdaBridge() {
    }

    static void install() {
        HbmEvents.MISSILE_LAUNCHED.register(e -> {
            if (!(e.level() instanceof ServerLevel level)) return;
            boolean heavy = nuclear(e.missileType());
            broadcast(level, e.launcher().getX(), e.launcher().getZ(),
                    Component.literal("Launch detected: " + pretty(e.missileType()) + " from " + e.launcher().getX() + ", " + e.launcher().getZ()
                            + " toward " + e.targetX() + ", " + e.targetZ()),
                    heavy ? PdaAlert.Tier.CRITICAL : PdaAlert.Tier.WARNING);
        });
        HbmEvents.MISSILE_IMPACT.register(e -> {
            if (!(e.level() instanceof ServerLevel level)) return;
            broadcast(level, e.x(), e.z(), Component.literal("Missile impact: " + pretty(e.missileType()) + " at " + (int) e.x() + ", " + (int) e.z()),
                    nuclear(e.missileType()) ? PdaAlert.Tier.CRITICAL : PdaAlert.Tier.WARNING);
        });
        HbmEvents.EXPLOSION.register(e -> {
            if (e.size() < LARGE_BLAST || !(e.level() instanceof ServerLevel level)) return;
            broadcast(level, e.x(), e.z(), Component.literal("Large blast: size " + (int) e.size() + " at " + (int) e.x() + ", " + (int) e.z()), PdaAlert.Tier.WARNING);
        });
        XSatelliteRegistry.ORBITED.register((level, stack, freq, sat) ->
                broadcast(level, 0, 0, Component.literal("Satellite NTM-" + freq + " (" + sat.getType() + ") reached orbit"), PdaAlert.Tier.INFO, Double.POSITIVE_INFINITY));
        HbmEvents.ENTITY_IRRADIATED.register(e -> {
            if (!(e.entity() instanceof ServerPlayer player)) return;
            long now = player.level().getGameTime();
            Long last = LAST_DOSE.put(player.getUUID(), now);
            if (last != null && now - last < 200) return;           // one line every ten seconds, not one per tick of exposure
            PdaAlert.send(player, Component.literal(String.format(Locale.ROOT, "Radiation: %s, dose %.1f", e.hazard(), e.dose())), PdaAlert.Tier.WARNING);
        });
        ContinuumBridge.LOG.info("PDA: Continuum launches, impacts, large blasts, satellites and doses are PDA alerts");
    }

    static boolean nuclear(String missileType) {
        String t = missileType.toLowerCase(Locale.ROOT);
        return t.contains("nuclear") || t.contains("mirv") || t.contains("doomsday") || t.contains("thermo") || t.contains("n2") || t.contains("balefire");
    }

    private static String pretty(String type) {
        int colon = type.indexOf(':');
        return (colon >= 0 ? type.substring(colon + 1) : type).replace('_', ' ');
    }

    static int broadcast(ServerLevel level, double x, double z, Component message, PdaAlert.Tier tier) {
        return broadcast(level, x, z, message, tier, HEARING);
    }

    /** Tells every PDA carrier in the level within {@code range} blocks of (x, z). @return how many were told */
    static int broadcast(ServerLevel level, double x, double z, Component message, PdaAlert.Tier tier, double range) {
        return broadcast(level.players(), x, z, message, tier, range);
    }

    static int broadcast(Iterable<? extends ServerPlayer> players, double x, double z, Component message, PdaAlert.Tier tier, double range) {
        int told = 0;
        for (ServerPlayer p : players) {
            if (Double.isInfinite(range) || Math.hypot(p.getX() - x, p.getZ() - z) <= range) {
                if (com.wf.wflib.pda.api.Pda.carries(p)) {
                    PdaAlert.send(p, message, tier);
                    told++;
                }
            }
        }
        return told;
    }
}
