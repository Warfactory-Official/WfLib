package com.wf.wflib.audio.client;

import com.wf.wflib.audio.SoundAtPacket;
import com.wf.wflib.audio.api.client.Sounds;
import net.minecraft.client.Minecraft;

public final class SoundAtClient {
    private SoundAtClient() {
    }

    public static void handle(SoundAtPacket p) {
        if (Minecraft.getInstance().level != null) {
            Sounds.at(p.layers(), p.x(), p.y(), p.z(), p.category(), p.seed());
        }
    }
}
