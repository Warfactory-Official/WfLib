package com.wf.wflib.ui.input;

import com.wf.wflib.ui.api.input.Interactive;
import com.wf.wflib.ui.api.input.UiInput;
import com.wf.wflib.ui.api.input.UiTooltip;
import net.minecraft.network.chat.Component;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class TooltipInputTest {
    private static final UiTooltip ECHO = item -> List.of(Component.literal(String.valueOf(item)));

    private static String shown(UiInput input) {
        List<Component> lines = input.tooltip();
        return lines == null ? null : lines.get(0).getString();
    }

    private static void frame(UiInput input, Runnable draw) {
        input.dispatch();
        input.begin();
        draw.run();
        input.end();
    }

    @Test
    void topmostOfferWinsAndPersistsUntilThePointerLeaves() {
        UiInput input = new UiInput();
        Interactive button = new Interactive() { };
        Runnable draw = () -> {
            input.offerTooltip(0, 0, 100, 100, ECHO, "panel");
            input.hit(10, 10, 30, 30, button, null);
            input.offerTooltip(10, 10, 30, 30, ECHO, "button");
        };
        input.pointer(20, 20, 0);
        frame(input, draw);
        assertEquals("button", shown(input), "inner (later) offer is topmost");
        assertEquals("button", shown(input), "published until the next frame ends");
        input.pointer(50, 50, 0);
        frame(input, draw);
        assertEquals("panel", shown(input));
        input.pointer(150, 50, 0);
        frame(input, draw);
        assertNull(shown(input));
    }

    @Test
    void offersFollowTransformAndClip() {
        UiInput input = new UiInput();
        input.pointer(105, 55, 0);
        frame(input, () -> {
            input.push().translate(100, 50).scale(2);
            input.pushClip(0, 0, 2, 10);
            input.offerTooltip(0, 0, 10, 10, ECHO, "clipped");
            input.popClip();
            input.offerTooltip(0, 0, 10, 10, ECHO, "scaled");
            input.pop();
        });
        assertEquals("scaled", shown(input), "x 105 = local 2.5: outside the 2-wide clip, inside the box");
        input.reset();
        assertNull(input.tooltip());
    }
}
