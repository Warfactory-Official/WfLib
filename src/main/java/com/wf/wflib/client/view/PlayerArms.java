package com.wf.wflib.client.view;

import com.wf.gemrender.direct.DirectPass;
import com.wf.gemrender.direct.DirectRenderer;
import com.wf.gemrender.direct.TextureSlots;
import com.wf.gemrender.gltf.GemRenderGltfModel;
import com.wf.gemrender.gltf.GltfPaletteLayout;
import com.wf.gemrender.gltf.NodeTable;
import com.wf.gemrender.texture.Paint;
import com.wf.gemrender.texture.VariantUv;
import com.wf.gemrender.asset.GemRenderModels;
import com.wf.gemrender.asset.ModelCache;
import com.wf.wflib.WFLib;
import com.wf.wflib.armor.client.ArmorPieceClient;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.resources.PlayerSkin;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.PlayerModelPart;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix4f;
import org.joml.Matrix4fc;

import java.util.Map;
import java.util.WeakHashMap;

/**
 * First-person player arms: skinned {@link #MODEL} (MineAssault {@code tools/ma.py player-arms}), joints
 * {@code arm_{right,left}_{upper,lower}} + {@code arm_{side}_twist{1..}} + {@code arm_{side}_fist} (= hand frame at the
 * fist end), frames +Y down the bone; segment lengths read from the rest pose. Forearm twist: joint {@code k} of
 * {@link #TWISTS} at {@code k/TWISTS} of the length turned {@code k/TWISTS} of the way to the hand. Side under worn
 * armour: whole arm turned by the twist instead (rigid sleeves; TODO twist armour sleeves). Wears the local player's skin
 * through texture slots. Render thread.
 *
 * <p>Worn glTF armour: {@code {side}_arm} node (vanilla arm part, glTF {@code S = diag(-1, -1, 1)}) remapped onto
 * the arm bones: across x {@link #ACROSS} about the vanilla arm axis, along piecewise at the elbow
 * ({@code [0, ELBOW]} -> upper, {@code [ELBOW, FIST]} -> forearm). AHF's {@code .ahf_ring} / {@code .ahf_bent}
 * joints (vertices sliced at that elbow) ride the forearm; absent => whole sleeve on the upper bone.
 */
public final class PlayerArms {
    public static final ResourceLocation MODEL = ResourceLocation.fromNamespaceAndPath(WFLib.MODID, "models/arms/player_arms.glb");
    private static final String[] SIDES = {"right", "left"};
    /** Vanilla arm, part px / 16: elbow = AHF {@code player_wide.glb} elbow pivot; fist end = MA reach. */
    private static final float ELBOW = 4 / 16F, FIST = 10 / 16F;
    /** {@code player_arms.glb} shoulder section (MA {@code malib/arms.py} PROFILE) / vanilla 4 px. */
    private static final float ACROSS = 0.125F / (4 / 16F);
    private static final String RING = ".ahf_ring", BENT = ".ahf_bent";
    /** Forearm twist segments: joints lower, twist1..{@code TWISTS - 1}, fist. */
    public static final int TWISTS = 6;
    /** Unwrapped twist past this => the wrapped value (no unbounded corkscrew). */
    private static final float MAX_TWIST = (float) (1.5 * Math.PI);

    /** Shoulder -> elbow; elbow -> fist end. */
    public record Lengths(float upper, float forearm) { }

    private static ModelCache.Handle<GemRenderGltfModel> handle;
    private static PlayerArms cached;

    private final GemRenderGltfModel model;
    private final Matrix4f[] palette;
    private final Matrix4f[] rest;
    /** [side][upper, lower, fist] node slots. */
    private final int[][] joints = new int[2][3];
    /** [side][twist k - 1] node slots. */
    private final int[][] twists = new int[2][TWISTS - 1];
    private final Matrix4f[] upper = {new Matrix4f(), new Matrix4f()}, lower = {new Matrix4f(), new Matrix4f()};
    private final float[] twist = new float[2];
    public final Lengths lengths;
    private final Map<GemRenderGltfModel, Matrix4f[]> sleeves = new WeakHashMap<>();

    private PlayerArms(GemRenderGltfModel model) {
        this.model = model;
        palette = model.newPalette();
        rest = model.restPalette();
        NodeTable table = model.layout().nodeTable();
        String[] names = {"upper", "lower", "fist"};
        for (int side = 0; side < 2; side++) {
            for (int j = 0; j < 3; j++) {
                int slot = table.slotOfName("arm_" + SIDES[side] + "_" + names[j]);
                if (slot < 0) throw new IllegalStateException(MODEL + ": no node arm_" + SIDES[side] + "_" + names[j]);
                joints[side][j] = slot;
            }
            for (int k = 1; k < TWISTS; k++) {
                int slot = table.slotOfName("arm_" + SIDES[side] + "_twist" + k);
                if (slot < 0) throw new IllegalStateException(MODEL + ": no node arm_" + SIDES[side] + "_twist" + k);
                twists[side][k - 1] = slot;
            }
        }
        lengths = new Lengths(table.restTranslation(joints[0][1], 1), table.restTranslation(joints[0][2], 1));
    }

    /** Null until loaded. */
    @Nullable
    public static PlayerArms get() {
        if (handle == null) handle = GemRenderModels.handle(MODEL);
        GemRenderGltfModel m = handle.get();
        if (m == null) return null;
        if (cached == null || cached.model != m) cached = new PlayerArms(m);
        return cached;
    }

