package com.wf.wflib.wiki.client;

import com.mojang.blaze3d.platform.InputConstants;
import com.wf.wflib.WFLib;
import com.wf.wflib.pda.Pda;
import com.wf.wflib.pda.client.PdaClient;
import com.wf.wflib.wiki.Article;
import com.wf.wflib.wiki.Wiki;
import net.minecraft.ChatFormatting;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ServerboundPlayerActionPacket;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.client.event.ScreenEvent;
import net.neoforged.neoforge.client.settings.KeyConflictContext;
import net.neoforged.neoforge.event.entity.player.ItemTooltipEvent;
import net.neoforged.neoforge.event.TagsUpdatedEvent;
import org.lwjgl.glfw.GLFW;

/** Tutorial key (F1): item under the cursor / in hand -> its article, PDA pulled out and opened on it. */
@EventBusSubscriber(modid = WFLib.MODID, value = Dist.CLIENT)
public final class WikiAccess {
    public static final KeyMapping OPEN = new KeyMapping("key.wflib.wiki_open", KeyConflictContext.UNIVERSAL,
            InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_F1, "key.categories.wflib");

    private WikiAccess() {
    }

    @SubscribeEvent
    public static void register(RegisterKeyMappingsEvent event) {
        event.register(OPEN);
    }

    @SubscribeEvent
    static void tags(TagsUpdatedEvent event) {
        Wiki.invalidateBindings();
    }

    @SubscribeEvent
    static void tooltip(ItemTooltipEvent event) {
        if (Wiki.forStack(event.getItemStack()) == null) {
            return;
        }
        Component line = PdaClient.carried()
                ? Component.translatable("wflib.wiki.tooltip", OPEN.getTranslatedKeyMessage())
                : Component.translatable("wflib.wiki.tooltip_no_pda");
        event.getToolTip().add(line.copy().withStyle(ChatFormatting.DARK_AQUA, ChatFormatting.ITALIC));
    }

    @SubscribeEvent
    static void screenKey(ScreenEvent.KeyPressed.Pre event) {
        if (!OPEN.matches(event.getKeyCode(), event.getScanCode()) || event.getScreen().getFocused() instanceof EditBox) {
            return;
        }
        ItemStack stack = ItemStack.EMPTY;
        if (event.getScreen() instanceof AbstractContainerScreen<?> screen) {
            Slot slot = screen.getSlotUnderMouse();
            if (slot != null) {
                stack = slot.getItem();
            }
        }
        Article article = Wiki.forStack(stack);
        Minecraft mc = Minecraft.getInstance();
        if (article != null && mc.player != null && Pda.carries(mc.player)) {
            event.setCanceled(true);
            mc.tell(() -> open(article));
        }
    }

    /** Called from the keyboard mixin with no Screen open. @return true = key consumed (no hide-GUI toggle). */
    public static boolean key(int key, int scan) {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        if (player == null || mc.screen != null || !OPEN.matches(key, scan) || !Pda.carries(player)) {
            return false;
        }
        Article article = Wiki.forStack(player.getMainHandItem());
        if (article == null) {
            article = Wiki.forStack(player.getOffhandItem());
        }
        if (article == null) {
            return false;
        }
        open(article);
        return true;
    }

    private static void open(Article article) {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        if (player == null) {
            return;
        }
        if (mc.screen != null) {
            mc.setScreen(null);
        }
        if (!equip(mc, player)) {
            return;
        }
        PdaClient.raiseWhenHeld(() -> {
            PdaClient.open(WikiApp.instance());
            WikiApp.show(article, null);
        });
    }

    /** PDA into the main hand; the swap round-trips the server, {@link PdaClient#raiseWhenHeld} waits for it. */
    private static boolean equip(Minecraft mc, LocalPlayer player) {
        if (Pda.is(player.getMainHandItem())) {
            return true;
        }
        Inventory inventory = player.getInventory();
        if (Pda.is(player.getOffhandItem())) {
            player.connection.send(new ServerboundPlayerActionPacket(
                    ServerboundPlayerActionPacket.Action.SWAP_ITEM_WITH_OFFHAND, BlockPos.ZERO, Direction.DOWN));
            return true;
        }
        for (int i = 0; i < Inventory.getSelectionSize(); i++) {
            if (Pda.is(inventory.getItem(i))) {
                inventory.selected = i;
                return true;
            }
        }
        for (int i = Inventory.getSelectionSize(); i < inventory.items.size(); i++) {
            if (Pda.is(inventory.getItem(i))) {
                mc.gameMode.handleInventoryMouseClick(player.inventoryMenu.containerId, i, inventory.selected,
                        ClickType.SWAP, player);
                return true;
            }
        }
        return false;
    }
}
