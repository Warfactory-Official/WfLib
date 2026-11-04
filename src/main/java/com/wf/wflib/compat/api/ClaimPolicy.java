package com.wf.wflib.compat.api;

import com.wf.wflib.compat.CompatConfig;

/** Config switches for whether an effect honours claims. */
public final class ClaimPolicy {
    private ClaimPolicy() {
    }

    public static boolean radiationRespectsClaims() {
        return CompatConfig.radiationRespectsClaims();
    }

    public static boolean empRespectsClaims() {
        return CompatConfig.empRespectsClaims();
    }
}
