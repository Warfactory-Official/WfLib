package com.wf.wflib.fx;

/** Server thread: wflib FX packets + {@link com.wf.wflib.audio.WfAudio} dropped inside {@link #run}. */
public final class FxMute {
    private static int depth;

    private FxMute() {
    }

    public static boolean active() {
        return depth > 0;
    }

    public static void run(Runnable action) {
        depth++;
        try {
            action.run();
        } finally {
            depth--;
        }
    }
}
