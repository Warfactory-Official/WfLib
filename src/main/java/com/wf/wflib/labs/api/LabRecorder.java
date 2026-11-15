package com.wf.wflib.labs.api;

import com.wf.wflib.labs.LabRecording;

/** Shot recorder sink: tables written while {@code /wflib lab record} is on. */
public final class LabRecorder {
    private LabRecorder() {
    }

    public static boolean active() {
        return LabRecording.active();
    }

    /** No-op while not recording; first call per table fixes its schema (later schema must be equal). */
    public static void record(String table, LabTable schema, Object... row) {
        LabRecording.record(table, schema, row);
    }
}
