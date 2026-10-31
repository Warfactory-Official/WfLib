package com.wf.wflib.cam.client;

import com.wf.wflib.cam.api.client.FeedPass;

public final class CamGemRender {
    private CamGemRender() {
    }

    public static void hideInFeeds() {
        com.wf.gemrender.particle.ParticleBuffer.getInstance().hideWhen(FeedPass::seesThroughSmoke);
        com.wf.gemrender.volume.VolumeBuffer.getInstance().hideWhen(FeedPass::seesThroughSmoke);
        com.wf.gemrender.medium.CameraMedium.getInstance().hideWhen(FeedPass::seesThroughSmoke);
    }
}
