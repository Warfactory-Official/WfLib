package com.wf.wflib.missile.api;

import com.wf.wflib.recon.api.Band;

/** Entity that seduces seekers in a band (flare = THERMAL, chaff = RADAR). */
public interface SeekerDecoy {

    /** Competes with a target's {@link com.wf.wflib.recon.api.Signature#on} in the same band; 0 = inert. */
    float decoyStrength(Band band);
}
