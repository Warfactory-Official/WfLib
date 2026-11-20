package com.wf.wflib.pda;

import com.wf.wflib.pda.api.client.PdaShell;

/** PdaShell registers signals that cannot be registered twice, and several app layout tests share one JVM. */
public final class PdaShellOnce {
    private static boolean done;

    private PdaShellOnce() {
    }

    public static synchronized void ensure() {
        if (!done) {
            PdaShell.register();
            done = true;
        }
    }
}
