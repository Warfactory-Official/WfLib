package com.wf.wflib.fire.client;

import com.wf.wflib.fx.api.WFDynamicLight;
import com.wf.wflib.fx.api.WFLights;
import com.wf.wflib.fire.api.FireLingeringEntity;

public final class FireClientFX {

    private static final int FIRE_LIGHT = 15;

    private FireClientFX() {
    }

    public static void tick(FireLingeringEntity fire) {
        if (!WFLights.fire(fire)) {
            WFDynamicLight.add(fire.getId(), WFLights.lightPos(fire), FIRE_LIGHT);
        }
    }
}
