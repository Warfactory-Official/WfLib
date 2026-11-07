package com.wf.wflib.corpse.client;

import com.wf.wflib.corpse.api.CorpseMenu;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;
import org.jetbrains.annotations.NotNull;

/** Corpse layout over the viewer's inventory; palette = backpack screen's. */
public class CorpseScreen extends AbstractContainerScreen<CorpseMenu> {
    private static final int SLOT = 18, PAD = 7, HEAD = 14, GAP = 4;
    private static final int SURFACE = 0xCC48494A, HEADER = 0xD1313233, OUTLINE = 0x33FFFFFF;
    private static final int WELL_EDGE = 0x30000000, WELL = 0x50000000, TEXT = 0xFFE6E8EB, MUTED = 0xFF9DA0A4;
    private static final Component OTHER = Component.translatable("wflib.corpse.other");

    private int extraLabelY, invTop;
    private Button up, down;

    public CorpseScreen(CorpseMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
    }

    @Override
    protected void init() {
        int equipY = HEAD + GAP;
        int mainY = equipY + SLOT + GAP;
        int hotbarY = mainY + 3 * SLOT + GAP;
        extraLabelY = hotbarY + SLOT + 3;
        int extraY = extraLabelY + 10;
        invTop = extraY + CorpseMenu.EXTRA_ROWS * SLOT + 13;
        imageWidth = 9 * SLOT + PAD * 2;
        imageHeight = invTop + 3 * SLOT + GAP + SLOT + PAD;
        titleLabelX = PAD;
        titleLabelY = 3;
        inventoryLabelX = PAD;
        inventoryLabelY = invTop - 11;
        super.init();
        for (int i = 0; i < 4; i++) {
            place(CorpseMenu.ARMOR + i, PAD + i * SLOT, equipY);
        }
        place(CorpseMenu.OFFHAND, PAD + 4 * SLOT + GAP, equipY);
        place(CorpseMenu.PACK, PAD + 5 * SLOT + GAP * 3, equipY);
        place(CorpseMenu.KIT, PAD + 6 * SLOT + GAP * 3, equipY);
        for (int i = 0; i < 27; i++) {
            place(CorpseMenu.MAIN + i, PAD + (i % 9) * SLOT, mainY + (i / 9) * SLOT);
        }
        for (int i = 0; i < 9; i++) {
            place(CorpseMenu.HOTBAR + i, PAD + i * SLOT, hotbarY);
        }
        for (int i = 0; i < CorpseMenu.EXTRA_SIZE; i++) {
            place(CorpseMenu.EXTRA + i, PAD + (i % CorpseMenu.EXTRA_COLS) * SLOT,
                    extraY + (i / CorpseMenu.EXTRA_COLS) * SLOT);
        }
        for (int i = 0; i < 36; i++) {
            place(CorpseMenu.INV + i, PAD + (i % 9) * SLOT, i < 27 ? invTop + (i / 9) * SLOT : invTop + 3 * SLOT + GAP);
        }
        int bw = 9 * SLOT + PAD - (PAD + 7 * SLOT + GAP * 4);
        addRenderableWidget(Button.builder(Component.literal("\u21C4"), b -> click(CorpseMenu.TRANSFER))
                .bounds(leftPos + PAD + 7 * SLOT + GAP * 4, topPos + equipY, bw, SLOT)
                .tooltip(Tooltip.create(Component.translatable("wflib.corpse.transfer"))).build());
        up = addRenderableWidget(Button.builder(Component.literal("▲"), b -> click(CorpseMenu.UP))
                .bounds(leftPos + imageWidth - PAD - 24, topPos + extraLabelY - 1, 12, 10).build());
        down = addRenderableWidget(Button.builder(Component.literal("▼"), b -> click(CorpseMenu.DOWN))
                .bounds(leftPos + imageWidth - PAD - 12, topPos + extraLabelY - 1, 12, 10).build());
    }

    private void place(int index, int x, int y) {
        Slot slot = menu.slots.get(index);
        slot.x = x + 1;
        slot.y = y + 1;
    }

    private void click(int button) {
        minecraft.gameMode.handleInventoryButtonClick(menu.containerId, button);
    }

    @Override
    protected void containerTick() {
        super.containerTick();
        boolean scrolls = menu.extras() > CorpseMenu.EXTRA_SIZE;
        up.visible = scrolls;
        down.visible = scrolls;
        up.active = menu.scrollRow() > 0;
        down.active = (menu.scrollRow() + CorpseMenu.EXTRA_ROWS) * CorpseMenu.EXTRA_COLS < menu.extras();
    }

    @Override
    protected void renderBg(@NotNull GuiGraphics g, float partialTick, int mouseX, int mouseY) {
        int l = leftPos, t = topPos;
        g.fill(l, t, l + imageWidth, t + imageHeight, SURFACE);
        g.fill(l, t, l + imageWidth, t + HEAD, HEADER);
        g.fill(l, t, l + imageWidth, t + 1, OUTLINE);
        g.fill(l, t + imageHeight - 1, l + imageWidth, t + imageHeight, OUTLINE);
        g.fill(l, t, l + 1, t + imageHeight, OUTLINE);
        g.fill(l + imageWidth - 1, t, l + imageWidth, t + imageHeight, OUTLINE);
        for (Slot slot : menu.slots) {
            int x = l + slot.x - 1, y = t + slot.y - 1;
            g.fill(x, y, x + SLOT, y + SLOT, WELL_EDGE);
            g.fill(x + 1, y + 1, x + SLOT - 1, y + SLOT - 1, WELL);
        }
    }

    @Override
    protected void renderLabels(@NotNull GuiGraphics g, int mouseX, int mouseY) {
        g.drawString(font, title, titleLabelX, titleLabelY, TEXT, false);
        g.drawString(font, OTHER, PAD, extraLabelY, MUTED, false);
        g.drawString(font, playerInventoryTitle, inventoryLabelX, inventoryLabelY, TEXT, false);
    }

    @Override
    public void render(@NotNull GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        super.render(g, mouseX, mouseY, partialTick);
        renderTooltip(g, mouseX, mouseY);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        double y = mouseY - topPos;
        if (menu.extras() > CorpseMenu.EXTRA_SIZE && y >= extraLabelY && y < invTop - 13 && scrollY != 0) {
            click(scrollY > 0 ? CorpseMenu.UP : CorpseMenu.DOWN);
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }
}
