package com.wf.wflib.soldier.client;

import com.wf.wflib.core.api.WFLib;
import com.wf.wflib.ui.api.ScreenProjection;
import com.wf.wflib.ui.api.Signals;
import com.wf.wflib.ui.api.Subject;
import com.wf.wflib.ui.api.UiHud;
import com.wf.wflib.ui.api.UiInstance;
import com.wf.wflib.ui.api.UiLayouts;
import com.wf.wflib.ui.api.widget.Box;
import com.wf.wflib.ui.api.widget.Point;
import com.wf.wflib.ui.api.widget.Scatter;
import com.wf.wflib.ui.api.widget.Transform;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import static com.wf.wflib.ui.api.Ui.layout;
import static com.wf.wflib.ui.api.Ui.text;

/** The order's name over each soldier that took it, projected onto the HUD, sized as a 0.03-block-per-pixel world billboard, brief, see-through. */
final class OrderLabels {
    private static final ResourceLocation LAYOUT = ResourceLocation.fromNamespaceAndPath(WFLib.MODID, "order_labels");
    private static final OrderLabels INSTANCE = new OrderLabels();
    private static final Subject<OrderLabels> LABELS = Subject.register("order_labels", OrderLabels.class, () -> INSTANCE);
    private static final Subject<Label> LABEL = Subject.register("order_label", Label.class);
    private static final float BLOCK_PER_PIXEL = 0.03f;
    private static final float SHOWN = 60;
    private static final float FADE = 20;

    record Label(float x, float y, float scale, float alpha, String text) {
    }

    private static final List<Label> labels = new ArrayList<>();
    private static final float[] projected = new float[2];
    private static long labelsFrame = -1;
    private static UiInstance instance;

    private OrderLabels() {
    }

    static void register() {
        Signals.items(LABELS, "list", l -> labels(), LABEL);
        Signals.num(LABEL, "x", Label::x);
        Signals.num(LABEL, "y", Label::y);
        Signals.num(LABEL, "scale", Label::scale);
        Signals.num(LABEL, "alpha", Label::alpha);
        Signals.text(LABEL, "text", 64, Label::text);
        var tag = new Transform("0", "0", "0", "order_label.scale", Point.TOP,
                new Box("rgba(0, 0, 0, order_label.alpha / 3)", "0", 0, 0, 0, 0.5f, 0, 0, Point.TOP_LEFT,
                        text("{order_label.text}", "rgba(255, 255, 255, order_label.alpha)")));
        UiLayouts.register(layout(LAYOUT, new Scatter("order_labels.list", "order_label.x", "order_label.y", Point.TOP, tag))
                .absolute());
        UiHud.addSource(UiHud.Layer.MAIN, LAYOUT, 0, out -> {
            if (SquadClient.markers().isEmpty()) {
                return;
            }
            if (instance == null || !instance.current()) {
                instance = UiLayouts.get(LAYOUT).instantiate();
            }
            out.add(instance);
        });
    }

    private static List<Label> labels() {
        long frame = UiHud.frame();
        if (frame == labelsFrame) {
            return labels;
        }
        labelsFrame = frame;
        labels.clear();
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) {
            return labels;
        }
        float partial = mc.getTimer().getGameTimeDeltaPartialTick(false);
        Vec3 camera = mc.gameRenderer.getMainCamera().getPosition();
        double pxPerBlockAtOne = mc.getWindow().getGuiScaledHeight() / (2 * Math.tan(Math.toRadians(mc.options.fov().get()) / 2));
        long now = mc.level.getGameTime();
        for (var marker : SquadClient.markers()) {
            float age = now + partial - marker.born();
            if (age > SHOWN) {
                continue;
            }
            float alpha = Math.min(1f, (SHOWN - age) / FADE);
            if (alpha * 255 < 8) {
                continue;
            }
            String text = marker.echo().label().toUpperCase(Locale.ROOT);
            for (int id : marker.echo().entities()) {
                var entity = mc.level.getEntity(id);
                if (entity == null) {
                    continue;
                }
                Vec3 at = entity.getPosition(partial).add(0, entity.getBbHeight() + 0.9, 0);
                if (!ScreenProjection.project(at, projected)) {
                    continue;
                }
                double distance = at.distanceTo(camera);
                labels.add(new Label(projected[0], projected[1], (float) (BLOCK_PER_PIXEL * pxPerBlockAtOne / distance), alpha * 255, text));
            }
        }
        return labels;
    }
}
