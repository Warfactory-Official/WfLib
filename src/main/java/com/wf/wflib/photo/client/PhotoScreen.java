package com.wf.wflib.photo.client;

import com.wf.wflib.core.api.WFLib;
import com.wf.wflib.photo.PhotoActionPacket;
import com.wf.wflib.photo.PhotoListPacket;
import com.wf.wflib.ui.api.BorrowedTexture;
import com.wf.wflib.ui.api.Signals;
import com.wf.wflib.ui.api.Subject;
import com.wf.wflib.ui.api.UiLayout;
import com.wf.wflib.ui.api.UiLayouts;
import com.wf.wflib.ui.api.UiScreen;
import com.wf.wflib.ui.api.WidgetDef;
import com.wf.wflib.ui.api.input.UiAction;
import com.wf.wflib.ui.api.widget.ImageFit;
import com.wf.wflib.ui.api.widget.Point;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.UUID;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

import static com.wf.wflib.ui.api.Ui.at;
import static com.wf.wflib.ui.api.Ui.button;
import static com.wf.wflib.ui.api.Ui.layout;
import static com.wf.wflib.ui.api.Ui.place;
import static com.wf.wflib.ui.api.Ui.stack;
import static com.wf.wflib.ui.api.Ui.text;
import static com.wf.wflib.ui.api.Ui.when;

/** Full-screen viewer for one photograph; the owner also gets print and delete (delete asks twice). */
final class PhotoScreen {
    private static final ResourceLocation LAYOUT = ResourceLocation.fromNamespaceAndPath(WFLib.MODID, "photo_view");
    private static final ResourceLocation SHOWN = ResourceLocation.fromNamespaceAndPath(WFLib.MODID, "photo/view_shown");
    private static final DateTimeFormatter WHEN = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")
            .withZone(ZoneId.systemDefault());
    private static final long CONFIRM_MS = 3000;
    private static final Subject<View> VIEW = Subject.register("photo_view", View.class);
    private static final BorrowedTexture ALIAS = new BorrowedTexture(SHOWN);

    private record View(UUID id) {
    }

    private static UUID shown;
    private static long armed;

    private PhotoScreen() {
    }

    static void register() {
        Signals.num(VIEW, "aspect", v -> {
            PhotoImages.Tex tex = PhotoImages.get(v.id());
            if (tex == null) {
                return 0;
            }
            ALIAS.adopt(tex.texture().getId());
            return (double) tex.width() / tex.height();
        });
        Signals.bool(VIEW, "failed", v -> PhotoImages.failed(v.id()));
        Signals.bool(VIEW, "owner", v -> PhotoClient.row(v.id()) != null);
        Signals.text(VIEW, "info", 64, v -> {
            PhotoListPacket.Row row = PhotoClient.row(v.id());
            return row == null ? "" : WHEN.format(Instant.ofEpochMilli(row.time())) + "   " + row.x() + " " + row.y()
                    + " " + row.z();
        });
        Signals.text(VIEW, "delete", 32, v -> Component.translatable(
                System.currentTimeMillis() - armed < CONFIRM_MS ? "wflib.photo.confirm" : "wflib.photo.delete").getString());
        UiLayouts.register(build());
    }

    static void open(UUID id) {
        armed = 0;
        shown = id;
        View view = new View(id);
        UiScreen.open(UiLayouts.get(LAYOUT), subject -> subject == VIEW ? view : null,
                Component.translatable("wflib.photo.title"), UiScreen.Options.DEFAULT);
    }

    private static UiLayout build() {
        String areaW = "(ui.width - 40)";
        String areaH = "(ui.height - 70)";
        String fitted = "min(" + areaW + " / photo_view.aspect, " + areaH + ")";
        String row = "ui.height - 26";
        UiAction done = item -> Minecraft.getInstance().setScreen(null);
        return layout(LAYOUT, stack(
                at("18", "18", new ImageFit(SHOWN, areaW + " + 4", areaH + " + 4", "photo_view.aspect", 2, "#FFE8E8E8")),
                when("photo_view.aspect > 0", place("ui.width / 2", "28 + (" + areaH + " + " + fitted + ") / 2", Point.TOP,
                        text("{photo_view.info}", "#FFCCCCCC"))),
                when("photo_view.aspect <= 0", place("ui.width / 2", "ui.height / 2 - 4", Point.TOP, when("photo_view.failed",
                        text("{tr:wflib.photo.unavailable}", "#FFAAAAAA"), text("{tr:wflib.photo.loading}", "#FFAAAAAA")))),
                at("ui.width / 2 - 50", row, command("{tr:gui.done}", done)),
                when("photo_view.owner", stack(
                        at("ui.width / 2 - 158", row, command("{tr:wflib.photo.print}", item -> PhotoClient.action(PhotoActionPacket.PRINT, shown))),
                        at("ui.width / 2 + 58", row, command("{photo_view.delete}", item -> {
                            long now = System.currentTimeMillis();
                            if (now - armed < CONFIRM_MS) {
                                PhotoClient.action(PhotoActionPacket.DELETE, shown);
                                Minecraft.getInstance().setScreen(null);
                            } else {
                                armed = now;
                            }
                        })))))).absolute();
    }

    private static WidgetDef command(String label, UiAction action) {
        return button(100, 20, "#FF2B2B2B", "#FF4A4A4A", "#FF8B8B8B", action, text(label, "#FFFFFFFF"));
    }
}
