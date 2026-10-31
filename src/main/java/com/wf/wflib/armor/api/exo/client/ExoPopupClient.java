package com.wf.wflib.armor.api.exo.client;

import com.mojang.blaze3d.platform.InputConstants;
import com.wf.wflib.armor.api.exo.ArmorBattery;
import com.wf.wflib.armor.api.exo.ExoConfig;
import com.wf.wflib.armor.api.exo.ExoPackets;
import com.wf.wflib.armor.api.exo.ExoPart;
import com.wf.wflib.armor.api.exo.ExoPopup;
import com.wf.wflib.armor.api.exo.ExoProfiles;
import com.wf.wflib.armor.api.exo.ExoSpec;
import com.wf.wflib.armor.api.exo.ExoStacks;
import com.wf.wflib.armor.api.exo.PartState;
import com.wf.wflib.core.api.MenuSlots;
import com.wf.wflib.core.api.SlotLayout;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.client.event.ScreenEvent;
import net.neoforged.neoforge.client.settings.KeyConflictContext;
import net.neoforged.neoforge.network.PacketDistributor;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.glfw.GLFW;

import java.util.List;
import java.util.Locale;

/**
 * Draggable attachment window over the survival inventory: battery / artifact belt / NVG slots, charge, part condition.
 * Not a screen; its slots are {@link ExoPopup} menu slots, drawn here above everything (overlapped inventory slots
 * resolve to the topmost via {@code MixinContainerScreenFindSlot}).
 */
public final class ExoPopupClient {
    public static final KeyMapping KEY = new KeyMapping("key.wflib.exo_popup", KeyConflictContext.UNIVERSAL,
            InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_X, "key.categories.wflib");

    private static final int WIDTH = 124;
    private static final int PAD = 4;
    private static final int HEAD = 14;
    private static final int CELL = 18;
    private static final int ROW = 11;
    private static final int LINE = 9;
    private static final int CLOSE = 9;
    private static final int SURFACE = 0xEE48494A;
    private static final int HEADER = 0xF1313233;
    private static final int OUTLINE = 0x55FFFFFF;
    private static final int TEXT = 0xFFE0E0E0;
    private static final int MUTED = 0xFF9DA0A4;
    private static final int CELL_BG = 0xFF1E1F20;

    private static boolean open;
    private static int x;
    private static int y;
    private static boolean placed;
    private static int dragDx = Integer.MIN_VALUE;
    private static int dragDy;

    private ExoPopupClient() {
    }

    public static void keys(RegisterKeyMappingsEvent e) {
        e.register(KEY);
    }

    private static boolean shown(@Nullable net.minecraft.client.gui.screens.Screen screen) {
        return open && screen instanceof InventoryScreen && screen == Minecraft.getInstance().screen;
    }

    private static void close(Player player) {
        if (open) {
            open = false;
            dragDx = Integer.MIN_VALUE;
            ExoPopup.close(player);
            PacketDistributor.sendToServer(new ExoPackets.Popup(-1));
        }
    }

    @SubscribeEvent
    public static void key(ScreenEvent.KeyPressed.Pre e) {
        if (!(e.getScreen() instanceof InventoryScreen inv) || !KEY.isActiveAndMatches(
                InputConstants.getKey(e.getKeyCode(), e.getScanCode()))) {
            return;
        }
        Player player = Minecraft.getInstance().player;
        Slot hovered = inv.getSlotUnderMouse();
        if (player == null || hovered == null || hovered.index < ExoPopup.FIRST_TARGET
                || hovered.index >= ExoPopup.END_TARGET || hovered.container != player.getInventory()
                && !(hovered.index >= 5 && hovered.index <= 8)) {
            return;
        }
        ItemStack stack = hovered.getItem();
        if (ExoProfiles.of(stack) == null || !ExoPopup.offersAny(stack)) {
            return;
        }
        if (open && ExoPopup.target(player) == hovered.index) {
            close(player);
        } else {
            if (!open) {
                placed = false;
            }
            if (ExoPopup.open(player, hovered.index)) {
                open = true;
                PacketDistributor.sendToServer(new ExoPackets.Popup(hovered.index));
            }
        }
        e.setCanceled(true);
    }

