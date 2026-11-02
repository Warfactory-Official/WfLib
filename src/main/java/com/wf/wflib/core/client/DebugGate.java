package com.wf.wflib.core.client;

import net.minecraft.client.Minecraft;
import net.neoforged.fml.loading.FMLEnvironment;

/** Debug overlays that reveal hidden state: dev run, own integrated server, or op on a remote server. */
public final class DebugGate {

    private DebugGate() {
    }

    public static boolean allowed() {
        if (!FMLEnvironment.production) {
            return true;
        }
        Minecraft mc = Minecraft.getInstance();
        return mc.getSingleplayerServer() != null || mc.player != null && mc.player.hasPermissions(2);
    }
}
