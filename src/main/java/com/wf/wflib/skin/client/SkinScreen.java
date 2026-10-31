package com.wf.wflib.skin.client;

import com.wf.wflib.skin.SkinChoose;
import com.wf.wflib.skin.api.Skinnable;
import com.wf.wflib.skin.api.Skins;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.PacketDistributor;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/** Skin picker for one stack: preview of the hovered (else worn) skin, one button per skin. */
final class SkinScreen extends Screen {
    private static final int BUTTON_W = 110, BUTTON_H = 20, GAP = 4, PREVIEW = 96;

    @Nullable
    private final Screen parent;
    /** {@link SkinChoose#slot}. */
    private final int slot;
    private final List<Button> buttons = new ArrayList<>();
    private int previewX, previewY;

    SkinScreen(@Nullable Screen parent, int slot) {
        super(Component.translatable("screen.wflib.skin"));
        this.parent = parent;
        this.slot = slot;
    }

    private ItemStack stack() {
        var player = minecraft.player;
        if (player == null) {
            return ItemStack.EMPTY;
        }
        return slot == SkinChoose.MAIN_HAND ? player.getItemInHand(InteractionHand.MAIN_HAND)
                : slot < player.inventoryMenu.slots.size() ? player.inventoryMenu.getSlot(slot).getItem() : ItemStack.EMPTY;
    }

    @Override
    protected void init() {
        buttons.clear();
        List<Skinnable.Skin> skins = Skins.of(stack());
        int rows = Math.max(1, (height - 60) / (BUTTON_H + GAP));
        int columns = Math.max(1, (skins.size() + rows - 1) / rows);
        int gridW = columns * (BUTTON_W + GAP) - GAP;
        int left = (width - (PREVIEW + 16 + gridW)) / 2;
        int top = 40;
        previewX = left;
        previewY = top;
        for (int i = 0; i < skins.size(); i++) {
            int index = i;
            Button b = Button.builder(Component.literal(skins.get(i).name()), x -> choose(index))
                    .bounds(left + PREVIEW + 16 + i / rows * (BUTTON_W + GAP), top + i % rows * (BUTTON_H + GAP),
                            BUTTON_W, BUTTON_H).build();
            buttons.add(addRenderableWidget(b));
        }
        refresh();
    }

    private void choose(int index) {
        PacketDistributor.sendToServer(new SkinChoose(slot, index));
        // Predicted; the server's slot sync confirms (or reverts) it.
        Skins.set(stack(), index);
        refresh();
    }

    private void refresh() {
        int worn = Skins.index(stack());
        for (int i = 0; i < buttons.size(); i++) {
            buttons.get(i).active = i != worn;
        }
    }

    @Override
    public void tick() {
        if (!SkinClient.choosable(stack())) {
            onClose();
        }
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        super.render(g, mouseX, mouseY, partialTick);
        ItemStack stack = stack();
        List<Skinnable.Skin> skins = Skins.of(stack);
        int shown = Skins.index(stack);
        for (int i = 0; i < buttons.size(); i++) {
            if (buttons.get(i).isHovered()) {
                shown = i;
            }
        }
        g.drawCenteredString(font, title, width / 2, 16, 0xFFFFFF);
        g.fill(previewX, previewY, previewX + PREVIEW, previewY + PREVIEW, 0x80000000);
        ItemStack preview = stack.copy();
        Skins.set(preview, shown);
        g.pose().pushPose();
        g.pose().translate(previewX + 8, previewY + 8, 0);
        g.pose().scale((PREVIEW - 16) / 16F, (PREVIEW - 16) / 16F, 1);
        g.renderItem(preview, 0, 0);
        g.pose().popPose();
        if (shown < skins.size()) {
            g.drawCenteredString(font, Component.translatable("tooltip.wflib.skin", skins.get(shown).name(), shown + 1,
                    skins.size()), previewX + PREVIEW / 2, previewY + PREVIEW + 6, 0xA0A0A0);
        }
    }

    @Override
    public void onClose() {
        minecraft.setScreen(parent);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
