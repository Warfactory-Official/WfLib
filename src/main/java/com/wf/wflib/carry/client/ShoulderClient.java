package com.wf.wflib.carry.client;

import com.wf.wflib.carry.api.Shoulder;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.InputEvent;

/** Hotbar slot locked while shouldering; server resets it too. */
public final class ShoulderClient {
    private static int locked = -1;

    private ShoulderClient() {
    }

    @SubscribeEvent
    public static void scroll(InputEvent.MouseScrollingEvent e) {
        LocalPlayer p = Minecraft.getInstance().player;
        if (p != null && Minecraft.getInstance().screen == null && Shoulder.shouldering(p)) {
            e.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void tick(ClientTickEvent.Post e) {
        LocalPlayer p = Minecraft.getInstance().player;
        if (p == null || !Shoulder.shouldering(p)) {
            locked = -1;
            return;
        }
        if (locked < 0) {
            locked = p.getInventory().selected;
        }
        p.getInventory().selected = locked;
    }
}
