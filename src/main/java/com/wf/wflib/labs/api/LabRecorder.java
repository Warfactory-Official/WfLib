package com.wf.wflib.labs.api;

/** Shot recorder sink: tables written while {@code /wflib lab record} is on. */
public final class LabRecorder {
    private LabRecorder() {
    }

    public static boolean active() {
        return com.wf.wflib.labs.LabRecording.active();
    }

    /** No-op while not recording; first call per table fixes its schema (later schema must be equal). */
    public static void record(String table, LabTable schema, Object... row) {
        com.wf.wflib.labs.LabRecording.record(table, schema, row);
    }
}
