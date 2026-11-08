package com.wf.wflib.soldier.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.wf.gemrender.asset.GemRenderModels;
import com.wf.gemrender.asset.ModelCache;
import com.wf.gemrender.direct.ArmorAppearance;
import com.wf.gemrender.direct.ArmorRig;
import com.wf.gemrender.direct.GemRenderArmorModel;
import com.wf.gemrender.gltf.NodeTable;
import com.wf.gemrender.gltf.GemRenderGltfModel;
import com.wf.wflib.core.api.WFLib;
import com.wf.wflib.soldier.Headlamp;
import net.minecraft.client.Minecraft;
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
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.HashMap;
import java.util.Map;

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
        if (!Headlamp.wearing(player) && !SoldierLightClient.lit(player.getId())) return;
        HumanoidModel m = model.prepare(player, new ItemStack(Headlamp.ITEM.get()), EquipmentSlot.HEAD);
        getParentModel().copyPropertiesTo(m);
        m.renderToBuffer(pose, null, light, OverlayTexture.NO_OVERLAY, -1);
        track(player, m, pose);
    }

    /** Where the lamp's lens drew, in the world, and which way it faces and is up. */
    record Lens(double x, double y, double z, Vector3f forward, Vector3f up, long tick) {}

    private static final Map<Integer, Lens> LENSES = new HashMap<>();
    private static final String HEAD = "head", SOCKET = "socket_lamp";

    /** The lens drawn this tick or the last, or null: callers fall back to the entity's eye. */
    static Lens lens(int entity, long now) {
        Lens l = LENSES.get(entity);
        return l != null && now - l.tick <= 1 ? l : null;
    }

    /**
     * Poses the lamp model's nodes exactly as GemRender just did (the head part's rotation, then the wearer rig's own
     * joints), and reads the lens socket's matrix: the light comes from where the lamp is drawn, however the body is
     * animated, not from where the entity's eye would be.
     */
    private static void track(AbstractClientPlayer player, HumanoidModel<?> m, PoseStack pose) {
        GemRenderGltfModel gltf = MODEL.get();
        if (gltf == null) return;
        NodeTable table = gltf.layout().nodeTable();
        int head = table.slotOfName(HEAD), lens = table.slotOfName(SOCKET);
        if (lens < 0) return;
        float[] state = table.newScratch();
        int[] slots = new int[GemRenderArmorModel.PARTS.size()];
        java.util.Arrays.fill(slots, -1);
        slots[0] = head;
        if (head >= 0 && table.isPosable(head)) {
            Quaternionf rest = new Quaternionf();
            table.restRotation(head, rest);
            table.setRotation(state, head, rest.mul(new Quaternionf().rotationZYX(m.head.zRot, -m.head.yRot, -m.head.xRot)));
        }
        ArmorRig rig = GemRenderArmorModel.rig();
        if (rig != null) rig.pose(m, table, state, slots);
        int[] parents = table.parentSlots();
        java.util.ArrayDeque<Integer> chain = new java.util.ArrayDeque<>();
        for (int s = lens; s >= 0; s = parents[s]) chain.push(s);
        Matrix4f node = new Matrix4f(), local = new Matrix4f();
        for (int s : chain) {
            table.localTransform(state, s, local);
            node.mul(local);
        }
        Matrix4f at = new Matrix4f(pose.last().pose()).scale(-1, -1, 1).mul(node);
        Vector3f p = at.transformPosition(new Vector3f());
        Vec3 cam = Minecraft.getInstance().gameRenderer.getMainCamera().getPosition();
        double x = cam.x + p.x, y = cam.y + p.y, z = cam.z + p.z;
        // A shadow or mirror pass has another origin: drop what is not on the wearer.
        if (player.distanceToSqr(x, y, z) > 9) return;
        LENSES.put(player.getId(), new Lens(x, y, z, at.transformDirection(new Vector3f(0, 0, -1)).normalize(),
                at.transformDirection(new Vector3f(0, 1, 0)).normalize(), player.level().getGameTime()));
    }
}
