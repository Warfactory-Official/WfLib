package com.wf.wflib.labs.api;

import com.wf.wflib.round.api.KineticPreset;

/**
 * What arrives at the next layer. Expected-value chain: {@code pReach} is the probability the round got here
 * (product of the earlier layers' {@code pPass}); {@code speedMs} is the speed on the perforating branch.
 *
 * @param roundId  kinetic preset id
 * @param launcher launcher id that fired it
 * @param speedMs  m/s on arrival
 * @param pReach   0..1
 * @param rangeM   m from the muzzle to the first layer
 */
public record ScenarioState(String launcher, String roundId, KineticPreset round, double speedMs, double pReach, double rangeM) {
    public ScenarioState next(double speedOut, double pPass) {
        return new ScenarioState(launcher, roundId, round, speedOut, pReach * pPass, rangeM);
    }
}
