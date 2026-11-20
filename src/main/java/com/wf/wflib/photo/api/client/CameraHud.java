package com.wf.wflib.photo.api.client;

import com.mojang.blaze3d.pipeline.RenderTarget;
import com.wf.gemrender.ui.Canvas;
import com.wf.wflib.core.api.WFLib;
import com.wf.wflib.ui.api.Signals;
import com.wf.wflib.ui.api.Subject;
import com.wf.wflib.ui.api.UiInstance;
import com.wf.wflib.ui.api.UiLayout;
import com.wf.wflib.ui.api.UiLayouts;
import com.wf.wflib.ui.api.WidgetDef;
import com.wf.wflib.ui.api.widget.FilmFx;
import com.wf.wflib.ui.api.widget.Point;
import com.wf.wflib.ui.api.widget.Rect;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.resources.ResourceLocation;

import static com.wf.wflib.ui.api.Ui.at;
import static com.wf.wflib.ui.api.Ui.layout;
import static com.wf.wflib.ui.api.Ui.place;
import static com.wf.wflib.ui.api.Ui.rect;
import static com.wf.wflib.ui.api.Ui.shadowed;
import static com.wf.wflib.ui.api.Ui.stack;
import static com.wf.wflib.ui.api.Ui.when;

/**
 * The camera's shooting display, in the drone view's style: shots taken and shutter state (top left), lens field of
 * view (top right), zoom (bottom left), heading / pitch (bottom right), centre cross, border with corner brackets,
 * film grain, vignette, scanlines. Layout {@code wflib:camera_hud} over {@link #WIDTH} x {@link #HEIGHT} units;
 * subject {@code camera_hud} = {@link Info}.
 */
public final class CameraHud {
    public static final int WIDTH = 256;
    public static final int HEIGHT = 144;
    public static final ResourceLocation LAYOUT = ResourceLocation.fromNamespaceAndPath(WFLib.MODID, "camera_hud");
    public static final Subject<Info> HUD = Subject.register("camera_hud", Info.class);

    private static final String GREEN = "#DD00FF00";
    private static final String DIM = "#9966FF99";
    private static final String AMBER = "#DDFFAA00";
    private static final int MARGIN = 8;
    private static final int INSET = 3;
    private static final int ARM = Math.max(10, Math.min(WIDTH, HEIGHT) / 14);

    /** What the display shows; {@code ready}: the shutter is not cooling down; {@code flash}: white veil 0..1. */
    public record Info(float fov, float zoom, float yawDegrees, float pitchDegrees, int shots, boolean ready,
                       float flash) {
        /** Reads the local player; {@code zoom} 1 = the lens at its widest. */
        public static Info of(LocalPlayer player, float fov, float zoom, int shots, float flash) {
            boolean ready = !player.getCooldowns().isOnCooldown(player.getMainHandItem().getItem());
            return new Info(fov, zoom, player.getYRot(), player.getXRot(), shots, ready, flash);
        }
    }

    private static UiInstance instance;

    private CameraHud() {
    }

    public static void register() {
        Signals.num(HUD, "fov", Info::fov);
        Signals.num(HUD, "zoom", Info::zoom);
        Signals.num(HUD, "yaw", Info::yawDegrees);
        Signals.num(HUD, "pitch", Info::pitchDegrees);
        Signals.num(HUD, "shots", Info::shots);
        Signals.bool(HUD, "ready", Info::ready);
        Signals.num(HUD, "flash", Info::flash);
        UiLayouts.register(build());
    }

    /** Draws into {@code target} (unit y = 0 on its top row). */
    public static void draw(RenderTarget target, Info info) {
        if (instance == null || !instance.current()) {
            instance = UiLayouts.get(LAYOUT).instantiate();
        }
        instance.context(subject -> subject == HUD ? info : null);
        Canvas canvas = Canvas.shared().begin(target.frameBufferId, target.width, target.height, WIDTH, HEIGHT, true);
        try {
            instance.render(canvas, 0, 0, WIDTH, HEIGHT, 0);
        } finally {
            canvas.end();
        }
    }

    private static UiLayout build() {
        String w = Integer.toString(WIDTH);
        String h = Integer.toString(HEIGHT);
        String right = Integer.toString(WIDTH - MARGIN);
        String bottom = Integer.toString(HEIGHT - MARGIN);
        return layout(LAYOUT, stack(
                new FilmFx(w, h),
                at(Integer.toString(INSET), Integer.toString(INSET), new Rect(Integer.toString(WIDTH - 2 * INSET),
                        Integer.toString(HEIGHT - 2 * INSET), DIM, 0, 1)),
                bracket(INSET, INSET, 1, 1), bracket(WIDTH - INSET, INSET, -1, 1),
                bracket(INSET, HEIGHT - INSET, 1, -1), bracket(WIDTH - INSET, HEIGHT - INSET, -1, -1),
                at(Integer.toString(MARGIN), Integer.toString(MARGIN),
                        shadowed("PHOTO {min(camera_hud.shots, 9999)|04}", GREEN)),
                at(Integer.toString(MARGIN), Integer.toString(MARGIN + 10), when("camera_hud.ready",
                        shadowed("READY", GREEN), shadowed("WAIT", AMBER))),
                place(right, Integer.toString(MARGIN), Point.TOP_RIGHT,
                        shadowed("FOV {round(camera_hud.fov)}", DIM)),
                place(Integer.toString(MARGIN), bottom, Point.BOTTOM_LEFT,
                        shadowed("ZOOM x{camera_hud.zoom|.1}", GREEN)),
                place(right, bottom, Point.BOTTOM_RIGHT, shadowed(
                        "{mod(round(camera_hud.yaw) + 180, 360)|03} / {round(-camera_hud.pitch)|+03}", GREEN)),
                at(Integer.toString(WIDTH / 2), Integer.toString(HEIGHT / 2), stack(
                        at("-8", "0", rect(6, 1, GREEN)), at("3", "0", rect(6, 1, GREEN)),
                        at("0", "-8", rect(1, 6, GREEN)), at("0", "3", rect(1, 6, GREEN)))),
                rect(w, h, "camera_hud.flash > 0.01 ? alpha(#FFFFFF, clamp(camera_hud.flash, 0, 1) * 200 / 255) : 0")))
                .absolute();
    }

    private static WidgetDef bracket(int x, int y, int dx, int dy) {
        int left = dx > 0 ? x : x - ARM;
        int top = dy > 0 ? y : y - 2;
        int vTop = dy > 0 ? y : y - ARM;
        int vLeft = dx > 0 ? x : x - 2;
        return stack(
                at(Integer.toString(left), Integer.toString(top), rect(ARM + 1, 3, GREEN)),
                at(Integer.toString(vLeft), Integer.toString(vTop), rect(3, ARM + 1, GREEN)));
    }
}
