package com.wf.wflib.carry.client;

import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.wf.wflib.WFLib;
import com.wf.wflib.carry.Carry;
import com.wf.wflib.carry.CarryPackets;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Options;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.player.PlayerRenderer;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.resources.PlayerSkin;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.event.InputEvent;
import net.neoforged.neoforge.client.event.MovementInputUpdateEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.client.event.RenderHandEvent;
import net.neoforged.neoforge.client.event.RenderLivingEvent;
import net.neoforged.neoforge.client.event.ScreenEvent;
import net.neoforged.neoforge.client.settings.KeyConflictContext;
import net.neoforged.neoforge.network.PacketDistributor;
import org.lwjgl.glfw.GLFW;

import java.util.Optional;

@EventBusSubscriber(modid = WFLib.MODID, value = Dist.CLIENT)
public final class CarryClient {
    public static final KeyMapping CARRY = new KeyMapping("key.wflib.carry", KeyConflictContext.IN_GAME,
            InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_V, "key.categories.wflib");

    private CarryClient() {
    }

    @SubscribeEvent
    public static void keys(RegisterKeyMappingsEvent e) {
        e.register(CARRY);
    }

    @SubscribeEvent
    public static void layers(EntityRenderersEvent.AddLayers e) {
        for (PlayerSkin.Model skin : e.getSkins()) {
            if (e.getSkin(skin) instanceof LivingEntityRenderer<?, ?> r) {
                addLayer(r);
            }
        }
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static void addLayer(LivingEntityRenderer r) {
        r.addLayer(new Layer(r));
    }

    // ------------------------------------------------------------------ input

    /** Inventory / swap / drop keys consumed while carrying; drop sends the carried item. */
    @SubscribeEvent
    public static void tick(ClientTickEvent.Pre e) {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        if (player == null || mc.screen != null) {
            return;
        }
        Options o = mc.options;
        if (Carry.carrying(player)) {
            while (o.keyDrop.consumeClick()) {
                PacketDistributor.sendToServer(new CarryPackets.Drop());
            }
            while (o.keyInventory.consumeClick()) {
            }
            while (o.keySwapOffhand.consumeClick()) {
            }
            while (CARRY.consumeClick()) {
            }
            o.keySprint.setDown(false);
            player.sprintTriggerTime = 0;
            return;
        }
        while (CARRY.consumeClick()) {
            if (!player.isCreative() && !player.isSpectator()) {
                PacketDistributor.sendToServer(new CarryPackets.Pickup(aimedItem(mc, player)));
            }
        }
    }

    /** Dropped carriable item under the crosshair and nearer than any wall, else -1 (main hand). */
    private static int aimedItem(Minecraft mc, Player player) {
        Vec3 eye = player.getEyePosition();
        Vec3 end = eye.add(player.getViewVector(1).scale(Carry.REACH));
        double limit = mc.level.clip(new ClipContext(eye, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player))
                .getLocation().distanceToSqr(eye);
        int best = -1;
        for (ItemEntity item : mc.level.getEntitiesOfClass(ItemEntity.class, new AABB(eye, end).inflate(1),
                i -> i.getItem().is(Carry.CARRIABLE))) {
            Optional<Vec3> hit = item.getBoundingBox().inflate(0.4).clip(eye, end);
            if (hit.isPresent() && hit.get().distanceToSqr(eye) < limit) {
                limit = hit.get().distanceToSqr(eye);
                best = item.getId();
            }
        }
        return best;
    }

    @SubscribeEvent
    public static void attack(InputEvent.InteractionKeyMappingTriggered e) {
        Player player = Minecraft.getInstance().player;
        if (e.isAttack() && player != null && Carry.carrying(player)) {
            e.setSwingHand(false);
            e.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void move(MovementInputUpdateEvent e) {
        if (Carry.carrying(e.getEntity())) {
            e.getInput().jumping = false;
        }
    }

    @SubscribeEvent
    public static void screen(ScreenEvent.Opening e) {
        Player player = Minecraft.getInstance().player;
        if (e.getNewScreen() instanceof InventoryScreen && player != null && Carry.carrying(player)) {
            e.setCanceled(true);
        }
    }

    // ------------------------------------------------------------------ view

    /** Both hands out on the load; arms follow the head, item drawn by {@link Layer}. */
    @SubscribeEvent
    @SuppressWarnings({"unchecked", "rawtypes"})
    public static void pose(RenderLivingEvent.Pre e) {
        if (e.getEntity() instanceof Player p && Carry.carrying(p) && e.getRenderer().getModel() instanceof PlayerModel m) {
            m.rightArmPose = HumanoidModel.ArmPose.CROSSBOW_HOLD;
            m.leftArmPose = HumanoidModel.ArmPose.CROSSBOW_HOLD;
        }
    }

    /** First person: vanilla hands replaced by the load between two forward arms. */
    @SubscribeEvent
    public static void hands(RenderHandEvent e) {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        if (player == null || !Carry.carrying(player)) {
            return;
        }
        e.setCanceled(true);
        if (e.getHand() != InteractionHand.MAIN_HAND) {
            return;
        }
        PoseStack pose = e.getPoseStack();
        MultiBufferSource buffers = e.getMultiBufferSource();
        int light = e.getPackedLight();
        pose.pushPose();
        pose.translate(0, -0.45F, -0.85F);
        mc.getItemRenderer().renderStatic(Carry.carried(player), ItemDisplayContext.FIRST_PERSON_RIGHT_HAND, light,
                OverlayTexture.NO_OVERLAY, pose, buffers, mc.level, 0);
        pose.popPose();
        arm(pose, buffers, light, player, HumanoidArm.RIGHT);
        arm(pose, buffers, light, player, HumanoidArm.LEFT);
    }

    /** Vanilla resting first-person arm transform (no swing, no equip). */
    private static void arm(PoseStack pose, MultiBufferSource buffers, int light, AbstractClientPlayer player,
                            HumanoidArm side) {
        float f = side == HumanoidArm.RIGHT ? 1 : -1;
        pose.pushPose();
        pose.translate(f * 0.64F, -0.6F, -0.72F);
        pose.mulPose(Axis.YP.rotationDegrees(f * 45));
        pose.translate(f * -1, 3.6F, 3.5F);
        pose.mulPose(Axis.ZP.rotationDegrees(f * 120));
        pose.mulPose(Axis.XP.rotationDegrees(200));
        pose.mulPose(Axis.YP.rotationDegrees(f * -135));
        pose.translate(f * 5.6F, 0, 0);
        PlayerRenderer renderer = (PlayerRenderer) Minecraft.getInstance().getEntityRenderDispatcher().getRenderer(player);
        if (side == HumanoidArm.RIGHT) {
            renderer.renderRightHand(pose, buffers, light, player);
        } else {
            renderer.renderLeftHand(pose, buffers, light, player);
        }
        pose.popPose();
    }

    /** Third person: load centred between the extended arms, bore forward, tilted with the head. */
    private static final class Layer extends RenderLayer<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> {
        Layer(RenderLayerParent<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> parent) {
            super(parent);
        }

        @Override
        public void render(PoseStack pose, MultiBufferSource buffers, int light, AbstractClientPlayer player,
                           float limbSwing, float limbSwingAmount, float partialTick, float age, float headYaw,
                           float headPitch) {
            if (!Carry.carrying(player)) {
                return;
            }
            pose.pushPose();
            pose.translate(0, 1.501F, 0);
            pose.scale(-1, -1, 1);
            pose.translate(0, 1.35F, 0);
            pose.mulPose(Axis.YP.rotationDegrees(-headYaw));
            pose.mulPose(Axis.XP.rotationDegrees(headPitch));
            pose.translate(0, -0.1F, 0.55F);
            pose.mulPose(Axis.YP.rotationDegrees(90));
            Minecraft.getInstance().getItemRenderer().renderStatic(Carry.carried(player), ItemDisplayContext.GROUND,
                    light, OverlayTexture.NO_OVERLAY, pose, buffers, player.level(), player.getId());
            pose.popPose();
        }
    }
}
