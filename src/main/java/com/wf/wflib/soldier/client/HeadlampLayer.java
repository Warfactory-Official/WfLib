package com.wf.wflib.soldier.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.wf.gemrender.asset.GemRenderModels;
import com.wf.gemrender.asset.ModelCache;
import com.wf.gemrender.direct.ArmorAppearance;
import com.wf.gemrender.direct.GemRenderArmorModel;
import com.wf.gemrender.gltf.GemRenderGltfModel;
import com.wf.wflib.core.api.WFLib;
import com.wf.wflib.soldier.Headlamp;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.resources.PlayerSkin;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import org.jetbrains.annotations.Nullable;

/** The headlamp on the head (headband, brow lamp, rear battery): drawn on the head bone of anyone wearing one. */
final class HeadlampLayer extends RenderLayer<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> {
    private static final ModelCache.Handle<GemRenderGltfModel> MODEL = GemRenderModels.armor(
            ResourceLocation.fromNamespaceAndPath(WFLib.MODID, "models/soldier/headlamp.glb"));
    private final GemRenderArmorModel model = new GemRenderArmorModel(new ArmorAppearance() {
        @Nullable
        @Override
        public GemRenderGltfModel model(@Nullable LivingEntity wearer, ItemStack worn, EquipmentSlot at) {
            return MODEL.get();
        }
    });

    private HeadlampLayer(RenderLayerParent<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> parent) {
        super(parent);
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    static void addLayers(EntityRenderersEvent.AddLayers e) {
        for (PlayerSkin.Model skin : e.getSkins()) {
            if (e.getSkin(skin) instanceof LivingEntityRenderer r) r.addLayer(new HeadlampLayer(r));
        }
    }

    @Override
    @SuppressWarnings({"unchecked", "rawtypes"})
    public void render(PoseStack pose, MultiBufferSource buffers, int light, AbstractClientPlayer player,
                       float limbSwing, float limbSwingAmount, float partialTick, float age, float headYaw, float headPitch) {
        if (!Headlamp.wearing(player)) return;
        HumanoidModel m = model.prepare(player, new ItemStack(Headlamp.ITEM.get()), EquipmentSlot.HEAD);
        getParentModel().copyPropertiesTo(m);
        m.renderToBuffer(pose, null, light, OverlayTexture.NO_OVERLAY, -1);
    }
}
