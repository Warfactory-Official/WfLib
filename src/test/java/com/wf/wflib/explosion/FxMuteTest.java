package com.wf.wflib.explosion;

import com.wf.wflib.explosion.api.FxMute;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FxMuteTest {

    @Test
    void nestedRunsUnwindEvenOnThrow() {
        assertFalse(FxMute.active());
        FxMute.run(() -> {
            assertTrue(FxMute.active());
            FxMute.run(() -> assertTrue(FxMute.active()));
            assertTrue(FxMute.active());
        });
        assertFalse(FxMute.active());
        try {
            FxMute.run(() -> {
                throw new IllegalStateException();
            });
        } catch (IllegalStateException expected) {
        }
        assertFalse(FxMute.active());
    }

    @Test
    void confinedToThread() throws Exception {
        AtomicBoolean other = new AtomicBoolean(true);
        FxMute.run(() -> {
            Thread t = new Thread(() -> other.set(FxMute.active()));
            t.start();
            try {
                t.join();
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
        });
        assertFalse(other.get());
    }
}
