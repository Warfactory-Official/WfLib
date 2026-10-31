package com.wf.wflib.mixin.testkit;

import net.minecraft.gametest.framework.GameTestServer;
import org.spongepowered.asm.mixin.Mixin;

/** Shutdown save of the throwaway test world: DSYNC ~10 ms/chunk => minutes of exit hang. */
@Mixin(GameTestServer.class)
public abstract class MixinGameTestServer {

    public boolean forceSynchronousWrites() {
        return false;
    }
}