    @SubscribeEvent
    public static void closing(ScreenEvent.Closing e) {
        Player player = Minecraft.getInstance().player;
        if (e.getScreen() instanceof InventoryScreen && player != null) {
            close(player);
        }
    }

    // --- layout --------------------------------------------------------------------------------

    private static int height(ItemStack garment, ExoSpec spec) {
        int h = HEAD + PAD + CELL + 10;
        if (spec.battery()) {
            h += 12;
        }
        return h + spec.parts().size() * ROW + PAD + repairLines().size() * LINE + 2;
    }

    private static void layout(InventoryScreen screen, Player player, ItemStack garment, ExoSpec spec) {
        if (!placed) {
            x = screen.getGuiLeft() + screen.getXSize() + 6;
            y = screen.getGuiTop();
            placed = true;
        }
        int h = height(garment, spec);
        int sw = screen.width;
        int sh = screen.height;
        x = Math.max(0, Math.min(x, sw - WIDTH));
        y = Math.max(0, Math.min(y, sh - h));
        SlotLayout.Placed range = MenuSlots.range(player, ExoPopup.GROUP);
        if (range == null) {
            return;
        }
        int col = 0;
        for (int k = 0; k < ExoPopup.KINDS; k++) {
            Slot slot = screen.getMenu().slots.get(range.first() + k);
            if (ExoPopup.offers(garment, k)) {
                slot.x = x - screen.getGuiLeft() + PAD + col * (CELL + 14) + 1;
                slot.y = y - screen.getGuiTop() + HEAD + PAD + 1;
                col++;
            } else {
                slot.x = -10000;
                slot.y = -10000;
            }
        }
    }

    private static boolean inside(double mx, double my, int h) {
        return mx >= x && mx < x + WIDTH && my >= y && my < y + h;
    }

    private static String shortUnits(long v) {
        if (v >= 1_000_000) {
            return String.format(Locale.ROOT, "%.1fM", v / 1e6);
        }
        return v >= 1000 ? String.format(Locale.ROOT, "%.1fk", v / 1e3) : Long.toString(v);
    }

    // --- render --------------------------------------------------------------------------------

