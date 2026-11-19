package com.wf.wflib.photo.client;

import static com.wf.wflib.ui.api.Ui.at;
import static com.wf.wflib.ui.api.Ui.list;
import static com.wf.wflib.ui.api.Ui.place;
import static com.wf.wflib.ui.api.Ui.rect;
import static com.wf.wflib.ui.api.Ui.stack;
import static com.wf.wflib.ui.api.Ui.text;
import static com.wf.wflib.ui.api.Ui.when;

import com.wf.wflib.core.api.WFLib;
import com.wf.wflib.photo.PhotoActionPacket;
import com.wf.wflib.photo.PhotoListPacket;
import com.wf.wflib.pda.api.client.PdaApp;
import com.wf.wflib.pda.api.client.PdaApps;
import com.wf.wflib.pda.api.client.PdaShell;
import com.wf.wflib.ui.api.Signals;
import com.wf.wflib.ui.api.Subject;
import com.wf.wflib.ui.api.WidgetDef;
import com.wf.wflib.ui.api.widget.Button;
import com.wf.wflib.ui.api.widget.Image;
import com.wf.wflib.ui.api.widget.Point;
import com.wf.wflib.ui.api.widget.Sized;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.UUID;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.AbstractTexture;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import org.jetbrains.annotations.Nullable;

/** The PDA gallery: the player's photographs on the left, the selected one on the right. */
final class PhotoApp implements PdaApp {
    private static final ResourceLocation ID = ResourceLocation.fromNamespaceAndPath(WFLib.MODID, "photos");
    private static final ResourceLocation SHOWN = ResourceLocation.fromNamespaceAndPath(WFLib.MODID, "photo/pda_shown");
    private static final DateTimeFormatter WHEN = DateTimeFormatter.ofPattern("MM-dd HH:mm")
            .withZone(ZoneId.systemDefault());
    private static final long CONFIRM_MS = 3000;
    private static final PhotoApp INSTANCE = new PhotoApp();
    private static final Subject<PhotoApp> APP = Subject.register("pda_photos", PhotoApp.class, () -> INSTANCE);
    private static final Subject<PhotoListPacket.Row> ROW = Subject.register("pda_photo", PhotoListPacket.Row.class);

    /** Present in the texture manager under {@link #SHOWN}; shows whichever photo texture was adopted last. */
    private static final class Alias extends AbstractTexture {
        void adopt(int glId) {
            this.id = glId;
        }

        @Override
        public void load(ResourceManager manager) {
            // the id is borrowed from a photo's own dynamic texture
        }

        @Override
        public void releaseId() {
            this.id = -1; // never delete: the id belongs to the photo texture
        }

        @Override
        public int getId() {
            return this.id;
        }
    }

    private static final Alias ALIAS = new Alias();
    private static boolean aliasRegistered;
    @Nullable
    private static UUID selected;
    private static long armed;

    private PhotoApp() {
    }

    static void register() {
        Signals.num(APP, "count", a -> PhotoClient.rows().size());
        Signals.items(APP, "rows", a -> PhotoClient.rows(), ROW);
        Signals.bool(APP, "picture", a -> shown());
        Signals.bool(APP, "has_sel", a -> selected != null && PhotoClient.row(selected) != null);
        Signals.text(APP, "del", 12, a -> System.currentTimeMillis() - armed < CONFIRM_MS ? "CONFIRM" : "DELETE");
        Signals.text(APP, "info", PdaShell.TEXT_MAX, a -> info());
        Signals.bool(ROW, "selected", r -> r.id().equals(selected));
        Signals.bool(ROW, "printed", PhotoListPacket.Row::shared);
        Signals.text(ROW, "label", PdaShell.TEXT_MAX, r -> WHEN.format(Instant.ofEpochMilli(r.time())) + "  " + r.x()
                + " " + r.z());
        PdaApps.register(INSTANCE, 960);
    }

    /** The list changed (new shot, delete): keep the selection valid, default to the newest. */
    static void listChanged() {
        if (selected == null || PhotoClient.row(selected) == null) {
            selected = PhotoClient.rows().isEmpty() ? null : PhotoClient.rows().get(0).id();
        }
    }

    private static boolean shown() {
        if (selected == null) {
            return false;
        }
        PhotoImages.Tex tex = PhotoImages.get(selected);
        if (tex == null) {
            return false;
        }
        if (!aliasRegistered) {
            Minecraft.getInstance().getTextureManager().register(SHOWN, ALIAS);
            aliasRegistered = true;
        }
        ALIAS.adopt(tex.texture().getId());
        return true;
    }

