package com.wf.wflib.labs.api;

import com.google.gson.JsonObject;

import java.util.Map;

/**
 * One layer of a scenario: what a round does at a wall, a vehicle plate, a body. Stages are pure functions of the
 * incoming {@link ScenarioState} and the layer's JSON spec; sweeps are expanded by the producer before {@code apply}
 * (a spec seen here has only scalar values).
 */
public interface ScenarioStage {
    /** Layer key {@code "stage"}, e.g. {@code cover}. Dependents prefix nothing; ids are unique across mods. */
    String id();

    /**
     * @param spec scalar layer spec; unknown keys => {@link IllegalArgumentException}
     * @throws IllegalArgumentException on a missing / invalid key, naming it
     */
    Result apply(LabContext ctx, ScenarioState in, JsonObject spec);

    /**
     * @param speedOut m/s leaving the layer along the original line; 0 when the round stopped, ricocheted or is spent
     * @param pPass    probability the round leaves the layer travelling on (perforate); terminal stages: 1
     * @param verdict  short upper-case word, e.g. PERFORATE STOP RICOCHET DETONATE WOUND
     * @param label    human text of the layer instance, e.g. {@code oak_planks 0.5 m @ 30 deg}
     * @param detail   stage-specific measures (name -> Number or String), keys identical per stage across calls
     */
    record Result(double speedOut, double pPass, String verdict, String label, Map<String, Object> detail) {
        public Result {
            detail = Map.copyOf(detail);
        }
    }
}
