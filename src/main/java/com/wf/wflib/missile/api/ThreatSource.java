package com.wf.wflib.missile.api;

import com.wf.wflib.damage.api.Threat;

/** A projectile that can say what kind of round it is. */
public interface ThreatSource {

    Threat threat();
}
