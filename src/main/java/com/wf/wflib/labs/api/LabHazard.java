package com.wf.wflib.labs.api;

import org.jetbrains.annotations.Nullable;

/**
 * A non-ballistic threat a body meets. Values are the emitter's own constants at intensity 1.
 *
 * @param kind             {@code radiation}, {@code gas}, {@code fire}, ...; keys {@link LabShelter#shield}
 * @param damageType       damage type id of the hits it deals; null = none
 * @param damage           hp per hit at intensity 1
 * @param hitIntervalTicks ticks between hits
 * @param exposureType     {@code ProtectionType} name its contact charges armour with; null = none
 * @param exposureTier     armour condition points per exposure interval at intensity 1
 * @param doseRate         RAD/s at intensity 1 (radiation fields); 0 = not a dose hazard
 * @param intensities      swept multiples of intensity 1 (RAD/s multiples for dose hazards)
 */
public record LabHazard(String id, String kind, @Nullable String damageType, double damage, int hitIntervalTicks,
                        @Nullable String exposureType, double exposureTier, double doseRate, double[] intensities) {
    public static final double[] DEFAULT_INTENSITIES = {0.25, 0.5, 1.0, 2.0};
}
