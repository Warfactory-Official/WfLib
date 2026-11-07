package com.wf.wflib.labs.api;

import net.minecraft.server.level.ServerLevel;

import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.function.Predicate;

/** Grid and services of one lab run. */
public interface LabContext {
    /** Overworld; entity / block game paths that need a level. Server thread owns it: use off-thread only for pure work. */
    ServerLevel level();

    /** Distances, m. */
    double[] ranges();

    /** Round id filter ({@code rounds=} argument); true for every round when unset. */
    Predicate<String> rounds();

    /** Launchers of selected rounds: registered ones plus the built-in default per live round, id order. */
    List<LabLauncher> launchers();

    /** {@code dev=1}: rows from dev / test content are produced too (flagged {@code content_origin=dev}); default off. */
    boolean includeDev();

    /** Directory of the current run; side files go here. Memory contexts: a fresh temp directory. */
    java.nio.file.Path outputDir();

    /** Fixed seed of every dice roll in the run. */
    long seed();

    String param(String key, String fallback);

    double paramNum(String key, double fallback);

    ExecutorService executor();

    /** Memory-only context (tests, tools): no executor sharing, default grid, {@code params} as given. */
    static LabContext of(ServerLevel level, double[] ranges, Predicate<String> rounds,
                         java.util.Map<String, String> params) {
        return new com.wf.wflib.labs.LabRun.Context(level, ranges, rounds, params, null, null);
    }
}
