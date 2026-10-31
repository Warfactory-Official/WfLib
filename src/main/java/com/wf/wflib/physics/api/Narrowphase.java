package com.wf.wflib.physics.api;

import com.wf.wflib.physics.rigid.NarrowNative;

public final class Narrowphase {

    private Narrowphase() {
    }

    /** Native library loaded (JNI symbols resolved, version matched); false => Java path. */
    public static boolean nativeLoaded() {
        return NarrowNative.available();
    }
}
