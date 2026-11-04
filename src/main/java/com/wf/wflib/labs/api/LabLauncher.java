package com.wf.wflib.labs.api;

import java.util.Map;

/**
 * A weapon (or the bare round) that decides how a round leaves the muzzle. Lab tables treat the round's own preset
 * values as the default launcher {@code wflib:<round path>}.
 */
public interface LabLauncher {
    /** Namespaced, e.g. {@code <modid>:<weapon>}. */
    String id();

    /** Ammo variant / kinetic preset id the launcher fires. */
    String roundId();

    /** m/s, after barrel and attachments. */
    double muzzleVelocity();

    /** Minutes of angle, half-cone about the aim line. */
    double dispersionMoa();

    /** Direct-hit damage factor. */
    double damageScale();

    /** Launcher-specific columns (name -> number or string); keys must be identical across launchers of one source. */
    default Map<String, Object> extra() {
        return Map.of();
    }
}
