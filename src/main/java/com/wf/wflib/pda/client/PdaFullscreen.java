package com.wf.wflib.pda.client;

import com.wf.wflib.cam.api.client.CameraFeedCache;
import com.wf.wflib.cam.api.client.CameraLink;
import com.wf.wflib.cam.api.client.CameraTarget;
import com.wf.wflib.cam.api.client.DroneCamHud;
import com.wf.wflib.ui.api.Signals;
import com.wf.wflib.ui.api.Subject;
import com.wf.wflib.ui.api.UiHud;
import com.wf.wflib.ui.api.UiInstance;
import com.wf.wflib.ui.api.UiLayout;
import com.wf.wflib.ui.api.widget.Feed;
import com.wf.wflib.ui.api.widget.Point;
import com.wf.wflib.ui.api.widget.Sized;
import net.minecraft.resources.ResourceLocation;
import com.wf.wflib.pda.api.client.PdaClient;
import com.wf.wflib.pda.api.client.PdaShell;
import com.wf.wflib.pda.api.client.FullscreenControl;
import com.wf.wflib.core.api.WFLib;

import static com.wf.wflib.ui.api.Ui.at;
import static com.wf.wflib.ui.api.Ui.layout;
import static com.wf.wflib.ui.api.Ui.place;
import static com.wf.wflib.ui.api.Ui.rect;
import static com.wf.wflib.ui.api.Ui.shadowed;
import static com.wf.wflib.ui.api.Ui.stack;
import static com.wf.wflib.ui.api.Ui.when;

/**
 * A PDA feed on the whole game screen ({@link PdaClient#fullscreen}): letterboxed, mouse pans the linked gimbal,
 * wheel zooms; attack takes control of a connectable body; use / Esc returns to the PDA.
 */
public final class PdaFullscreen {
    static final Subject<PdaFullscreen> FULL = Subject.register("pda_full", PdaFullscreen.class, PdaFullscreen::active);
    private static final ResourceLocation LAYOUT = ResourceLocation.fromNamespaceAndPath(WFLib.MODID, "pda_full");
    private static final PdaFullscreen INSTANCE = new PdaFullscreen();
    /** {@code LocalPlayer.turn} units -> degrees at zoom 1 (= TV link). */
    static final double TURN_SCALE = 0.15;

    private static UiInstance instance;

    private PdaFullscreen() {
    }

    private static PdaFullscreen active() {
        return PdaClient.fullscreenFeed() == 0 ? null : INSTANCE;
    }

    public static void register() {
        Signals.num(FULL, "feed", f -> PdaClient.fullscreenFeed());
        Signals.bool(FULL, "picture", f -> CameraFeedCache.drawable(PdaClient.fullscreenFeed()));
        Signals.num(FULL, "zoom", f -> CameraLink.zoom());
        Signals.bool(FULL, "control", f -> FullscreenControl.HOOK.get().controllable(PdaClient.fullscreenFeed()));
        double aspect = CameraTarget.ASPECT;
        String w = "min(ui.width, round(ui.height * " + aspect + "))";
        String h = "round(" + w + " / " + aspect + ")";
        String x = "floor((ui.width - " + w + ") / 2)";
        String y = "floor((ui.height - " + h + ") / 2)";
        UiLayout full = layout(LAYOUT, when("pda_full.present", stack(
                rect("ui.width", "ui.height", "#ff000000"),
                at(x, y, new Sized(w, h, Point.TOP_LEFT, false, new Feed("pda_full.feed", w, h))),
                when("!pda_full.picture && !drone_cam.present", place("ui.width / 2", "ui.height / 2 - 4", Point.TOP,
                        shadowed("NO SIGNAL", PdaShell.DIM))),
                place("ui.width / 2", "ui.height - 14", Point.TOP, when("pda_full.control", shadowed(
                        "x{pda_full.zoom|.1}  scroll zoom  [{key:key.attack}] take control  [{key:key.use}] / Esc",
                        PdaShell.TEXT), shadowed("x{pda_full.zoom|.1}  scroll zoom  [{key:key.use}] / Esc",
                        PdaShell.TEXT))))));
        UiHud.addSource(UiHud.Layer.MAIN, net.minecraft.resources.ResourceLocation.fromNamespaceAndPath(com.wf.wflib.core.api.WFLib.MODID, "pda_fullscreen"), 0, out -> {
            int feed = PdaClient.fullscreenFeed();
            DroneCamHud.watch(feed, feed == 0 ? null : FullscreenControl.HOOK.get().name(feed));
            if (feed == 0) {
                return;
            }
            if (instance == null) {
                instance = full.instantiate();
            }
            out.add(instance);
        });
        DroneCamHud.addToHud();
    }

    /** Attack in full screen: take manual control of the body behind the feed. */
    public static void control() {
        FullscreenControl.HOOK.get().control(PdaClient.fullscreenFeed());
    }

    public static void steer(double yaw, double pitch) {
        double gain = TURN_SCALE / Math.max(1.0, CameraLink.zoom());
        CameraLink.pan(yaw * gain, pitch * gain);
    }
}
