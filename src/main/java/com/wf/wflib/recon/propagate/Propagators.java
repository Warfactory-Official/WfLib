package com.wf.wflib.recon.propagate;

import com.wf.wflib.recon.api.Band;
import org.jetbrains.annotations.Nullable;

import java.util.EnumMap;
import java.util.Map;
import com.wf.wflib.recon.api.Propagator;
import com.wf.wflib.recon.api.RadarPropagator;

/** Which model runs for which band. */
public final class Propagators {

    private static final Map<Band, Propagator> BY_BAND = new EnumMap<>(Map.of(
            Band.RADAR, RadarPropagator.INSTANCE,
            Band.SEISMIC, SeismicPropagator.INSTANCE,
            Band.THERMAL, ThermalPropagator.INSTANCE,
            Band.SONAR, SonarPropagator.INSTANCE));

    private Propagators() {
    }

    @Nullable
    public static Propagator of(Band band) {
        return BY_BAND.get(band);
    }
}
