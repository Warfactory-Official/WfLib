package com.wf.wflib.ui.api.input;

import com.wf.wflib.core.api.OrderedRegistry;

/** Claims a key press when no UI text field has focus; first claimant by priority wins. */
@FunctionalInterface
public interface KeyInterceptors {
    OrderedRegistry<KeyInterceptors> INTERCEPTORS = OrderedRegistry.create("ui", "ui.key_interceptors");

    /** @return true if the press was consumed. */
    boolean key(int key, int scan);

    static boolean intercept(int key, int scan) {
        for (KeyInterceptors interceptor : INTERCEPTORS.values()) {
            if (interceptor.key(key, scan)) {
                return true;
            }
        }
        return false;
    }
}
