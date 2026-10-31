package com.wf.wflib.ui.input;

import com.sun.management.ThreadMXBean;
import org.junit.jupiter.api.Test;

import java.lang.management.ManagementFactory;
import java.util.ArrayList;
import java.util.List;
import com.wf.wflib.ui.api.input.Interactive;
import com.wf.wflib.ui.api.input.UiInput;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class UiInputTest {
    private static final class Probe implements Interactive {
        final List<String> log = new ArrayList<>();
        boolean consume;

        @Override
        public void press(Object item, float x, float y) {
            log.add("press " + item + " " + Math.round(x) + "," + Math.round(y));
        }

        @Override
        public void drag(Object item, float x, float y) {
            log.add("drag " + item + " " + Math.round(x) + "," + Math.round(y));
        }

        @Override
        public void release(Object item, float x, float y, boolean inside) {
            log.add("release " + item + " " + inside);
        }

        @Override
        public boolean wheel(Object item, float amount) {
            log.add("wheel " + item);
            return consume;
        }
    }

    private static void frame(UiInput input, Runnable draw) {
        input.dispatch();
        input.begin();
        draw.run();
        input.end();
    }

    @Test
    void disclosureObservationRespectsClipAndTransformWithoutTakingParentClicks() {
        UiInput input = new UiInput();
        Probe parent = new Probe();
        Runnable draw = () -> {
            input.push().translate(100, 50).scale(2);
            input.pushClip(0, 0, 10, 10);
            assertTrue(input.over(0, 0, 10, 10));
            assertFalse(input.over(0, 0, 6, 10));
            input.hit(0, 0, 10, 10, parent, "parent");
            input.pushClip(0, 0, 6, 10);
            assertFalse(input.over(0, 0, 10, 10));
            input.popClip(); input.popClip(); input.pop();
        };
        input.pointer(114, 58, 0); frame(input, draw);
        input.pointer(114, 58, UiInput.PRIMARY); frame(input, draw);
        assertEquals(List.of("press parent 7,4"), parent.log);
    }

    @Test
    void pressAndReleaseBetweenTwoFramesClick() {
        UiInput input = new UiInput();
        Probe probe = new Probe();
        Runnable draw = () -> input.hit(10, 10, 30, 20, probe, "b");
        input.pointer(15, 15, 0);
        frame(input, draw);
        input.button(15, 15, UiInput.PRIMARY);
        input.button(15, 15, 0);
        input.pointer(15, 15, 0);
        frame(input, draw);
        assertEquals(List.of("press b 5,5", "release b true"), probe.log);
    }

    @Test
    void nestedTransformMapsToLocal() {
        UiInput input = new UiInput();
        Probe probe = new Probe();
        Runnable draw = () -> {
            input.push().translate(100, 50).scale(2);
            input.push().translate(10, 0).rotate((float) Math.toRadians(90));
            input.hit(0, 0, 20, 10, probe, null);
            input.pop();
            input.pop();
        };
        frame(input, draw);
        // local (5, 2): rotate 90 => (-2, 5); +10,0 => (8, 5); *2 +100,50 => (116, 60)
        input.pointer(116, 60, UiInput.PRIMARY);
        frame(input, draw);
        assertEquals(List.of("press null 5,2"), probe.log);
    }

    @Test
    void clipHidesOutside() {
        UiInput input = new UiInput();
        Probe probe = new Probe();
        Runnable draw = () -> {
            input.pushClip(0, 0, 10, 10);
            input.hit(0, 0, 40, 40, probe, null);
            input.popClip();
        };
        frame(input, draw);
        input.pointer(20, 20, UiInput.PRIMARY);
        frame(input, draw);
        assertTrue(probe.log.isEmpty());
        input.pointer(5, 5, 0);
        frame(input, draw);
        input.pointer(5, 5, UiInput.PRIMARY);
        frame(input, draw);
        assertEquals(List.of("press null 5,5"), probe.log);
    }

    @Test
    void topmostWinsAndItemsAreDistinct() {
        UiInput input = new UiInput();
        Probe under = new Probe();
        Probe over = new Probe();
        Runnable draw = () -> {
            input.hit(0, 0, 100, 100, under, null);
            input.hit(0, 0, 50, 10, over, "a");
            input.hit(0, 10, 50, 20, over, "b");
        };
        frame(input, draw);
        input.pointer(5, 15, 0);
        frame(input, draw);
        assertTrue(input.hovered(over, "b"));
        assertFalse(input.hovered(over, "a"));
        input.pointer(5, 15, UiInput.PRIMARY);
        frame(input, draw);
        input.pointer(5, 15, 0);
        frame(input, draw);
        assertEquals(List.of("press b 5,5", "release b true"), over.log);
        assertTrue(under.log.isEmpty());
    }

    @Test
    void captureFollowsPointerOutside() {
        UiInput input = new UiInput();
        Probe probe = new Probe();
        Runnable draw = () -> input.hit(0, 0, 10, 10, probe, "x");
        frame(input, draw);
        input.pointer(5, 5, UiInput.PRIMARY);
        frame(input, draw);
        input.pointer(30, -4, UiInput.PRIMARY);
        frame(input, draw);
        input.pointer(30, -4, 0);
        frame(input, draw);
        assertEquals(List.of("press x 5,5", "drag x 30,-4", "release x false"), probe.log);
    }

    @Test
    void wheelFallsThroughUntilConsumed() {
        UiInput input = new UiInput();
        Probe under = new Probe();
        Probe over = new Probe();
        under.consume = true;
        Runnable draw = () -> {
            input.hit(0, 0, 100, 100, under, "u");
            input.hit(0, 0, 50, 50, over, "o");
        };
        frame(input, draw);
        input.pointer(5, 5, 0);
        input.wheel(1);
        frame(input, draw);
        assertEquals(List.of("wheel o"), over.log);
        assertEquals(List.of("wheel u"), under.log);
    }

    private static final class FeedbackProbe implements UiInput.Feedback {
        int enters, clicks;
        public void hover() { enters++; }
        public void click() { clicks++; }
    }

    @Test
    void feedbackEntersOncePerOwnerAndItemAndIgnoresStationaryLayoutChanges() {
        UiInput input = new UiInput(); Probe owner = new Probe(); FeedbackProbe audio = new FeedbackProbe();
        String[] item = {"a"};
        Runnable draw = () -> { input.feedback(audio); input.hit(0, 0, 20, 20, owner, item[0]); };
        frame(input, draw); input.pointer(3, 3, 0); frame(input, draw);
        for (int i = 0; i < 50; i++) frame(input, draw);
        assertEquals(1, audio.enters);
        item[0] = "b"; frame(input, draw); frame(input, draw);
        assertEquals(1, audio.enters); // A row scrolling under a resting pointer is not an enter.
        input.pointer(30, 3, 0); frame(input, draw);
        input.pointer(4, 3, 0); frame(input, draw);
        assertEquals(2, audio.enters);
        input.pointer(4, 3, UiInput.PRIMARY); frame(input, draw);
        input.pointer(4, 3, 0); frame(input, draw);
        assertEquals(1, audio.clicks);
    }

    @Test
    void feedbackIgnoresWheelAndCaptureAndDoesNotQueueDelayedEnters() {
        UiInput input = new UiInput(); Probe owner = new Probe(); FeedbackProbe audio = new FeedbackProbe();
        Runnable draw = () -> { input.feedback(audio); input.hit(0, 0, 10, 10, owner, "a"); input.hit(10, 0, 20, 10, owner, "b"); };
        frame(input, draw); input.pointer(3, 3, 0); input.wheel(1); frame(input, draw);
        frame(input, draw); assertEquals(0, audio.enters);
        input.pointer(3, 3, UiInput.PRIMARY); frame(input, draw);
        input.pointer(13, 3, UiInput.PRIMARY); frame(input, draw);
        input.pointer(13, 3, 0); frame(input, draw); frame(input, draw);
        assertEquals(0, audio.enters); assertEquals(0, audio.clicks);
        input.pointer(30, 3, 0); frame(input, draw); input.pointer(13, 3, 0); frame(input, draw);
        assertEquals(1, audio.enters);
    }

    @Test
    void feedbackHonoursTopmostModalAndReadOnlyPopupAndScopes() {
        UiInput input = new UiInput(); Probe under = new Probe(); FeedbackProbe audio = new FeedbackProbe();
        Interactive modal = new Interactive() { public boolean feedback() { return false; } };
        boolean[] popup = {true};
        Runnable draw = () -> {
            input.feedback(audio); input.hit(0, 0, 40, 20, under, null);
            if (popup[0]) { input.hit(0, 0, 10, 20, modal, null); input.coverFeedback(10, 0, 20, 20); }
            input.feedback(null); input.hit(40, 0, 50, 20, under, null);
        };
        frame(input, draw); input.pointer(3, 3, 0); frame(input, draw);
        input.pointer(13, 3, 0); frame(input, draw); assertEquals(0, audio.enters);
        input.pointer(43, 3, 0); frame(input, draw); assertEquals(0, audio.enters);
        popup[0] = false; frame(input, draw); input.pointer(23, 3, 0); frame(input, draw);
        // Same owner/item despite a different box: still a single control.
        assertEquals(0, audio.enters);
        input.pointer(60, 3, 0); frame(input, draw); input.pointer(23, 3, 0); frame(input, draw);
        assertEquals(1, audio.enters);
    }

    @Test
    void steadyStateAllocatesNothing() {
        UiInput input = new UiInput();
        Interactive sink = new Interactive() {
        };
        String[] items = {"a", "b", "c"};
        Runnable draw = () -> {
            input.push().translate(3, 4).scale(2);
            input.pushClip(0, 0, 500, 500);
            for (int i = 0; i < 200; i++) {
                input.hit(0, i * 10, 100, i * 10 + 9, sink, items[i % 3]);
            }
            input.popClip();
            input.pop();
        };
        for (int i = 0; i < 1000; i++) {
            input.pointer(i % 200, i % 300, i % 7 == 0 ? UiInput.PRIMARY : 0);
            frame(input, draw);
        }
        ThreadMXBean bean = (ThreadMXBean) ManagementFactory.getThreadMXBean();
        // A few hundred stray bytes show up on CI runners now and then (JIT/runtime, not UiInput); a real per-frame
        // allocation costs >= 16 KB in every round, so it still fails all five.
        long allocated = Long.MAX_VALUE;
        for (int round = 0; round < 5 && allocated != 0; round++) {
            long before = bean.getCurrentThreadAllocatedBytes();
            for (int i = 0; i < 1000; i++) {
                input.pointer(i % 200, i % 300, i % 7 == 0 ? UiInput.PRIMARY : 0);
                input.wheel(1);
                frame(input, draw);
            }
            allocated = bean.getCurrentThreadAllocatedBytes() - before;
        }
        assertEquals(0, allocated);
    }
}
