package com.wf.wflib.recon.example;

import com.wf.wflib.recon.api.Band;
import com.wf.wflib.recon.api.Countermeasure;
import com.wf.wflib.recon.api.Signature;
import com.wf.wflib.recon.api.SensorSnapshot;
import com.wf.wflib.recon.api.TargetSnapshot;

/**
 * The simplest countermeasure there is: multiply the radar cross-section by a constant.
 *
 * @param factor multiplier on radar cross-section. 1.0 is inert, 0.0 is invisible to radar.
 */
public record RadarAbsorbentCoating(float factor) implements Countermeasure {

    /** A believable coating: a sixteenfold cut, which halves the range it is seen at. */
    public static final RadarAbsorbentCoating STANDARD = new RadarAbsorbentCoating(0.0625f);

    @Override
    public Band band() {
        return Band.RADAR;
    }

    @Override
    public Signature modify(Signature in, TargetSnapshot self, SensorSnapshot sensor) {
        return in.scaled(Band.RADAR, factor);
    }
}
