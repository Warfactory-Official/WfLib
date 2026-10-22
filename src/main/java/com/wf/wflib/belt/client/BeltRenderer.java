package com.wf.wflib.belt.client;

import com.wf.gemrender.direct.DirectPass;
import com.wf.gemrender.direct.DirectRenderer;
import com.wf.gemrender.gltf.GemRenderGltfModel;
import com.wf.gemrender.texture.VariantUv;
import com.wf.wflib.belt.BeltChain;
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
     * Links {@code [0, count)} at {@code frame * link(k)}; {@code clip} (projection * frame) non-null => stops after the
     * first link below the screen. -> links drawn.
     */
    public static int submit(GemRenderGltfModel link, BeltChain chain, int count, Matrix4fc frame,
                             @Nullable Matrix4fc clip, int light, int overlay, DirectPass pass) {
        int n = Math.min(count, chain.links());
        for (int k = 0; k < n; k++) {
            POSE.set(frame).mul(chain.link(k, LINK));
            DirectRenderer.submit(link, null, 0, POSE, light, overlay, 0xFFFFFFFF, pass, VariantUv.NONE);
            if (clip != null) {
                var p = chain.position(k);
                clip.transform(CLIP.set(p.x(), p.y(), p.z(), 1));
                if (CLIP.w > 0 && CLIP.y / CLIP.w < OFF_SCREEN) return k + 1;
            }
        }
        return n;
    }
}
