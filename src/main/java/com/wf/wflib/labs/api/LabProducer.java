package com.wf.wflib.labs.api;

/** Fills one table by running real game code over a grid. */
public interface LabProducer {
    /** Namespaced, e.g. {@code wflib:round_flight}. */
    String id();

    LabTable table();

    /** Manifest grouping: {@code player}, {@code vehicle}, or {@code shared} (rounds and cover both bodies meet). */
    default String domain() {
        return "player";
    }

    /** Needs {@link LabTicks}: {@code all} skips it outside headless runs; naming it there fails. */
    default boolean ticksWorld() {
        return false;
    }

    /** Needs arguments: {@code all} skips it; naming it runs it. */
    default boolean explicitOnly() {
        return false;
    }

    /** Rows go to {@code sink}; pure work may use {@link LabContext#executor()}; must return after all rows are written. */
    void produce(LabContext ctx, LabSink sink);
}