    /** Hinge joint globals of {@code side}, view space, no forearm twist. */
    public void pose(int side, Matrix4fc upper, Matrix4fc lower) {
        this.upper[side].set(upper);
        this.lower[side].set(lower);
        twist[side] = 0;
    }

    /**
     * Hinge joint globals of {@code side}, view space ({@link ArmViewPose#solve}); {@code twist} rad, unwrapped
     * against the side's previous one.
     */
    public void pose(int side, Matrix4fc upper, Matrix4fc lower, float twist) {
        this.upper[side].set(upper);
        this.lower[side].set(lower);
        float prev = this.twist[side];
        float near = prev + (float) Math.IEEEremainder(twist - prev, 2 * Math.PI);
        this.twist[side] = Math.abs(near) > MAX_TWIST ? twist : near;
    }

    private void palette(int side, boolean rigid) {
        int[] j = joints[side];
        float t = twist[side], forearm = lengths.forearm();
        if (rigid) {
            palette[j[0]].set(upper[side]).rotateY(t);
            palette[j[1]].set(lower[side]).rotateY(t);
            for (int k = 1; k < TWISTS; k++) {
                palette[twists[side][k - 1]].set(palette[j[1]]).translate(0, forearm * k / TWISTS, 0);
            }
            palette[j[2]].set(palette[j[1]]).translate(0, forearm, 0);
            return;
        }
        palette[j[0]].set(upper[side]);
        palette[j[1]].set(lower[side]);
        for (int k = 1; k < TWISTS; k++) {
            palette[twists[side][k - 1]].set(lower[side]).translate(0, forearm * k / TWISTS, 0)
                    .rotateY(t * k / TWISTS);
        }
        palette[j[2]].set(lower[side]).translate(0, forearm, 0).rotateY(t);
    }

    /** Both arms in {@code player}'s skin + worn glTF armour arms; a skin sleeve only while its layer shows, uncovered. */
    public void submit(Matrix4f base, int light, AbstractClientPlayer player) {
        boolean[] covered = new boolean[2];
        for (ItemStack worn : player.getArmorSlots()) {
            GemRenderGltfModel armour = ArmorPieceClient.model(worn);
            if (armour != null) {
                NodeTable table = armour.layout().nodeTable();
                for (int side = 0; side < 2; side++) {
                    covered[side] |= table.slotOfName(SIDES[side] + "_arm") >= 0;
                }
            }
        }
        palette(0, covered[0]);
        palette(1, covered[1]);
        for (ItemStack worn : player.getArmorSlots()) {
            GemRenderGltfModel armour = ArmorPieceClient.model(worn);
            if (armour != null) {
                armour(armour, base, light);
            }
        }
        for (GltfPaletteLayout.SkinBlock block : model.layout().skins()) {
            int[] slots = block.jointSlots();
            for (int k = 0; k < slots.length; k++) {
                palette[block.base() + k].set(palette[slots[k]]).mul(block.inverseBind()[k]);
            }
        }
        PlayerSkin skin = player.getSkin();
        ResourceLocation texture = skin.texture();
        boolean slim = skin.model() == PlayerSkin.Model.SLIM;
        boolean rightSleeve = !covered[0] && player.isModelPartShown(PlayerModelPart.RIGHT_SLEEVE);
        boolean leftSleeve = !covered[1] && player.isModelPartShown(PlayerModelPart.LEFT_SLEEVE);
        TextureSlots slots = slot -> slot.endsWith("_slim") != slim ? null
                : switch (slim ? slot.substring(0, slot.length() - "_slim".length()) : slot) {
                    case "player_arm" -> texture;
                    case "player_sleeve_right" -> rightSleeve ? texture : null;
                    case "player_sleeve_left" -> leftSleeve ? texture : null;
                    default -> throw new IllegalStateException(MODEL + ": unknown texture slot " + slot);
                };
        DirectRenderer.submit(model, palette, null, base, light, OverlayTexture.NO_OVERLAY, 0xFFFFFFFF, DirectPass.HAND,
                VariantUv.NONE, Paint.NONE, 0, rest, slots);
    }

    /** {@code armour}'s arm nodes on the posed bones; other nodes collapsed. */
    private void armour(GemRenderGltfModel armour, Matrix4f base, int light) {
        NodeTable table = armour.layout().nodeTable();
        Matrix4f[] out = sleeves.computeIfAbsent(armour, GemRenderGltfModel::newPalette);
        for (Matrix4f m : out) {
            m.zero();
        }
        boolean any = false;
        for (int side = 0; side < 2; side++) {
            String name = SIDES[side] + "_arm";
            int owner = table.slotOfName(name);
            if (owner < 0) {
                continue;
            }
            any = true;
            float axis = (side == 0 ? -1 : 1) / 16F;
            out[owner].set(palette[joints[side][0]]).scale(ACROSS, lengths.upper() / ELBOW, ACROSS)
                    .translate(-axis, 0, 0).scale(-1, -1, 1);
            int bent = table.slotOfName(name + BENT);
            if (bent >= 0) {
                out[bent].set(palette[joints[side][1]]).scale(ACROSS, lengths.forearm() / (FIST - ELBOW), ACROSS)
                        .translate(-axis, -ELBOW, 0).scale(-1, -1, 1);
                out[table.slotOfName(name + RING)].set(out[bent]);
            }
        }
        if (any) {
            DirectRenderer.submit(armour, out, null, base, light, OverlayTexture.NO_OVERLAY, 0xFFFFFFFF, DirectPass.HAND,
                    VariantUv.NONE, Paint.NONE, 0, out);
        }
    }
}
