package com.wf.wflib.labs.api;

import java.util.Map;
import java.util.Set;

/** What a dependent contributes to a run's provenance (manifest {@code content}). */
public interface LabContent {
    /** Namespaced owner id. */
    String id();

    /** Content packs loaded (pack id -> version or SHA-256); empty if none. */
    default Map<String, String> packs() {
        return Map.of();
    }

    /** Names of {@code COMMON}/{@code SERVER} config files whose effective values affect ballistics. */
    default Set<String> configFiles() {
        return Set.of();
    }

    /** True for ids that are dev or test fixtures, never shipped. */
    default boolean dev(String contentId) {
        return false;
    }
}
