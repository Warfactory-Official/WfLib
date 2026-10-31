package com.wf.wflib.smoking.client;

import com.wf.wflib.view.api.HandClip;
import com.wf.wflib.view.api.HandClipView;
import com.wf.wflib.smoking.Smoking;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.client.event.RenderHandEvent;
import net.neoforged.neoforge.client.event.ViewportEvent;
import org.jetbrains.annotations.Nullable;
import com.wf.wflib.core.api.WFLib;

/** First person while {@link Smoking}: GAMMA's hand and cigarette tracks replace the vanilla hands. */
public final class SmokingClient {
    private static final ResourceLocation CLIP = ResourceLocation.fromNamespaceAndPath(WFLib.MODID, "smoking/cigarette.json");
    private static final HandClipView.Prop CIGARETTE = new HandClipView.Prop("cig",
            ResourceLocation.fromNamespaceAndPath(WFLib.MODID, "models/smoking/cigarette.glb"));
    private static final HandClipView.Prop LIGHTER = new HandClipView.Prop("lighter",
            ResourceLocation.fromNamespaceAndPath(WFLib.MODID, "models/smoking/lighter.glb"));

    @Nullable
    private static HandClip clip;

    private SmokingClient() {
    }

    private static boolean smoking(@Nullable LocalPlayer p) {
        return p != null && Smoking.session(p) != null;
    }

    private static float seconds(LocalPlayer p, float partial) {
        return (Smoking.tick(p) + partial) / 20.0f;
    }

    private static HandClip clip() {
        if (clip == null) {
            clip = HandClip.load(Minecraft.getInstance().getResourceManager(), CLIP);
        }
        return clip;
    }

    /** LOW: after hand owners that cancel (PDA, artefact, carry). */
    @SubscribeEvent(priority = EventPriority.LOW)
    public static void onRenderHand(RenderHandEvent event) {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        if (smoking(player) && mc.getCameraEntity() == player) {
            HandClipView.render(event, player, clip(), seconds(player, event.getPartialTick()), CIGARETTE, LIGHTER);
        }
    }

    @SubscribeEvent
    public static void onCamera(ViewportEvent.ComputeCameraAngles event) {
        Minecraft mc = Minecraft.getInstance();
        if (smoking(mc.player) && mc.options.getCameraType().isFirstPerson()) {
            HandClipView.camera(event, clip(), seconds(mc.player, (float) event.getPartialTick()));
        }
    }
}
