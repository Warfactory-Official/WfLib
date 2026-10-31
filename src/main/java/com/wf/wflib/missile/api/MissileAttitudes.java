package com.wf.wflib.missile.api;

import com.wf.wflib.core.api.WFRegistry;
import net.minecraft.resources.ResourceLocation;
import com.wf.wflib.core.api.WFLib;
import com.wf.wflib.missile.attitude.NoseToVelocityAttitude;

public final class MissileAttitudes {

    public static final String DEFAULT_ID = "missile";

    private static final WFRegistry<MissileAttitude> BY_ID = WFRegistry.create("missile", "missile attitudes");

    private MissileAttitudes() {
    }

    public static ResourceLocation rl(String path) {
        return ResourceLocation.fromNamespaceAndPath(WFLib.MODID, path);
    }

    public static void register(ResourceLocation id, MissileAttitude attitude) {
        BY_ID.register(id, attitude);
    }

    /** Bare path under the wflib namespace; unknown => nose-to-velocity. */
    public static MissileAttitude get(String id) {
        ResourceLocation key = id.indexOf(':') >= 0 ? ResourceLocation.tryParse(id) : rl(id);
        MissileAttitude attitude = key == null ? null : BY_ID.get(key);
        return attitude != null ? attitude : NoseToVelocityAttitude.INSTANCE;
    }
}
