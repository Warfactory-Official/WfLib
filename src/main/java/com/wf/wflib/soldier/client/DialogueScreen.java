package com.wf.wflib.soldier.client;

import com.google.gson.JsonObject;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Pose;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;

/**
 * A conversation laid out like STALKER 2's: the speaker's face fills the screen, their name sits over a column of answers (the
 * chosen one in orange, a key hint beside it), and what they say runs along the bottom, typed out as it is heard. The server owns the
 * conversation; this only shows a node and reports the pick.
 */
final class DialogueScreen extends Screen {
    private record Opt(String tag, String text, int kind, boolean on, String hint) {}

    private static final int ORANGE = 0xFFE8A31B, ROW = 0xD0151515, ROW_HOVER = 0xD0262626, TEXT = 0xFFE9E9E9, DIM = 0xFF8A8A8A, GREY = 0xFF5C5C5C;
    private static final int ROW_H = 24, GAP = 3;

    private int entity;
    private String name = "", sub = "", line = "";
    private final List<Opt> options = new ArrayList<>();
    private int selected, age, typed;

    DialogueScreen(JsonObject node) {
        super(Component.literal("Dialogue"));
        update(node);
        age = 0;
    }

    void update(JsonObject j) {
        entity = j.get("entity").getAsInt();
        name = j.get("name").getAsString();
        sub = j.get("sub").getAsString();
        line = j.get("line").getAsString();
        options.clear();
        for (var e : j.getAsJsonArray("options")) {
            var o = e.getAsJsonObject();
            options.add(new Opt(o.get("tag").getAsString(), o.get("text").getAsString(), o.get("kind").getAsInt(), o.get("on").getAsBoolean(), o.get("hint").getAsString()));
        }
        selected = 0;
        typed = 0;
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void tick() {
        age++;
        typed = Math.min(line.length(), typed + 3);
    }

    private int colW() {
        return Math.min(width - 60, 520);
    }

    private int colX() {
        return (width - colW()) / 2;
    }

    private int top() {
        return height - 88 - options.size() * (ROW_H + GAP);
    }

    @Override
    public void render(GuiGraphics g, int mx, int my, float pt) {
        float fade = Math.min(1f, (age + pt) / 8f);
        g.fillGradient(0, 0, width, height, 0xB0101310, 0xD0030403);          // the world still shows through, darkened
        portrait(g, pt);
        g.fillGradient(0, height / 2, width, height, 0x00000000, 0xE6000000);
        g.fillGradient(0, 0, width, 40, 0xAA000000, 0x00000000);
        int x = colX(), y = top();
        // the speaker's tag over the answers
        String tag = name + (sub.isEmpty() ? "" : "  " + sub);
        int tw = font.width(name) + 14;
        g.fill(x, y - 18, x + tw, y - 2, 0xE0101010);
        g.drawString(font, name, x + 7, y - 14, ORANGE, false);
        if (!sub.isEmpty()) g.drawString(font, sub, x + tw + 8, y - 14, DIM, false);
        for (int i = 0; i < options.size(); i++) {
            Opt o = options.get(i);
            int ry = y + i * (ROW_H + GAP);
            boolean hot = i == selected;
            boolean over = mx >= x && mx < x + colW() && my >= ry && my < ry + ROW_H;
            g.fill(x, ry, x + colW(), ry + ROW_H, hot ? (o.on ? ORANGE : 0xFF6E5214) : over ? ROW_HOVER : ROW);
            if (!hot) g.fill(x, ry, x + 2, ry + ROW_H, 0x88FFFFFF);
            icon(g, x + 10, ry + 6, o.kind, hot);
            String label = (o.tag.isEmpty() ? "" : "[" + o.tag + "] ") + o.text;
            int colour = !o.on ? GREY : hot ? 0xFF111111 : TEXT;
            g.drawString(font, label, x + 34, ry + 8, colour, false);
            if (!o.hint.isEmpty()) {
                String hint = o.hint;
                g.drawString(font, hint, x + colW() - 8 - font.width(hint), ry + 8, !o.on ? 0xFFB04A3A : hot ? 0xFF3A2A06 : DIM, false);
            }
            if (hot) {
                g.fill(x - 26, ry + 3, x - 6, ry + ROW_H - 3, 0xFFD6D6D6);
                g.drawCenteredString(font, "F", x - 16, ry + 8, 0xFF111111);
            }
        }
        // what they say, typed out
        int ty = height - 64;
        g.drawString(font, name + ":", x - 90 < 8 ? 8 : x - 90, ty, ORANGE, false);
        int tx = x;
        List<FormattedCharSequence> lines = font.split(Component.literal(line.substring(0, typed)), Math.min(width - 40, 640));
        for (int i = 0; i < lines.size() && i < 4; i++) g.drawString(font, lines.get(i), tx, ty + i * 11, TEXT, false);
        if (fade < 1f) g.fill(0, 0, width, height, (int) ((1f - fade) * 255) << 24);
    }

    /** The speaker's face: the real body, turned to face us and stood up for the picture, then put back as it was. */
    private void portrait(GuiGraphics g, float pt) {
        var level = minecraft == null ? null : minecraft.level;
        if (level == null || !(level.getEntity(entity) instanceof LivingEntity who)) return;
        float body = who.yBodyRot, bodyO = who.yBodyRotO, head = who.yHeadRot, headO = who.yHeadRotO, yRot = who.getYRot(), xRot = who.getXRot(), yO = who.yRotO, xO = who.xRotO;
        Pose pose = who.getPose();
        boolean tag = who.isCustomNameVisible();
        who.setCustomNameVisible(false);                                         // no giant name over the face
        who.setPose(Pose.STANDING);
        who.yBodyRot = who.yBodyRotO = who.yHeadRot = who.yHeadRotO = 180;
        who.setYRot(180);
        who.yRotO = 180;
        who.setXRot(0);
        who.xRotO = 0;
        float scale = height * 0.56f;
        InventoryScreen.renderEntityInInventory(g, width / 2f, height * 0.30f + 0.85f * scale, scale, new Vector3f(0, 1f, 0),
                new Quaternionf().rotateZ((float) Math.PI), new Quaternionf(), who);
        g.flush();
        com.mojang.blaze3d.systems.RenderSystem.clear(org.lwjgl.opengl.GL11.GL_DEPTH_BUFFER_BIT, net.minecraft.client.Minecraft.ON_OSX);   // the 3D body must not hide what is drawn after it
        who.setCustomNameVisible(tag);
        who.setPose(pose);
        who.yBodyRot = body;
        who.yBodyRotO = bodyO;
        who.yHeadRot = head;
        who.yHeadRotO = headO;
        who.setYRot(yRot);
        who.setXRot(xRot);
        who.yRotO = yO;
        who.xRotO = xO;
    }

    private static void icon(GuiGraphics g, int x, int y, int kind, boolean hot) {
        int c = hot ? 0xFF111111 : 0xFFD8D8D8;
        switch (kind) {
            case 1 -> {                                                              // a deed: a "!" in a diamond-ish box
                g.fill(x + 2, y, x + 12, y + 12, hot ? 0xFF111111 : ORANGE);
                g.fill(x + 4, y + 2, x + 10, y + 10, hot ? ORANGE : 0xFF2A1D04);
                g.fill(x + 6, y + 3, x + 8, y + 7, hot ? 0xFF111111 : ORANGE);
                g.fill(x + 6, y + 8, x + 8, y + 9, hot ? 0xFF111111 : ORANGE);
            }
            case 2 -> {                                                              // leave: a door and an arrow
                g.fill(x + 1, y, x + 9, y + 12, c);
                g.fill(x + 3, y + 2, x + 7, y + 10, hot ? ORANGE : ROW);
                g.fill(x + 8, y + 5, x + 14, y + 7, c);
            }
            default -> {                                                             // a plain question: a speech bubble
                g.fill(x, y + 1, x + 14, y + 10, c);
                g.fill(x + 2, y + 10, x + 5, y + 12, c);
                g.fill(x + 3, y + 5, x + 5, y + 6, hot ? ORANGE : ROW);
                g.fill(x + 6, y + 5, x + 8, y + 6, hot ? ORANGE : ROW);
                g.fill(x + 9, y + 5, x + 11, y + 6, hot ? ORANGE : ROW);
            }
        }
    }

    private void choose(int i) {
        if (i >= 0 && i < options.size()) DialogueClient.pick(i);
    }

    @Override
    public boolean keyPressed(int key, int scan, int mods) {
        switch (key) {
            case GLFW.GLFW_KEY_ESCAPE -> {
                DialogueClient.pick(-1);
                onClose();
                return true;
            }
            case GLFW.GLFW_KEY_UP, GLFW.GLFW_KEY_W -> selected = Math.floorMod(selected - 1, Math.max(1, options.size()));
            case GLFW.GLFW_KEY_DOWN, GLFW.GLFW_KEY_S -> selected = Math.floorMod(selected + 1, Math.max(1, options.size()));
            case GLFW.GLFW_KEY_F, GLFW.GLFW_KEY_ENTER, GLFW.GLFW_KEY_KP_ENTER, GLFW.GLFW_KEY_SPACE -> choose(selected);
            default -> {
                if (key >= InputConstants.KEY_1 && key <= InputConstants.KEY_9) choose(key - InputConstants.KEY_1);
                else return super.keyPressed(key, scan, mods);
            }
        }
        return true;
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        int x = colX(), y = top();
        for (int i = 0; i < options.size(); i++) {
            int ry = y + i * (ROW_H + GAP);
            if (mx >= x && mx < x + colW() && my >= ry && my < ry + ROW_H) {
                selected = i;
                choose(i);
                return true;
            }
        }
        return super.mouseClicked(mx, my, button);
    }

    @Override
    public void mouseMoved(double mx, double my) {
        int x = colX(), y = top();
        for (int i = 0; i < options.size(); i++) {
            int ry = y + i * (ROW_H + GAP);
            if (mx >= x && mx < x + colW() && my >= ry && my < ry + ROW_H) selected = i;
        }
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double dx, double dy) {
        selected = Math.floorMod(selected - (int) Math.signum(dy), Math.max(1, options.size()));
        return true;
    }

    @Override
    public void onClose() {
        super.onClose();
    }
}
