package com.wf.wflib.smoking.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.wf.gemrender.asset.GemRenderModels;
import com.wf.gemrender.asset.ModelCache;
import com.wf.gemrender.direct.GemRenderItemRenderer;
import com.wf.gemrender.direct.ItemAppearance;
import com.wf.gemrender.gltf.GemRenderGltfModel;
import com.wf.wflib.smoking.Smoking;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.renderer.BlockEntityWithoutLevelRenderer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.client.extensions.common.IClientItemExtensions;
import org.jetbrains.annotations.Nullable;
import com.wf.wflib.core.api.WFLib;

/** Placeholder glb in every context; per-context placement in {@code models/item/cigarette.json}. */
public final class CigaretteItemClient implements IClientItemExtensions {
    public static final CigaretteItemClient INSTANCE = new CigaretteItemClient();

    /** glb long axis (+Y, m) -> item cell fill. */
    static final float LENGTH = 0.0816f, FILL = 0.9f;

    private static ModelCache.Handle<GemRenderGltfModel> model;
    private static GemRenderItemRenderer renderer;

    private CigaretteItemClient() {
    }

    @Override
    public HumanoidModel.ArmPose getArmPose(LivingEntity entity, InteractionHand hand, ItemStack stack) {
        Smoking.Session s = Smoking.session(entity);
        return s != null && s.hand() == hand ? HumanoidModel.ArmPose.SPYGLASS : null;
    }

    @Override
    public BlockEntityWithoutLevelRenderer getCustomRenderer() {
        if (renderer == null) {
            model = GemRenderModels.handle(ResourceLocation.fromNamespaceAndPath(WFLib.MODID, "models/smoking/cigarette.glb"));
            renderer = new GemRenderItemRenderer(new ItemAppearance() {
                @Nullable
                @Override
                public GemRenderGltfModel model(ItemStack stack, ItemDisplayContext context) {
                    return model.get();
                }

                @Override
                public void transform(ItemStack stack, ItemDisplayContext context, PoseStack pose) {
                    float s = FILL / LENGTH;
                    pose.scale(s, s, s);
                }
            });
        }
        return renderer;
    }
}
