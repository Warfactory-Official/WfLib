package com.wf.wflib.skin.client;

import com.wf.wflib.skin.SkinChoose;
import com.mojang.blaze3d.platform.InputConstants;
import com.wf.wflib.skin.api.Skinnable;
import com.wf.wflib.skin.api.Skins;
import net.minecraft.ChatFormatting;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.client.event.ScreenEvent;
import net.neoforged.neoforge.client.settings.KeyConflictContext;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.player.ItemTooltipEvent;
import org.lwjgl.glfw.GLFW;

import java.util.List;

/** Skin key: held item in game, hovered slot in the inventory (armour slots too); tooltip count. */
public final class SkinClient {
    // Every letter is taken by vanilla or a WF mod; the tooltip names whatever it is bound to.
    public static final KeyMapping CHOOSE = new KeyMapping("key.wflib.skin", KeyConflictContext.UNIVERSAL,
            InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_SEMICOLON, "key.categories.wflib");

    private SkinClient() {
    }

    @SubscribeEvent
    public static void keys(RegisterKeyMappingsEvent e) {
        e.register(CHOOSE);
    }

    @SubscribeEvent
    public static void setup(FMLClientSetupEvent e) {
        NeoForge.EVENT_BUS.addListener((ClientTickEvent.Post t) -> {
            Minecraft mc = Minecraft.getInstance();
            while (CHOOSE.consumeClick()) {
                if (mc.screen == null && mc.player != null
                        && choosable(mc.player.getItemInHand(InteractionHand.MAIN_HAND))) {
                    mc.setScreen(new SkinScreen(null, SkinChoose.MAIN_HAND));
                }
            }
        });
        NeoForge.EVENT_BUS.addListener(SkinClient::inventoryKey);
        NeoForge.EVENT_BUS.addListener(SkinClient::tooltip);
    }

    private static void inventoryKey(ScreenEvent.KeyPressed.Pre e) {
        if (e.getScreen() instanceof InventoryScreen inv && CHOOSE.isActiveAndMatches(InputConstants.getKey(
                e.getKeyCode(), e.getScanCode()))) {
            Slot hovered = inv.getSlotUnderMouse();
            if (hovered != null && choosable(hovered.getItem())) {
                Minecraft.getInstance().setScreen(new SkinScreen(inv, hovered.index));
                e.setCanceled(true);
            }
        }
    }

    private static void tooltip(ItemTooltipEvent e) {
        List<Skinnable.Skin> skins = Skins.of(e.getItemStack());
        if (skins.size() < 2) {
            return;
        }
        int i = Skins.index(e.getItemStack());
        e.getToolTip().add(Component.translatable("tooltip.wflib.skin", skins.get(i).name(), i + 1, skins.size())
                .withStyle(ChatFormatting.GRAY));
        e.getToolTip().add(Component.translatable("tooltip.wflib.skin.change", skins.size(),
                CHOOSE.getTranslatedKeyMessage()).withStyle(ChatFormatting.DARK_GRAY));
    }

    static boolean choosable(ItemStack stack) {
        return Skins.of(stack).size() >= 2;
    }
}
