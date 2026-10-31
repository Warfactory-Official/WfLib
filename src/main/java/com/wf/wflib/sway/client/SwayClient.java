package com.wf.wflib.sway.client;

import com.wf.wflib.core.api.WFLib;
import com.wf.wflib.sway.api.client.CameraSway;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.common.NeoForge;

public final class SwayClient {
    private static final CameraSwayMix MIX = new CameraSwayMix();
    private static final float[] DELTA = new float[2];
    private static double time;

    private SwayClient() {
    }

    public static void register() {
        CameraSway.SOURCES.register(ResourceLocation.fromNamespaceAndPath(WFLib.MODID, "pain"), 0, new PainCamera());
        NeoForge.EVENT_BUS.addListener(SwayClient::onTick);
        NeoForge.EVENT_BUS.addListener((ClientPlayerNetworkEvent.LoggingOut e) -> {
            MIX.reset(CameraSway.SOURCES.values());
            time = 0;
        });
    }

    private static void onTick(ClientTickEvent.Post e) {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer p = mc.player;
        if (p == null || mc.level == null || mc.isPaused() || mc.screen != null) {
            return;
        }
        time++;
        if (MIX.step(CameraSway.SOURCES.values(), p, time, !p.isSpectator(), DELTA)) {
            p.setYRot(p.getYRot() + DELTA[0]);
            p.setXRot(Mth.clamp(p.getXRot() + DELTA[1], -90.0F, 90.0F));
        }
    }
}
