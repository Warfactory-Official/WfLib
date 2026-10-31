package com.wf.wflib.artifact.api;

import net.minecraft.resources.ResourceLocation;
import com.wf.wflib.core.api.WFLib;

/**
 * STALKER's artefact detectors (GAMMA's numbers: {@code detector_simple / _advanced / _elite}). All hear artefacts
 * 30 m out ({@code af_radius}) and beep faster as one nears, from every 2 s to every 0.05 s ({@code af_freq}); one
 * shows an artefact only within its {@code af_vis_radius}, and finds only the kinds its rank reaches.
 *
 * @param rank      artefacts of this rank or lower are found (each artefact's {@code detectRank})
 * @param radius    heard within, m
 * @param reveal    shown (and can be picked up) within, m
 * @param display   what its face shows: a blinking LED, a direction, or a screen of blips
 */
public enum DetectorTier {
    /** {@code detector_simple}: the Echo. Beeps, an LED that blinks with it. */
    ECHO("echo", 1, 30.0D, 3.0D, Display.LED),
    /** {@code detector_advanced}: the Bear. Its LEDs light toward the nearest artefact. */
    BEAR("bear", 2, 30.0D, 4.0D, Display.DIRECTION),
    /** {@code detector_elite}: the Veles. A screen showing every artefact in range as a blip. */
    VELES("veles", 3, 30.0D, 5.0D, Display.SCREEN);

    public enum Display { LED, DIRECTION, SCREEN }

    /** Beep interval at the edge of hearing and right on top, seconds ({@code af_freq = 0.05, 2}). */
    public static final double BEEP_FAR = 2.0D;
    public static final double BEEP_NEAR = 0.05D;

    public final String id;
    public final int rank;
    public final double radius;
    public final double reveal;
    public final Display display;

    DetectorTier(String id, int rank, double radius, double reveal, Display display) {
        this.id = id;
        this.rank = rank;
        this.radius = radius;
        this.reveal = reveal;
        this.display = display;
    }

    public ResourceLocation model() {
        return ResourceLocation.fromNamespaceAndPath(WFLib.MODID, "models/detector/lowpoly/" + id + ".glb");
    }

    public ResourceLocation texture() {
        return ResourceLocation.fromNamespaceAndPath(WFLib.MODID, "textures/detector/" + id + ".png");
    }

    /** Seconds between beeps for an artefact {@code distance} away: linear from far to near, as X-Ray lerps it. */
    public double beepInterval(double distance) {
        double k = Math.max(0.0D, Math.min(1.0D, distance / radius));
        return BEEP_NEAR + (BEEP_FAR - BEEP_NEAR) * k;
    }
}
