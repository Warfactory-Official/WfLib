package com.wf.wflib.packs.api;

/** {@code mod}: providing mod of a jar pack, "" => packs dir. */
public record PackEntry(String id, String sha256, String mod) {
    public static PackEntry of(Pack p) {
        return new PackEntry(p.id(), p.sha256(), p.mod());
    }
}
