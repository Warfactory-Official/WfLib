package com.wf.wflib.map.client.journeymap;

import com.wf.wflib.map.api.client.WFMaps;
import journeymap.api.v2.client.IClientAPI;
import journeymap.api.v2.client.IClientPlugin;
import journeymap.api.v2.common.JourneyMapPlugin;
import com.wf.wflib.core.api.WFLib;

/**
 * The mod's only JM plugin: {@code PluginHelper} keys client plugins by {@code getModId()}, a second
 * annotated class silently replaces the first (still logged as found). Tell: {@code journeymap.log}
 * {@code Initialized IJourneyMapPlugin as:}.
 */
@JourneyMapPlugin(apiVersion = IClientAPI.API_VERSION)
public final class WflibJourneyMapPlugin implements IClientPlugin {

    @Override
    public String getModId() {
        return WFLib.MODID;
    }

    @Override
    public void initialize(IClientAPI clientApi) {
        new JourneyMapTools().attach();
        WFMaps.addHost(new JourneyMapHost(clientApi));
    }
}
