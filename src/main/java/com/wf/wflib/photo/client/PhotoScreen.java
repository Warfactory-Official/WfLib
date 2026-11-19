package com.wf.wflib.photo.client;

import com.wf.wflib.photo.PhotoActionPacket;
import com.wf.wflib.photo.PhotoListPacket;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.UUID;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/** Full-screen viewer for one photograph; the owner also gets print and delete. */
final class PhotoScreen extends Screen {
    private static final DateTimeFormatter WHEN = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")
            .withZone(ZoneId.systemDefault());
    private static final long CONFIRM_MS = 3000;

    private final UUID id;
    private Button delete;
    private long armed;

    PhotoScreen(UUID id) {
        super(Component.translatable("wflib.photo.title"));
        this.id = id;
    }

    @Override
    protected void init() {
        addRenderableWidget(Button.builder(Component.translatable("gui.done"), b -> onClose())
                .bounds(width / 2 - 50, height - 26, 100, 20).build());
        if (PhotoClient.row(id) != null) {
            addRenderableWidget(Button.builder(Component.translatable("wflib.photo.print"),
                    b -> PhotoClient.action(PhotoActionPacket.PRINT, id)).bounds(width / 2 - 158, height - 26, 100, 20)
                    .build());
            delete = addRenderableWidget(Button.builder(Component.translatable("wflib.photo.delete"), b -> {
                long now = System.currentTimeMillis();
                if (now - armed < CONFIRM_MS) {
                    PhotoClient.action(PhotoActionPacket.DELETE, id);
                    onClose();
                } else {
                    armed = now;
                }
            }).bounds(width / 2 + 58, height - 26, 100, 20).build());
        }
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partial) {
        if (delete != null) {
            delete.setMessage(Component.translatable(System.currentTimeMillis() - armed < CONFIRM_MS
                    ? "wflib.photo.confirm" : "wflib.photo.delete"));
        }
        super.render(g, mouseX, mouseY, partial);
        PhotoImages.Tex tex = PhotoImages.get(id);
        int areaW = width - 40;
        int areaH = height - 70;
        if (tex == null) {
            g.drawCenteredString(font, Component.translatable(PhotoImages.failed(id) ? "wflib.photo.unavailable"
                    : "wflib.photo.loading"), width / 2, height / 2 - 4, 0xFFAAAAAA);
            return;
        }
        float scale = Math.min((float) areaW / tex.width(), (float) areaH / tex.height());
        int w = Math.max(1, Math.round(tex.width() * scale));
        int h = Math.max(1, Math.round(tex.height() * scale));
        int x = (width - w) / 2;
        int y = 20 + (areaH - h) / 2;
        g.fill(x - 2, y - 2, x + w + 2, y + h + 2, 0xFFE8E8E8);
        g.blit(tex.location(), x, y, w, h, 0f, 0f, tex.width(), tex.height(), tex.width(), tex.height());
        PhotoListPacket.Row row = PhotoClient.row(id);
        if (row != null) {
            g.drawCenteredString(font, WHEN.format(Instant.ofEpochMilli(row.time())) + "   " + row.x() + " " + row.y()
                    + " " + row.z(), width / 2, y + h + 8, 0xFFCCCCCC);
        }
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
