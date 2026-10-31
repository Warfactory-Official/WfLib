package com.wf.wflib.nvg.client;

import com.wf.wflib.nvg.api.client.NvgClient;

public final class NvgLights {
    private NvgLights() {
    }

    public static void register() {
        com.wf.gemrender.light.Lights.irView(NvgClient::irView);
        com.wf.gemrender.light.Lights.nightSky(NvgClient::nightSky);
    }
}
