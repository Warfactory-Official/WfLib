package com.wf.wflib.cam.client;

import com.wf.gemrender.medium.CameraMedium;
import com.wf.gemrender.particle.ParticleBuffer;
import com.wf.gemrender.volume.VolumeBuffer;
import com.wf.wflib.cam.api.client.FeedPass;

public final class CamGemRender {
    private CamGemRender() {
    }

    public static void hideInFeeds() {
        ParticleBuffer.getInstance().hideWhen(FeedPass::seesThroughSmoke);
        VolumeBuffer.getInstance().hideWhen(FeedPass::seesThroughSmoke);
        CameraMedium.getInstance().hideWhen(FeedPass::seesThroughSmoke);
    }
}