    @SubscribeEvent
    public static void render(ScreenEvent.Render.Post e) {
        if (!shown(e.getScreen())) {
            return;
        }
        InventoryScreen screen = (InventoryScreen) e.getScreen();
        Minecraft mc = Minecraft.getInstance();
        Player player = mc.player;
        ItemStack garment = ExoPopup.garment(player);
        ExoSpec spec = ExoProfiles.of(garment);
        if (spec == null || !ExoPopup.offersAny(garment)) {
            close(player);
            return;
        }
        layout(screen, player, garment, spec);
        GuiGraphics g = e.getGuiGraphics();
        Font font = mc.font;
        int h = height(garment, spec);
        g.pose().pushPose();
        g.pose().translate(0, 0, 300);
        g.fill(x, y, x + WIDTH, y + h, SURFACE);
        g.fill(x, y, x + WIDTH, y + HEAD, HEADER);
        outline(g, x, y, WIDTH, h);
        g.drawString(font, clip(font, garment.getHoverName(), WIDTH - PAD * 3 - CLOSE), x + PAD, y + 3, TEXT, false);
        closeButton(g, x + WIDTH - PAD - CLOSE, y + 3, onClose(e.getMouseX(), e.getMouseY()));

        int ox = screen.getGuiLeft();
        int oy = screen.getGuiTop();
        SlotLayout.Placed range = MenuSlots.range(player, ExoPopup.GROUP);
        Slot hover = null;
        String[] labels = {"wflib.exo.slot.battery", "wflib.exo.slot.belt", "wflib.exo.slot.nvg"};
        for (int k = 0; k < ExoPopup.KINDS && range != null; k++) {
            if (!ExoPopup.offers(garment, k)) {
                continue;
            }
            Slot slot = screen.getMenu().slots.get(range.first() + k);
            int sx = ox + slot.x;
            int sy = oy + slot.y;
            g.fill(sx - 1, sy - 1, sx + 17, sy + 17, CELL_BG);
            outline(g, sx - 1, sy - 1, CELL, CELL);
            ItemStack held = slot.getItem();
            if (!held.isEmpty()) {
                g.renderItem(held, sx, sy);
                g.renderItemDecorations(font, held, sx, sy);
            }
            g.pose().pushPose();
            g.pose().translate(sx - 1, sy + 19, 0);
            g.pose().scale(0.7F, 0.7F, 1);
            g.drawString(font, Component.translatable(labels[k]), 0, 0, MUTED, false);
            g.pose().popPose();
            if (e.getMouseX() >= sx && e.getMouseX() < sx + 16 && e.getMouseY() >= sy && e.getMouseY() < sy + 16) {
                hover = slot;
                g.fill(sx, sy, sx + 16, sy + 16, 0x80FFFFFF);
            }
        }
        int cy = y + HEAD + PAD + CELL + 10;
        if (spec.battery()) {
            ItemStack battery = ExoStacks.battery(garment);
            double f = battery.isEmpty() ? 0 : ArmorBattery.fraction(battery);
            g.fill(x + PAD, cy + 1, x + WIDTH - PAD, cy + 9, CELL_BG);
            g.fill(x + PAD, cy + 1, x + PAD + (int) ((WIDTH - PAD * 2) * f), cy + 9, f > 0.25 ? 0xFF4C9A50 : 0xFFC0902E);
            ExoConfig.Values cfg = ExoConfig.get();
            long stored = ArmorBattery.stored(battery);
            long cap = ArmorBattery.capacity(battery);
            double div = cfg.unit() == ExoConfig.Unit.EU ? cfg.euToFe() : 1;
            String text = battery.isEmpty() ? Component.translatable("wflib.exo.no_battery").getString()
                    : shortUnits(Math.round(stored / div)) + "/" + shortUnits(Math.round(cap / div)) + " " + cfg.unit();
            g.drawString(font, text, x + PAD + 2, cy + 1, TEXT, false);
            cy += 12;
        }
        PartState[] states = ExoStacks.states(garment, spec);
        List<ExoPart> parts = spec.parts();
        Component partTip = null;
        for (int i = 0; i < parts.size(); i++) {
            int ry = cy + i * ROW;
            g.drawString(font, Component.translatableWithFallback("wflib.exo.part." + parts.get(i).id(),
                    parts.get(i).id()), x + PAD, ry + 1, TEXT, false);
            int bx = x + 60;
            int bw = WIDTH - PAD - 60;
            double f = (double) ExoStacks.hp(garment, spec, i) / parts.get(i).maxHp();
            g.fill(bx, ry + 1, bx + bw, ry + 8, CELL_BG);
            g.fill(bx, ry + 1, bx + (int) (bw * f), ry + 8, color(states[i]));
            if (e.getMouseX() >= x + PAD && e.getMouseX() < x + WIDTH - PAD && e.getMouseY() >= ry
                    && e.getMouseY() < ry + ROW) {
                partTip = Component.translatable("wflib.exo.part.tip", parts.get(i).id(),
                        ExoStacks.hp(garment, spec, i), parts.get(i).maxHp(),
                        Component.translatable("wflib.exo.state." + states[i].name().toLowerCase(Locale.ROOT)));
            }
        }
        List<FormattedCharSequence> repair = repairLines();
        for (int i = 0; i < repair.size(); i++) {
            g.drawString(font, repair.get(i), x + PAD, y + h - PAD - (repair.size() - i) * LINE, MUTED, false);
        }
        g.pose().popPose();

        ItemStack carried = screen.getMenu().getCarried();
        g.pose().pushPose();
        g.pose().translate(0, 0, 400);
        if (hover != null && carried.isEmpty() && hover.hasItem()) {
            g.renderTooltip(font, hover.getItem(), e.getMouseX(), e.getMouseY());
        } else if (partTip != null && carried.isEmpty()) {
            g.renderTooltip(font, partTip, e.getMouseX(), e.getMouseY());
        }
        if (!carried.isEmpty() && inside(e.getMouseX(), e.getMouseY(), h)) {
            g.renderItem(carried, e.getMouseX() - 8, e.getMouseY() - 8);
            g.renderItemDecorations(font, carried, e.getMouseX() - 8, e.getMouseY() - 8);
        }
        g.pose().popPose();
    }

