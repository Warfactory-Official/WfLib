package com.wf.wflib.nvg.api.client;

import com.wf.wflib.core.api.OrderedRegistry;
import com.wf.wflib.view.api.ArmViewPose;
import com.wf.wflib.view.api.PlayerArms;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.InteractionHand;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.client.event.RenderHandEvent;
import org.joml.Matrix4f;
import org.joml.Vector3f;

import java.util.function.BooleanSupplier;

/** Left hand to the brow during a flip, view space; right arm collapsed (vanilla draws it). */
public final class NvgHand {
    static final Vector3f LOW = new Vector3f(-0.30f, -0.80f, -0.25f);
    public static final Vector3f BROW = new Vector3f(-0.10f, 0.18f, -0.24f);

    private static final OrderedRegistry<BooleanSupplier> OWNERS = OrderedRegistry.create("nvg", "nvg.hand_owners");
    private static final Matrix4f VIEW = new Matrix4f();
    private static final Matrix4f UPPER = new Matrix4f();
    private static final Matrix4f LOWER = new Matrix4f();
    private static final Matrix4f HIDDEN = new Matrix4f().scaling(0.0f);
    private static final Vector3f WRIST = new Vector3f();
    private static final Vector3f SHOULDER = new Vector3f();
    private static final Vector3f ELBOW = new Vector3f();

    private NvgHand() {
    }

    /** A viewmodel that animates the reach itself ({@link NvgClient#reach}) while {@code owns}. */
    public static void claim(ResourceLocation id, BooleanSupplier owns) {
        OWNERS.register(id, 0, owns);
    }

    @SubscribeEvent(priority = EventPriority.LOW)
    public static void onRenderHand(RenderHandEvent event) {
        if (event.isCanceled() || event.getHand() != InteractionHand.MAIN_HAND) {
            return;
        }
        float reach = NvgClient.reach(event.getPartialTick());
        LocalPlayer player = Minecraft.getInstance().player;
        if (reach <= 0.0f || player == null) {
            return;
        }
        for (BooleanSupplier owner : OWNERS.values()) {
            if (owner.getAsBoolean()) {
                return;
            }
        }
        PlayerArms arms = PlayerArms.get();
        if (arms == null) {
            return;
        }
        LOW.lerp(BROW, reach, WRIST);
        ArmViewPose.shoulder(true, SHOULDER);
        ArmViewPose.solve(WRIST, null, SHOULDER, ArmViewPose.LEFT_POLE, arms.lengths, UPPER, LOWER, ELBOW);
        arms.pose(1, UPPER, LOWER);
        arms.pose(0, HIDDEN, HIDDEN);
        VIEW.set(event.getPoseStack().last().pose());
        arms.submit(VIEW, event.getPackedLight(), 0xFFFFFFFF, player);
    }
}
