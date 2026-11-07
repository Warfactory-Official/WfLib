package com.wf.wflib.backpack.api.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.norwood.ahf.client.body.ArmorRenderer;
import com.wf.gemrender.asset.GemRenderModels;
import com.wf.gemrender.asset.ModelCache;
import com.wf.gemrender.direct.ArmorAppearance;
import com.wf.gemrender.direct.GemRenderArmorModel;
import com.wf.gemrender.gltf.GemRenderGltfModel;
import com.wf.wflib.backpack.api.BackpackItem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3f;

import java.util.HashMap;
import java.util.Map;
import java.util.function.BiConsumer;
import java.util.function.Function;

/** Worn backpack on the body bone (armour frame, chest slot); bends with the wearer rig. */
public final class BackpackLayer<T extends LivingEntity, M extends HumanoidModel<T>> extends RenderLayer<T, M> {
    /** Pack centre, vanilla body-part space (px / 16, +Z = back). */
    private static final Vector3f PACK = new Vector3f(0, 0.4F, 0.32F);
    private static final Map<ResourceLocation, ModelCache.Handle<GemRenderGltfModel>> MODELS = new HashMap<>();
    private final GemRenderArmorModel model = new GemRenderArmorModel(new ArmorAppearance() {
        @Nullable
        @Override
        public GemRenderGltfModel model(@Nullable LivingEntity entity, ItemStack stack, EquipmentSlot slot) {
            ResourceLocation id = ((BackpackItem) stack.getItem()).model();
            return id == null ? null : MODELS.computeIfAbsent(id, GemRenderModels::armor).get();
        }
    });
    private final Function<T, ItemStack> worn;
    private final @Nullable BiConsumer<T, Vec3> drawn;

    /** {@code drawn}: world pack centre each draw. */
    public BackpackLayer(RenderLayerParent<T, M> parent, Function<T, ItemStack> worn,
                         @Nullable BiConsumer<T, Vec3> drawn) {
        super(parent);
        this.worn = worn;
        this.drawn = drawn;
    }

    @Override
    @SuppressWarnings({"unchecked", "rawtypes"})
    public void render(PoseStack pose, MultiBufferSource buffers, int light, T entity, float limbSwing,
                       float limbSwingAmount, float partialTick, float age, float headYaw, float headPitch) {
        ItemStack stack = worn.apply(entity);
        if (!(stack.getItem() instanceof BackpackItem item) || item.model() == null) {
            return;
        }
        if (drawn != null) {
            pose.pushPose();
            getParentModel().body.translateAndRotate(pose);
            Vector3f p = pose.last().pose().transformPosition(PACK, new Vector3f());
            pose.popPose();
            Vec3 cam = Minecraft.getInstance().gameRenderer.getMainCamera().getPosition();
            drawn.accept(entity, cam.add(p.x, p.y, p.z));
        }
        HumanoidModel m = model.prepare(entity, stack, EquipmentSlot.CHEST);
        getParentModel().copyPropertiesTo(m);
        // AHF armour scope: GemRender nodes follow the jointed body; outside it only part rotations copy (ragdoll: pack
        // left at the standing body's height).
        ArmorRenderer.begin(getParentModel());
        try {
            m.renderToBuffer(pose, null, light, OverlayTexture.NO_OVERLAY, -1);
        } finally {
            ArmorRenderer.end();
        }
    }
}
