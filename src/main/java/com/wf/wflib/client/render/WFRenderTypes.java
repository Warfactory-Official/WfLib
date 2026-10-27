package com.wf.wflib.client.render;

import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.renderer.RenderType;

public final class WFRenderTypes extends RenderType {

    public static final RenderType NORMALS = create("wf_normals",
            DefaultVertexFormat.POSITION_COLOR, VertexFormat.Mode.QUADS, 2048, false, false,
            CompositeState.builder()
                    .setShaderState(POSITION_COLOR_SHADER)
                    .setCullState(CULL)
                    .createCompositeState(false));

    public static final RenderType NORMALS_NOCULL = create("wf_normals_nocull",
            DefaultVertexFormat.POSITION_COLOR, VertexFormat.Mode.QUADS, 2048, false, false,
            CompositeState.builder()
                    .setShaderState(POSITION_COLOR_SHADER)
                    .setCullState(NO_CULL)
                    .createCompositeState(false));

    /** The translucent volume drawn around a player's own mining charges while they sneak. */
    public static final RenderType EXPLOSIVE_OVERLAY = create("wf_explosive_overlay",
            DefaultVertexFormat.POSITION_COLOR, VertexFormat.Mode.QUADS, 1536, false, true,
            CompositeState.builder()
                    .setShaderState(POSITION_COLOR_SHADER)
                    .setTransparencyState(TRANSLUCENT_TRANSPARENCY)
                    .setCullState(NO_CULL)
                    .setDepthTestState(NO_DEPTH_TEST)
                    .setWriteMaskState(COLOR_WRITE)
                    .createCompositeState(false));

    private static final java.util.Map<java.util.List<Object>, RenderType> XRAY = new java.util.concurrent.ConcurrentHashMap<>();

    /**
     * X-Ray particle sprites ({@link com.wf.wflib.client.xray.XrParticles}): textured quads tinted by vertex colour,
     * unlit, no depth write, both faces, in the particles target. ADD = ONE, ONE; ALPHA_ADD = SRC_ALPHA, ONE;
     * BLEND = over.
     */
    public static RenderType xray(net.minecraft.resources.ResourceLocation texture,
                                  com.wf.wflib.client.xray.XrDefs.Blend blend) {
        return XRAY.computeIfAbsent(java.util.List.of(texture, blend), k -> create("wf_xray_" + blend,
                DefaultVertexFormat.POSITION_TEX_COLOR, VertexFormat.Mode.QUADS, 1536, false, false,
                CompositeState.builder()
                        .setShaderState(new ShaderStateShard(
                                net.minecraft.client.renderer.GameRenderer::getPositionTexColorShader))
                        .setTextureState(new TextureStateShard(texture, true, false))
                        .setTransparencyState(switch (blend) {
                            case ADD -> ADDITIVE_TRANSPARENCY;
                            case ALPHA_ADD -> LIGHTNING_TRANSPARENCY;
                            case BLEND -> TRANSLUCENT_TRANSPARENCY;
                        })
                        .setCullState(NO_CULL)
                        .setWriteMaskState(COLOR_WRITE)
                        .setOutputState(PARTICLES_TARGET)
                        .createCompositeState(false)));
    }

    private WFRenderTypes(String name, VertexFormat format, VertexFormat.Mode mode, int bufferSize,
                          boolean affectsCrumbling, boolean sortOnUpload, Runnable setup, Runnable clear) {
        super(name, format, mode, bufferSize, affectsCrumbling, sortOnUpload, setup, clear);
    }
}
