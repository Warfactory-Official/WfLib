package com.wf.wflib.nvg.client;

import com.wf.gemrender.light.Lights;
import com.wf.wflib.nvg.api.client.NvgClient;

public final class NvgLights {
    private NvgLights() {
    }

    public static void register() {
        Lights.irView(NvgClient::irView);
        Lights.nightSky(NvgClient::nightSky);
    }
}
