package com.wf.wflib.artifact.detect.client;

import com.wf.gemrender.light.Lights;
import com.wf.wflib.artifact.api.Artifact;
import com.wf.wflib.artifact.api.ZoneArtifact;
import com.wf.wflib.particle.api.client.XrParticles;
import net.minecraft.client.Minecraft;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;
import com.wf.wflib.artifact.api.ArtifactSounds;

/**
 * X-Ray's artefact look round a {@link ZoneArtifact}: shown by a detector, {@code det_show_particles} +
 * {@code det_show_snd} once, then {@code particles} looped and the trail light while shown; unseen and over 40 m from
 * the camera, a {@code det_show_particles} blink every 2 game hours ({@code SArtefactDetectorsSupport::Blink}: X-Ray's
 * time factor 10 => 12 real minutes).
 */
public final class ZoneArtifactClient {
    private static final double NEAR = 64.0D;
    private static final double BLINK_FROM = 40.0D;
    private static final long BLINK_MS = 12L * 60L * 1000L;
    private static final double LIGHT_WITHIN = 32.0D;

    private static final class Seen {
        boolean shown;
        @Nullable
        XrParticles.Playing idle;
        long blinkAt = System.currentTimeMillis() + ThreadLocalRandom.current().nextLong(BLINK_MS);
    }

    private static final Map<ZoneArtifact, Seen> SEEN = new HashMap<>();

    private ZoneArtifactClient() {
    }

    static Vec3 centre(ZoneArtifact a, float partialTick) {
        return a.getPosition(partialTick).add(0.0D, a.getBbHeight() * 0.5D, 0.0D);
    }

    @SubscribeEvent
    public static void onTick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) {
            SEEN.values().forEach(ZoneArtifactClient::stopIdle);
            SEEN.clear();
            return;
        }
        Vec3 eye = mc.gameRenderer.getMainCamera().getPosition();
        long now = System.currentTimeMillis();
        for (ZoneArtifact a : mc.level.getEntitiesOfClass(ZoneArtifact.class,
                mc.player.getBoundingBox().inflate(NEAR))) {
            Artifact art = a.artifact();
            if (art == null) {
                continue;
            }
            Seen s = SEEN.computeIfAbsent(a, k -> new Seen());
            boolean shown = a.shown();
            Vec3 at = centre(a, 1.0F);
            if (shown && !s.shown && !a.isDropped()) {
                XrParticles.play(art.showFx(), at, 1.0F, 1.0F, false);
                mc.level.playLocalSound(at.x, at.y, at.z, ArtifactSounds.ANOMALY_BFUZZ_BLOWOUT.get(), SoundSource.NEUTRAL,
                        0.5F, 1.0F, false);
            }
            s.shown = shown;
            if (shown && art.idleFx() != null) {
                if (s.idle == null) {
                    s.idle = XrParticles.play(art.idleFx(), at, 1.0F, 1.0F, true).orElse(null);
                } else {
                    s.idle.trailTo(at);
                }
            } else {
                stopIdle(s);
            }
            if (!shown && now >= s.blinkAt) {
                s.blinkAt = now + BLINK_MS;
                if (at.distanceTo(eye) > BLINK_FROM) {
                    XrParticles.play(art.showFx(), at, 1.0F, 1.0F, false);
                }
            }
        }
        for (Iterator<Map.Entry<ZoneArtifact, Seen>> it = SEEN.entrySet().iterator(); it.hasNext(); ) {
            Map.Entry<ZoneArtifact, Seen> e = it.next();
            if (e.getKey().isRemoved() || e.getKey().level() != mc.level || e.getKey().distanceTo(mc.player) > NEAR) {
                stopIdle(e.getValue());
                it.remove();
            }
        }
    }

    private static void stopIdle(Seen s) {
        if (s.idle != null) {
            s.idle.stop();
            s.idle = null;
        }
    }

    /** Client setup: a shown artefact's trail light ({@code trail_light_color}, {@code trail_light_range}). */
    public static void registerLights() {
        Lights.register((sink, partialTick) -> {
            Minecraft mc = Minecraft.getInstance();
            if (mc.level == null || mc.player == null) {
                return;
            }
            for (ZoneArtifact a : mc.level.getEntitiesOfClass(ZoneArtifact.class,
                    mc.player.getBoundingBox().inflate(LIGHT_WITHIN), ZoneArtifact::shown)) {
                Artifact art = a.artifact();
                if (art != null) {
                    Vec3 p = centre(a, partialTick);
                    sink.point(p.x, p.y, p.z, art.lightRange(), art.lightRgb(),
                            art.lightRange() * art.lightRange() * 0.35F, false);
                }
            }
        });
    }
}