    private static int color(PartState s) {
        return switch (s) {
            case OK -> 0xFF4C9A50;
            case DAMAGED -> 0xFFC0B02E;
            case CRITICAL -> 0xFFD0602E;
            case DESTROYED -> 0xFF702020;
        };
    }

    private static boolean onClose(double mx, double my) {
        int cx = x + WIDTH - PAD - CLOSE;
        return mx >= cx && mx < cx + CLOSE && my >= y + 2 && my < y + HEAD - 1;
    }

    private static void closeButton(GuiGraphics g, int cx, int cy, boolean hot) {
        int c = hot ? 0xFFFF6060 : MUTED;
        for (int i = 0; i < 7; i++) {
            g.fill(cx + i, cy + i, cx + i + 1, cy + i + 1, c);
            g.fill(cx + 6 - i, cy + i, cx + 7 - i, cy + i + 1, c);
        }
    }

    private static void outline(GuiGraphics g, int ox, int oy, int w, int h) {
        g.fill(ox, oy, ox + w, oy + 1, OUTLINE);
        g.fill(ox, oy + h - 1, ox + w, oy + h, OUTLINE);
        g.fill(ox, oy, ox + 1, oy + h, OUTLINE);
        g.fill(ox + w - 1, oy, ox + w, oy + h, OUTLINE);
    }

    private static List<FormattedCharSequence> repairLines() {
        return Minecraft.getInstance().font.split(Component.translatable("wflib.exo.repair"), WIDTH - PAD * 2);
    }

    private static String clip(Font font, Component text, int room) {
        String s = text.getString();
        return font.width(s) <= room ? s : font.plainSubstrByWidth(s, Math.max(0, room - font.width("..."))) + "...";
    }

    // --- mouse ---------------------------------------------------------------------------------

    private static int currentHeight() {
        Player player = Minecraft.getInstance().player;
        ItemStack garment = ExoPopup.garment(player);
        ExoSpec spec = ExoProfiles.of(garment);
        return spec == null ? 0 : height(garment, spec);
    }

    @SubscribeEvent
    public static void pressed(ScreenEvent.MouseButtonPressed.Pre e) {
        if (!shown(e.getScreen()) || e.getButton() != 0 && e.getButton() != 1) {
            return;
        }
        double mx = e.getMouseX();
        double my = e.getMouseY();
        int h = currentHeight();
        if (h == 0 || !inside(mx, my, h)) {
            return;
        }
        if (e.getButton() == 0 && onClose(mx, my)) {
            close(Minecraft.getInstance().player);
            e.setCanceled(true);
        } else if (e.getButton() == 0 && my < y + HEAD) {
            dragDx = (int) mx - x;
            dragDy = (int) my - y;
            e.setCanceled(true);
        } else if (overSlot(e.getScreen(), mx, my) == null) {
            e.setCanceled(true);
        }
    }

    @Nullable
    private static Slot overSlot(net.minecraft.client.gui.screens.Screen screen, double mx, double my) {
        Player player = Minecraft.getInstance().player;
        SlotLayout.Placed range = MenuSlots.range(player, ExoPopup.GROUP);
        if (range == null || !(screen instanceof InventoryScreen inv)) {
            return null;
        }
        for (int k = 0; k < ExoPopup.KINDS; k++) {
            Slot slot = inv.getMenu().slots.get(range.first() + k);
            int sx = inv.getGuiLeft() + slot.x;
            int sy = inv.getGuiTop() + slot.y;
            if (slot.isActive() && mx >= sx && mx < sx + 16 && my >= sy && my < sy + 16) {
                return slot;
            }
        }
        return null;
    }

    @SubscribeEvent
    public static void dragged(ScreenEvent.MouseDragged.Pre e) {
        if (shown(e.getScreen()) && dragDx != Integer.MIN_VALUE) {
            x = (int) e.getMouseX() - dragDx;
            y = (int) e.getMouseY() - dragDy;
            e.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void released(ScreenEvent.MouseButtonReleased.Pre e) {
        if (dragDx != Integer.MIN_VALUE) {
            dragDx = Integer.MIN_VALUE;
            e.setCanceled(true);
        }
    }
}
