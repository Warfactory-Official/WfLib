package com.wf.wflib.pda.api.client;

import com.wf.wflib.core.api.Hook;
import org.jetbrains.annotations.Nullable;

/** Body behind a full-screen PDA feed that the viewer may take over. */
public interface FullscreenControl {
    Hook<FullscreenControl> HOOK = Hook.of("pda.fullscreen_control", new FullscreenControl() {
        @Override
        public boolean controllable(int feedId) {
            return false;
        }

        @Override
        public void control(int feedId) {
        }

        @Override
        @Nullable
        public String name(int feedId) {
            return null;
        }
    });

    boolean controllable(int feedId);

    void control(int feedId);

    /** null = no linked body shows {@code feedId}. */
    @Nullable
    String name(int feedId);
}
