package com.wf.wflib.armor.api.exo.client;

import com.wf.wflib.armor.api.exo.Exo;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;

public final class ExoClient {
    private ExoClient() {
    }

    @SubscribeEvent
    public static void tick(ClientTickEvent.Pre e) {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        if (player != null && !Exo.canSprint(player)) {
            mc.options.keySprint.setDown(false);
            player.sprintTriggerTime = 0;
        }
    }
}
