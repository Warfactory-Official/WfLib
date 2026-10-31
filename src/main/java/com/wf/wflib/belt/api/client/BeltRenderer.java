package com.wf.wflib.belt.api.client;

import com.wf.gemrender.direct.DirectPass;
import com.wf.gemrender.direct.DirectRenderer;
import com.wf.gemrender.gltf.GemRenderGltfModel;
import com.wf.gemrender.texture.VariantUv;
import com.wf.wflib.belt.api.BeltChain;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix4f;
import org.joml.Matrix4fc;
import org.joml.Vector4f;

/**
 * {@link BeltChain} drawn as one link model per link (link frame = model origin, X along the belt, Z = cartridge
 * axis); copies batch into one draw per pass. Render thread.
 */
public final class BeltRenderer {
    /** Clip-space y under which a link is off the bottom of the screen. */
    static final float OFF_SCREEN = -1.05F;

    private static final Matrix4f POSE = new Matrix4f(), LINK = new Matrix4f();
    private static final Vector4f CLIP = new Vector4f();

    private BeltRenderer() {
    }

    /**
     * Links {@code [0, count)} at {@code frame * link(k, shift)} (shift > 0 => link -1 too); {@code clip} (projection *
     * frame) non-null => stops after the first link below the screen. -> links drawn.
     */
    public static int submit(GemRenderGltfModel link, BeltChain chain, int count, float shift, Matrix4fc frame,
                             @Nullable Matrix4fc clip, int light, int overlay, int argb, DirectPass pass) {
        int n = Math.min(count, chain.links());
        int first = shift > 0 && n > 0 ? -1 : 0;
        for (int k = first; k < n; k++) {
            chain.link(k, shift, LINK);
            POSE.set(frame).mul(LINK);
            DirectRenderer.submit(link, null, 0, POSE, light, overlay, argb, pass, VariantUv.NONE);
            if (clip != null) {
                clip.transform(CLIP.set(LINK.m30(), LINK.m31(), LINK.m32(), 1));
                if (CLIP.w > 0 && CLIP.y / CLIP.w < OFF_SCREEN) return k - first + 1;
            }
        }
        return n - first;
    }
}
