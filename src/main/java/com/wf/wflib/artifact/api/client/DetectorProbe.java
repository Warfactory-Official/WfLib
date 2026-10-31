package com.wf.wflib.artifact.api.client;

/** Test hooks into the client detector state (the bench). */
public final class DetectorProbe {
    private DetectorProbe() {
    }

    public static int heard() {
        return DetectorClient.heard().size();
    }
}
