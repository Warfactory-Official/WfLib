package com.wf.wflib.soldier.client;

import com.wf.wflib.soldier.Companion;
import com.wf.wflib.soldier.api.SoldierOrder.Kind;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.phys.Vec3;
import org.lwjgl.glfw.GLFW;

/**
 * Hold the wheel key, move the mouse to an order, let go: the selected group does it. The scroll wheel or 1-5 change group.
 * Orders that need a point use the spot the crosshair was on when the wheel opened (greyed when there was none).
 */
final class CommandWheel extends Screen {
    /** Clockwise from the top. */
    private static final Kind[] ORDERS = {Kind.FOLLOW, Kind.MOVE, Kind.ATTACK, Kind.SUPPRESS, Kind.RESUPPLY, Kind.GUARD, Kind.PATROL, Kind.HOLD};
    private static final int RADIUS = 78, W = 62, H = 22, DEAD = 22;

    private final Vec3 aim;
    private int hovered = -1;

    CommandWheel(Vec3 aim) {
        super(Component.translatable("wflib.squad.wheel"));
        this.aim = aim;
    }

    @Override public boolean isPauseScreen() { return false; }

    @Override public void renderBackground(GuiGraphics g, int mx, int my, float pt) { /* no blur: the world stays in view */ }

    private boolean usable(Kind k) {
        return !k.needsPos || aim != null;
    }

    private int pick(double mx, double my) {
        double dx = mx - width / 2.0, dy = my - height / 2.0;
        if (dx * dx + dy * dy < DEAD * DEAD) return -1;
        double angle = Math.toDegrees(Math.atan2(dx, -dy));                 // 0 = up, clockwise
        int i = (int) Math.round(((angle % 360 + 360) % 360) / 45.0) % ORDERS.length;
        return usable(ORDERS[i]) ? i : -1;
    }

    /** A screen releases every key and stops reading them; the wheel hands the movement keys back so you can keep walking while you choose. */
    private static void keepMoving() {
        var mc = net.minecraft.client.Minecraft.getInstance();
        var o = mc.options;
        for (var k : new net.minecraft.client.KeyMapping[]{o.keyUp, o.keyDown, o.keyLeft, o.keyRight, o.keyJump, o.keySprint, o.keyShift}) {
            var key = k.getKey();
            if (key.getType() == com.mojang.blaze3d.platform.InputConstants.Type.KEYSYM)     // setDown on the mapping itself: the key table points at whichever binding registered last
                k.setDown(com.mojang.blaze3d.platform.InputConstants.isKeyDown(mc.getWindow().getWindow(), key.getValue()));
        }
    }

    private static boolean down(net.minecraft.client.KeyMapping k) {
        var key = k.getKey();
        var mc = net.minecraft.client.Minecraft.getInstance();
        return key.getType() == com.mojang.blaze3d.platform.InputConstants.Type.KEYSYM
                && com.mojang.blaze3d.platform.InputConstants.isKeyDown(mc.getWindow().getWindow(), key.getValue());
    }

    /** Fills the player's movement input from the keys as they are held, since a screen's input handling leaves it empty. */
    static void drive(net.minecraft.client.player.Input in) {
        var o = net.minecraft.client.Minecraft.getInstance().options;
        in.up = down(o.keyUp);
        in.down = down(o.keyDown);
        in.left = down(o.keyLeft);
        in.right = down(o.keyRight);
        in.jumping = down(o.keyJump);
        in.shiftKeyDown = down(o.keyShift);
        in.forwardImpulse = (in.up ? 1f : 0f) - (in.down ? 1f : 0f);
        in.leftImpulse = (in.left ? 1f : 0f) - (in.right ? 1f : 0f);
        if (in.shiftKeyDown) {
            in.forwardImpulse *= 0.3f;
            in.leftImpulse *= 0.3f;
        }
    }

    @Override
    public void tick() {
        keepMoving();
        if (!SquadClient.wheelHeld()) {                                      // let go: do what is hovered
            if (hovered >= 0) SquadClient.send(ORDERS[hovered].id, ORDERS[hovered].needsPos ? aim : null);
            onClose();
        }
    }

    @Override
    public void render(GuiGraphics g, int mx, int my, float pt) {
        hovered = pick(mx, my);
        int cx = width / 2, cy = height / 2;
        g.fill(cx - DEAD, cy - DEAD, cx + DEAD, cy + DEAD, 0x99000000);
        SquadHud.frame(g, cx - DEAD, cy - DEAD, cx + DEAD, cy + DEAD, SquadClient.group < 0 ? 0xFFB0B0B0 : 0xFFD83A3A);
        g.drawCenteredString(font, SquadClient.group < 0 ? "ALL" : Companion.TEAMS[SquadClient.group].toUpperCase(java.util.Locale.ROOT), cx, cy - 4, 0xFFFFFFFF);
        for (int i = 0; i < ORDERS.length; i++) {
            double a = Math.toRadians(i * 45.0);
            int x = cx + (int) Math.round(Math.sin(a) * RADIUS), y = cy - (int) Math.round(Math.cos(a) * RADIUS);
            boolean on = i == hovered, ok = usable(ORDERS[i]);
            g.fill(x - W / 2, y - H / 2, x + W / 2, y + H / 2, on ? 0xDDD83A3A : ok ? 0xAA000000 : 0x66000000);
            SquadHud.frame(g, x - W / 2, y - H / 2, x + W / 2, y + H / 2, on ? 0xFFFFFFFF : 0xFF606060);
            g.drawCenteredString(font, ORDERS[i].title, x, y - 4, ok ? 0xFFFFFFFF : 0xFF707070);
        }
        g.drawCenteredString(font, Component.translatable("wflib.squad.wheel_hint"), cx, cy + RADIUS + H + 6, 0xFFB0B0B0);
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double sx, double sy) {
        if (sy != 0) SquadClient.cycleGroup();
        return true;
    }

    @Override
    public boolean keyPressed(int key, int scan, int mods) {
        if (key >= GLFW.GLFW_KEY_1 && key <= GLFW.GLFW_KEY_5) {
            SquadClient.selectGroup(key - GLFW.GLFW_KEY_1 - 1);              // 1 = everyone, 2-5 = Alpha..Delta
            return true;
        }
        return super.keyPressed(key, scan, mods);
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        int i = pick(mx, my);
        if (i >= 0) {
            SquadClient.send(ORDERS[i].id, ORDERS[i].needsPos ? aim : null);
            onClose();
        }
        return true;
    }
}
