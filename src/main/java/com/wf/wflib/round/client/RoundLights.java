package com.wf.wflib.round.client;

import com.wf.gemrender.light.LightSink;
import com.wf.gemrender.light.Lights;
import com.wf.wflib.round.api.client.ClientRounds;

/** Rounds with a preset {@code light} (signal / illumination stars) -> shadowed point lights in flight. */
public final class RoundLights {
    /** No tracer colour => this. */
    static final int WHITE = 0xFFF0D0;

    private RoundLights() {
    }

    public static void register() {
        Lights.register(RoundLights::collect);
    }

    private static void collect(LightSink sink, float partialTick) {
        for (ClientRounds.Round r : ClientRounds.live()) {
            float range = r.preset().light();
            if (range > 0) {
                int rgb = r.preset().tracerColor();
                // E ~ 2 at half range (WFLights.blast).
                sink.point(r.ix(partialTick), r.iy(partialTick), r.iz(partialTick), range, rgb == 0 ? WHITE : rgb,
                        range * range * 0.5F, true, false, 1);
            }
        }
    }
}
