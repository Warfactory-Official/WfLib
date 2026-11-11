package com.wf.wflib.soldier.client;

import com.wf.wflib.soldier.DialoguePackets;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;

/**
 * Going through gear with a soldier of yours: click a stack on either side to move it across. The server does the moving (and
 * checks the soldier is still yours and close by) and sends the soldier's inventory back after every move.
 */
final class TradeScreen extends Screen {
    private static final int CELL = 18, PANEL = 0xE0121212, EDGE = 0xFF3A3A3A, ORANGE = 0xFFE8A31B, DIM = 0xFF8A8A8A;
    /** Where the soldier's armour and off hand sit in its inventory (feet, legs, chest, head, off hand), shown head first. */
    private static final int[] WORN = {39, 38, 37, 36, 40};
    private static final String[] WORN_NAME = {"Head", "Chest", "Legs", "Feet", "Off hand"};

    private int entity;
    private final List<ItemStack> soldier = new ArrayList<>();

    TradeScreen(DialoguePackets.Trade packet) {
        super(Component.literal("Trade"));
        update(packet);
    }

    void update(DialoguePackets.Trade packet) {
        entity = packet.entity();
        soldier.clear();
        soldier.addAll(packet.items());
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    private int panelW() {
        return 9 * CELL + 16;
    }

    private int leftX() {
        return width / 2 - panelW() - 6;
    }

    private int rightX() {
        return width / 2 + 6;
    }

    private int top() {
        return Math.max(16, height / 2 - 100);
    }

    /** The slot under (x, y) of the panel at {@code px}, whose first row of grid is at {@code gy}; -1 when none. Main rows: hotbar last, as in the inventory. */
    private static int gridSlot(int px, int gy, double mx, double my) {
        int col = (int) Math.floor((mx - (px + 8)) / CELL), row = (int) Math.floor((my - gy) / CELL);
        if (mx < px + 8 || col < 0 || col > 8 || my < gy || row < 0 || row > 3) return -1;
        return row == 3 ? col : 9 + row * 9 + col;
    }

    private int soldierGridY() {
        return top() + 24 + CELL + 14;
    }

    private int playerGridY() {
        return top() + 24;
    }

    @Override
    public void render(GuiGraphics g, int mx, int my, float pt) {
        g.fill(0, 0, width, height, 0xB0000000);
        ItemStack tip = ItemStack.EMPTY;
        // the player's side
        int lx = leftX(), ty = top();
        panel(g, lx, ty, "You", 24 + 4 * CELL + 12);
        var inv = minecraft.player.getInventory();
        for (int s = 0; s < 36; s++) {
            int[] at = cell(lx, playerGridY(), s);
            ItemStack st = inv.getItem(s);
            slot(g, at[0], at[1], st, mx, my);
            if (!st.isEmpty() && over(at[0], at[1], mx, my)) tip = st;
        }
        // the soldier's side: worn pieces, then the bag
        int rx = rightX();
        String name = minecraft.level != null && minecraft.level.getEntity(entity) != null ? minecraft.level.getEntity(entity).getName().getString() : "Soldier";
        panel(g, rx, ty, name, 24 + CELL + 14 + 4 * CELL + 12);
        for (int i = 0; i < WORN.length; i++) {
            int x = rx + 8 + i * (CELL + 8), y = ty + 24;
            ItemStack st = WORN[i] < soldier.size() ? soldier.get(WORN[i]) : ItemStack.EMPTY;
            slot(g, x, y, st, mx, my);
            if (st.isEmpty()) g.drawString(font, WORN_NAME[i].substring(0, 1), x + 6, y + 5, 0xFF444444, false);
            if (!st.isEmpty() && over(x, y, mx, my)) tip = st;
        }
        for (int s = 0; s < 36; s++) {
            int[] at = cell(rx, soldierGridY(), s);
            ItemStack st = s < soldier.size() ? soldier.get(s) : ItemStack.EMPTY;
            slot(g, at[0], at[1], st, mx, my);
            if (!st.isEmpty() && over(at[0], at[1], mx, my)) tip = st;
        }
        String hint = "Click a stack to move it across  -  Esc to finish";
        g.drawCenteredString(font, hint, width / 2, ty + 24 + CELL + 14 + 4 * CELL + 24, DIM);
        if (!tip.isEmpty()) g.renderTooltip(font, tip, mx, my);
    }

    private static int[] cell(int px, int gy, int slot) {
        int row = slot < 9 ? 3 : (slot - 9) / 9, col = slot < 9 ? slot : (slot - 9) % 9;
        return new int[]{px + 8 + col * CELL, gy + row * CELL};
    }

    private static boolean over(int x, int y, double mx, double my) {
        return mx >= x && mx < x + CELL && my >= y && my < y + CELL;
    }

    private void panel(GuiGraphics g, int x, int y, String title, int h) {
        g.fill(x, y, x + panelW(), y + h, PANEL);
        g.renderOutline(x, y, panelW(), h, EDGE);
        g.drawString(font, title, x + 8, y + 8, ORANGE, false);
    }

    private void slot(GuiGraphics g, int x, int y, ItemStack st, int mx, int my) {
        g.fill(x, y, x + CELL - 1, y + CELL - 1, over(x, y, mx, my) ? 0xFF3C3C3C : 0xFF202020);
        if (st.isEmpty()) return;
        g.renderItem(st, x + 1, y + 1);
        g.renderItemDecorations(font, st, x + 1, y + 1);
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        int s = gridSlot(leftX(), playerGridY(), mx, my);
        if (s >= 0) {
            DialogueClient.move(false, s);
            return true;
        }
        s = gridSlot(rightX(), soldierGridY(), mx, my);
        if (s >= 0) {
            DialogueClient.move(true, s);
            return true;
        }
        for (int i = 0; i < WORN.length; i++) {
            if (over(rightX() + 8 + i * (CELL + 8), top() + 24, mx, my)) {
                DialogueClient.move(true, WORN[i]);
                return true;
            }
        }
        return super.mouseClicked(mx, my, button);
    }

    @Override
    public boolean keyPressed(int key, int scan, int mods) {
        if (key == GLFW.GLFW_KEY_ESCAPE) {
            DialogueClient.pick(-1);
            onClose();
            return true;
        }
        return super.keyPressed(key, scan, mods);
    }
}
