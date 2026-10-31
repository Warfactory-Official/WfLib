package com.wf.wflib.armor.api;

/** Deterministic from arithmetic, not rolled. */
public enum ArmorOutcome {
    /** Nothing got through; backface deformation can still bruise/crack a rib. */
    STOPPED,
    REDUCED,
    THROUGH
}