    private static String info() {
        PhotoListPacket.Row r = selected == null ? null : PhotoClient.row(selected);
        if (r == null) {
            return "";
        }
        return WHEN.format(Instant.ofEpochMilli(r.time())) + "   " + r.x() + " " + r.y() + " " + r.z() + "   "
                + (r.size() / 1024) + " KB" + (r.shared() ? "   PRINTED" : "");
    }

    private static void select(Object item) {
        selected = ((PhotoListPacket.Row) item).id();
        armed = 0;
    }

    private static void delete(Object ignored) {
        if (selected == null) {
            return;
        }
        long now = System.currentTimeMillis();
        if (now - armed < CONFIRM_MS) {
            PhotoClient.action(PhotoActionPacket.DELETE, selected);
            armed = 0;
        } else {
            armed = now;
        }
    }

    private static void print(Object ignored) {
        if (selected != null) {
            PhotoClient.action(PhotoActionPacket.PRINT, selected);
        }
    }

    private static void view(Object ignored) {
        if (selected != null) {
            PhotoClient.view(selected);
        }
    }

    @Override
    public ResourceLocation id() {
        return ID;
    }

    @Override
    public Component title() {
        return Component.translatable("wflib.pda.app.photos");
    }

    @Override
    public void opened() {
        PhotoClient.requestList();
    }

    @Override
    public WidgetDef build() {
        float listW = 112;
        float h = PdaShell.APP_HEIGHT - 16;
        float rowW = listW - 8;
        WidgetDef row = new Button(PhotoApp::select, "true", "pda_photo.selected ? " + PdaShell.PRESS + " : 0",
                PdaShell.HOVER, PdaShell.PRESS, "0", 0, 0, 0, 0, rowW, 18, Point.TOP_LEFT,
                new Sized(Float.toString(rowW), "18", Point.TOP_LEFT, false, stack(
                        at("5", "5", PdaShell.fit("{pda_photo.label}", "pda_photo.printed ? " + PdaShell.ACCENT + " : "
                                + PdaShell.TEXT, rowW - 8)))));
        float viewX = listW + 16;
        float viewW = PdaShell.APP_WIDTH - viewX - 8;
        float picW = viewW - 8;
        float picH = (float) Math.floor(picW * 9 / 16);
        String pw = Float.toString(picW);
        String ph = Float.toString(picH);
        float buttonsY = 24 + picH + 22;
        return stack(
                at("8", "8", PdaShell.panel(listW, h)),
                at("14", "13", PdaShell.fit("PHOTOS  {pda_photos.count}", PdaShell.DIM, listW - 12)),
                at("12", "28", list("pda_photos.rows", 18, 1, Float.toString(rowW), Float.toString(h - 24),
                        PdaShell.EDGE, row)),
                when("pda_photos.count == 0", stack(at("14", "34", text("NO PHOTOS", PdaShell.DIM)),
                        at("14", "46", PdaShell.wrap("USE A CAMERA", PdaShell.DIM, listW - 12, 3)))),
                at(Float.toString(viewX), "8", PdaShell.panel(viewW, h)),
                at(Float.toString(viewX + 6), "13", PdaShell.fit("{pda_photos.info}", PdaShell.DIM, viewW - 12)),
                at(Float.toString(viewX + 4), "24", stack(
                        rect(pw, ph, "#ff101210"),
                        when("pda_photos.picture", new Image(SHOWN, picW, picH, 0, 0, 1, 1, "#ffffffff", "0")),
                        when("!pda_photos.picture", place(Float.toString(picW / 2), Float.toString(picH / 2 - 4),
                                Point.TOP, text("{pda_photos.has_sel ? \"LOADING\" : \"\"}", PdaShell.DIM))))),
                at(Float.toString(viewX + 4), Float.toString(buttonsY), button("VIEW", PhotoApp::view)),
                at(Float.toString(viewX + 64), Float.toString(buttonsY), button("PRINT", PhotoApp::print)),
                at(Float.toString(viewX + viewW - 62), Float.toString(buttonsY), new Button(PhotoApp::delete,
                        "pda_photos.has_sel", PdaShell.PANEL, PdaShell.HOVER, PdaShell.PRESS, PdaShell.EDGE, 1, 0, 8, 4,
                        0, 0, Point.CENTER, text("{pda_photos.del}", PdaShell.TEXT))));
    }

    private static WidgetDef button(String label, java.util.function.Consumer<Object> action) {
        return new Button(action::accept, "pda_photos.has_sel", PdaShell.PANEL, PdaShell.HOVER, PdaShell.PRESS,
                PdaShell.EDGE, 1, 0, 8, 4, 0, 0, Point.CENTER, text(label, PdaShell.TEXT));
    }
}
