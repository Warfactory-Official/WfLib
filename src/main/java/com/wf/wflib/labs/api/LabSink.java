package com.wf.wflib.labs.api;

/** Row receiver of one table; thread-safe. */
public interface LabSink {
    /** @param values one per column, schema order; length mismatch throws */
    void row(Object... values);
}
