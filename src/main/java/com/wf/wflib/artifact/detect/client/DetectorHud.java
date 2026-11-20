package com.wf.wflib.artifact.detect.client;

import com.wf.wflib.artifact.api.DetectorTier;
import com.wf.wflib.artifact.api.client.DetectorClient;
import com.wf.wflib.core.api.WFLib;
import com.wf.wflib.ui.api.Signals;
import com.wf.wflib.ui.api.Subject;
import com.wf.wflib.ui.api.UiHud;
import com.wf.wflib.ui.api.UiInstance;
import com.wf.wflib.ui.api.UiLayouts;
import com.wf.wflib.ui.api.WidgetDef;
import com.wf.wflib.ui.api.widget.Point;
import com.wf.wflib.ui.api.widget.Scatter;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;
import net.neoforged.neoforge.client.gui.VanillaGuiLayers;

import java.util.ArrayList;
import java.util.List;

import static com.wf.wflib.ui.api.Ui.at;
import static com.wf.wflib.ui.api.Ui.layout;
import static com.wf.wflib.ui.api.Ui.place;
import static com.wf.wflib.ui.api.Ui.rect;
import static com.wf.wflib.ui.api.Ui.sized;
import static com.wf.wflib.ui.api.Ui.stack;
import static com.wf.wflib.ui.api.Ui.text;
import static com.wf.wflib.ui.api.Ui.when;

/**
 * The detector's face, right of the hotbar while one is out: the Echo's LED blinking with its beeps, the
 * Bear's ring of LEDs lit toward the nearest artefact, the Veles' screen of blips. shortcut: a HUD stand-in for the
 * device's own face; moves onto the model when a viewmodel draws the detector in hand.
 */
public final class DetectorHud {
    private static final ResourceLocation LAYOUT = ResourceLocation.fromNamespaceAndPath(WFLib.MODID, "detector");
    private static final int SIZE = 56;
    private static final int C = SIZE / 2;

    public record Blip(int x, int y) {
    }

    private static final DetectorHud INSTANCE = new DetectorHud();
    private static final Subject<DetectorHud> FACE = Subject.register("detector", DetectorHud.class, DetectorHud::visible);
    private static final Subject<Blip> BLIP = Subject.register("detector_blip", Blip.class);
    private static final List<Blip> blips = new ArrayList<>();
    private static long blipsFrame = -1;
    private static UiInstance instance;

    private DetectorHud() {
    }

    private static DetectorHud visible() {
        return DetectorClient.held() == null || Minecraft.getInstance().player == null || DetectorClient.faceOnDevice() ? null : INSTANCE;
    }

    public static void registerLayer(RegisterGuiLayersEvent event) {
        UiHud.layer(event, LAYOUT, UiHud.Position.ABOVE, VanillaGuiLayers.HOTBAR, out -> {
            if (visible() == null) {
                return;
            }
            if (instance == null || !instance.current()) {
                instance = UiLayouts.get(LAYOUT).instantiate();
            }
            out.add(instance);
        });
    }

    /** Eight LEDs round the face: the index toward the nearest artefact, -1 when none is heard. */
    private static int lit() {
        if (DetectorClient.heard().isEmpty()) {
            return -1;
        }
        Vec3 to = DetectorClient.heard().get(0).subtract(Minecraft.getInstance().player.position());
        double bearing = Math.toDegrees(Math.atan2(-to.x, to.z)) - Minecraft.getInstance().player.getYRot();
        return Math.floorMod(Math.round((float) (bearing / 45.0D)), 8);
    }

    /** Up the screen is ahead; every artefact heard a blip, scaled to the detector's reach. */
    private static List<Blip> blips() {
        long frame = UiHud.frame();
        if (frame != blipsFrame) {
            blipsFrame = frame;
            blips.clear();
            var player = Minecraft.getInstance().player;
            double yaw = Math.toRadians(player.getYRot());
            double k = (SIZE / 2.0D - 3.0D) / DetectorClient.held().radius;
            for (Vec3 p : DetectorClient.heard()) {
                Vec3 to = p.subtract(player.position());
                double right = -to.x * Math.cos(yaw) - to.z * Math.sin(yaw);
                double ahead = -to.x * Math.sin(yaw) + to.z * Math.cos(yaw);
                blips.add(new Blip((int) Math.round(Mth.clamp(-right * k, -SIZE / 2.0D + 2, SIZE / 2.0D - 2)),
                        -(int) Math.round(Mth.clamp(ahead * k, -SIZE / 2.0D + 2, SIZE / 2.0D - 2))));
            }
        }
        return blips;
    }

    public static void register() {
        Signals.choice(FACE, "display", DetectorTier.Display.class, f -> DetectorClient.held().display);
        Signals.num(FACE, "flash", f -> DetectorClient.flash());
        Signals.num(FACE, "lit", f -> lit());
        Signals.text(FACE, "name", 16, f -> DetectorClient.held().name());
        Signals.items(FACE, "blips", f -> blips(), BLIP);
        Signals.num(BLIP, "x", Blip::x);
        Signals.num(BLIP, "y", Blip::y);
        WidgetDef[] leds = new WidgetDef[8];
        for (int i = 0; i < 8; i++) {
            double a = Math.toRadians(i * 45.0D);
            int lx = C + (int) Math.round(Math.sin(a) * -18.0D);
            int ly = C + (int) Math.round(-Math.cos(a) * 18.0D);
            leds[i] = at(Integer.toString(lx - 3), Integer.toString(ly - 3), rect("6", "6",
                    "detector.lit == " + i + " ? rgb(0, 140 + 115 * detector.flash, 32) : #FF203020"));
        }
        WidgetDef direction = stack(stack(leds), at(Integer.toString(C - 2), Integer.toString(C - 2), rect(4, 4, "#FF506050")));
        WidgetDef screen = stack(at(Integer.toString(C - 1), Integer.toString(C - 1), rect(2, 2, "#FFB0FFB0")),
                new Scatter("detector.blips", Integer.toString(C) + " + detector_blip.x", Integer.toString(C) + " + detector_blip.y",
                        Point.CENTER, rect("4", "4", "rgb(0, 120 + 135 * detector.flash, 48)")));
        WidgetDef face = sized(SIZE, SIZE, stack(
                at("-2", "-2", rect(SIZE + 4, SIZE + 4, "#C0101010")),
                rect(SIZE, SIZE, "#C0202422"),
                when("detector.display == detector.display.led",
                        at(Integer.toString(C - 5), Integer.toString(C - 5),
                                rect("10", "10", "rgb(80 + 175 * detector.flash, 20 + 30 * detector.flash, 0)"))),
                when("detector.display == detector.display.direction", direction),
                when("detector.display == detector.display.screen", screen),
                at("2", Integer.toString(SIZE - 9), text("{detector.name}", "#FF90A090"))));
        UiLayouts.register(layout(LAYOUT, when("detector.present",
                place("ui.width / 2 + 101", "ui.height - " + (SIZE + 4), Point.TOP_LEFT, face))).absolute());
    }
}
