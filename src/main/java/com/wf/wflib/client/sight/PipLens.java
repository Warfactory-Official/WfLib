package com.wf.wflib.client.sight;

import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.mojang.blaze3d.vertex.VertexSorting;
import com.wf.gemrender.gltf.GemRenderGltfModel;
import com.wf.gemrender.gltf.GltfMesh;
import com.wf.gemrender.gltf.MeshGeometry;
import com.wf.gemrender.gltf.skin.BoneAttributeCodec;
import com.wf.gemrender.gltf.skin.SkinnedBounds;
import com.wf.wflib.client.cam.OffscreenView;
import com.wf.wflib.client.cam.SightSensor;
import com.wf.wflib.drone.cam.CameraMode;
import com.wf.wflib.sight.Optic;
import dev.engine_room.flywheel.api.model.Model;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix4f;
import org.joml.Matrix4fc;
import org.joml.Vector3f;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL13;
import org.lwjgl.opengl.GL15;
import org.lwjgl.opengl.GL33;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Picture-in-picture optic: the level from the eye ({@link OffscreenView} shared grid) into a square target at
 * {@link #frame}, eye-box shadow + label composed into it, drawn by {@link #draw} on the lens mesh's own triangles
 * (each vertex projected along its eye ray onto the picture plane; depth-tested => rings / hoods in front clip it).
 * Lens geometry = last {@link #draw}'s. Scope frame F = view space at full ADS without sway: eye = exit pupil, picture
 * axes = F x/y. One instance per optic user; render thread.
 */
public final class PipLens {
    /** Composed label, lens coordinates (radii, reticle centre {@code x, y}, line {@code height}). */
    public interface Label {
        void draw(float x, float y, float height);
    }

    /** Camera of the pass: {@code {x, y, z, yaw, pitch, roll}}; called only when a pass runs. */
    public interface Pose {
        void pose(double[] out);
    }

    private static final int SEGMENTS = 48;
    /** Polygon offset (factor, units) over the coplanar ocular mesh; layers share it (equal depth, LEQUAL). */
    private static final float OFFSET_FACTOR = -1, OFFSET_UNITS = -8;
    /**
     * Lens vertices scaled toward the camera (same pixels): the ocular mesh drawn through another matrix path (Flywheel
     * instance) differs by more than the polygon offset (~5e-8 m at 7 cm) => z-fight.
     */
    private static final float PULL = 0.999F;
    private static final ResourceLocation DUPLEX = ResourceLocation.fromNamespaceAndPath("wflib", "pip_duplex");
    private static final int DUPLEX_PX = 512;
    private static boolean duplexReady;
    /** Lens triangles in model space (rest pose), per model + lens slot. */
    private static final Map<GemRenderGltfModel, Map<Integer, float[]>> LENS_TRIS = new IdentityHashMap<>();
    private static final Map<GemRenderGltfModel, Matrix4f[]> REST = new IdentityHashMap<>();
    /** Rangefinder-style label centre, line height; lens radii from the reticle centre. */
    private static final float LABEL_X = 0.3F, LABEL_Y = 0.42F, LABEL_H = 0.12F;
    /** Shadow edge width, lens radii. */
    private static final float SOFT = 0.08F;
    /** Magnification approach rate between zoom levels, 1/s. */
    private static final float LEVEL_RATE = 10F;
    /** Picture FOV above, degrees => no pass (lens at the eye). */
    public static final float MAX_FOV = 150F;
    /** GPU timestamp pairs in flight; read when available. */
    private static final int FRAMES = 4;
    /** No pass this long => view + target freed. */
    private static final long IDLE_NANOS = 2_000_000_000L;

    private final Matrix4f lensView = new Matrix4f();
    @Nullable
    private float[] lensTris;
    private final Vector3f lv = new Vector3f();
    @Nullable
    private Label label;
    @Nullable
    private SightSensor view;
    @Nullable
    private CameraMode sensor;
    @Nullable
    private TextureTarget target;
    static final int SIZE_STEP = 128, MIN_SIZE = 128;
    static final float SHRINK = 0.6F;
    private final double[] pose = new double[6];
    private float driftX, driftY;
    /** Share of the rendered picture the lens shows ({@link ScopeMath#crop}). */
    private float crop = 1;

    private boolean lensValid;
    /** tan of the field stop's angular radius from the exit pupil: lens radius / eye relief. */
    private float fieldTan;
    /** Lens apparent size / field stop: lens coords x this = picture coords (closer => image kept, ring beyond). */
    private float fieldScale = 1;
    /** tan of the last picture's half FOV. */
    private float pictureTan = 1;
    /** Shadow hole {@code {x, y, radius}}, lens radii. */
    public final float[] hole = new float[3];
    /** Last lens on screen, window px {@code {x, y, radius}}. */
    public final float[] lensPx = new float[3];
    /** Pass drawn this frame => lens drawn by {@link #draw}. */
    private boolean rendered;

    @Nullable
    private Object owner;
    @Nullable
    private Optic optic;
    private int level;
    /** Magnification approached; listed level ({@link #cycle}) or {@link #scroll} within the listed range. */
    private float wanted = 1;
    private float magnification = 1;
    private long lastNanos;
    private long lastPass;

    private final Matrix4f fl = new Matrix4f();
    private final Matrix4f ortho = new Matrix4f();
    private final Matrix4f g = new Matrix4f();
    private final Vector3f c = new Vector3f();
    private final Vector3f v = new Vector3f();
    private final Vector3f a = new Vector3f();
    private final Vector3f x = new Vector3f();
    private final Vector3f y = new Vector3f();
    private final Vector3f p = new Vector3f();
    private final float[] box = new float[6];

    /** GPU timestamps only after the first {@link #stats()} call. */
    private boolean timing;
    private int[] queries;
    private final boolean[] pending = new boolean[FRAMES];
    private int frame;
    private long cpuNanos;
    private long gpuNanos;
    private int cpuSamples;
    private int gpuSamples;

    public boolean shown() {
        return rendered;
    }

    public float magnification() {
        return magnification;
    }

    public int level() {
        return level;
    }

    public float crop() {
        return crop;
    }

    /**
     * Optic in use ({@code null} => none); {@code owner} or {@code optic} changed => first zoom level. {@code sensor}:
     * sight chain, null => plain.
     */
    public void select(@Nullable Object owner, @Nullable Optic optic, @Nullable CameraMode sensor,
                       @Nullable Label label) {
        this.sensor = sensor;
        this.label = label;
        if (owner != this.owner || optic != this.optic) {
            this.owner = owner;
            this.optic = optic;
            level = 0;
            magnification = wanted = optic == null ? 1 : optic.zoom().getFirst();
        }
        if (optic == null) {
            lensValid = false;
        }
    }

    @Nullable
    public Optic optic() {
        return optic;
    }

    /** Next / previous zoom level of the shown optic. */
    public void cycle(int dir) {
        if (rendered && optic != null) {
            level = ScopeMath.step(level, optic.zoom().size(), dir);
            wanted = optic.zoom().get(level);
        }
    }

    /** {@link ScopeMath#ZOOM_STEP} per notch on a shown multi-level optic; true => consumed. */
    public boolean scroll(double notches) {
        if (!rendered || optic == null || optic.zoom().size() < 2 || notches == 0) {
            return false;
        }
        wanted = ScopeMath.adjust(wanted, optic.zoom(), notches);
        return true;
    }

    /**
     * Scope pass for this frame from the last {@link #draw}; {@code active} false => none (freed after idle).
     *
     * @param maxSize picture px cap; picture = last picture screen diameter / {@link #crop} (texel per screen px)
     */
    public boolean frame(DeltaTracker delta, boolean active, int maxSize, Entity viewer,
                         Pose camera) {
        rendered = false;
        Minecraft mc = Minecraft.getInstance();
        long now = System.nanoTime();
        if (optic == null) {
            close();
            return false;
        }
        if (!active || !lensValid || mc.level == null) {
            if (now - lastPass > IDLE_NANOS) {
                close();
            }
            return false;
        }
        lastPass = now;
        int size = pictureSize(2 * lensPx[2] / (crop * fieldScale), target == null ? 0 : target.width, maxSize);
        if (target == null || target.width != size) {
            if (target != null) {
                target.destroyBuffers();
            }
            target = new TextureTarget(size, size, true, Minecraft.ON_OSX);
            // Boxy PRECISE distant-entity depth asks the pass's "main" target for a stencil.
            target.enableStencil();
            target.setClearColor(0, 0, 0, 1);
            // Lens past the field stop (eye inside the relief) samples outside the picture => black, not edge texels.
            RenderSystem.bindTexture(target.getColorTextureId());
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_S, GL13.GL_CLAMP_TO_BORDER);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_T, GL13.GL_CLAMP_TO_BORDER);
            RenderSystem.bindTexture(0);
        }
        if (view == null) {
            view = new SightSensor(OffscreenView.sharedGrid());
        }
        int slot = -1;
        if (timing) {
            if (queries == null) {
                queries = new int[FRAMES * 2];
                GL15.glGenQueries(queries);
            }
            for (int i = 0; i < FRAMES; i++) {
                if (pending[i] && GL15.glGetQueryObjecti(queries[i * 2 + 1], GL15.GL_QUERY_RESULT_AVAILABLE) != 0) {
                    gpuNanos += GL33.glGetQueryObjecti64(queries[i * 2 + 1], GL15.GL_QUERY_RESULT)
                            - GL33.glGetQueryObjecti64(queries[i * 2], GL15.GL_QUERY_RESULT);
                    gpuSamples++;
                    pending[i] = false;
                }
            }
            slot = frame++ % FRAMES;
            if (pending[slot]) {
                slot = -1;
            } else {
                GL33.glQueryCounter(queries[slot * 2], GL33.GL_TIMESTAMP);
            }
        }

        float fov = ScopeMath.fovDeg(fieldTan, magnification);
        if (!(fov < MAX_FOV)) {
            return false;
        }
        pictureTan = (float) Math.tan(Math.toRadians(fov) / 2);
        float pass = ScopeMath.passFovDeg(fov, mc.gameRenderer.getDepthFar());
        crop = ScopeMath.crop(fov, pass);
        camera.pose(pose);
        rendered = view.render(viewer, new Vec3(pose[0], pose[1], pose[2]), (float) pose[3], (float) pose[4],
                (float) pose[5], pass, target, delta, sensor);
        if (rendered) {
            compose(mc);
        }
        if (slot >= 0) {
            GL33.glQueryCounter(queries[slot * 2 + 1], GL33.GL_TIMESTAMP);
            pending[slot] = rendered;
        }
        if (rendered) {
            cpuNanos += System.nanoTime() - now;
            cpuSamples++;
        }
        return rendered;
    }

    /**
     * Picture px for {@code wanted} (lens screen px / crop): up to {@link #SIZE_STEP} multiples, within
     * {@link #MIN_SIZE}..{@code maxSize}; {@code current} kept while >= wanted and wanted >= {@link #SHRINK} x it (no
     * reallocation per frame as the lens moves).
     */
    static int pictureSize(float wanted, int current, int maxSize) {
        int size = Math.clamp((int) Math.ceil(wanted / SIZE_STEP) * SIZE_STEP, MIN_SIZE, Math.max(MIN_SIZE, maxSize));
        return current >= size && size >= SHRINK * current && current <= maxSize ? current : size;
    }

    /** Shadow over the lens part ({@link #crop}) of the level picture; main target bound after. */
    private void compose(Minecraft mc) {
        TextureTarget t = target;
        t.bindWrite(true);
        Matrix4f savedProjection = RenderSystem.getProjectionMatrix();
        VertexSorting savedSorting = RenderSystem.getVertexSorting();
        RenderSystem.setProjectionMatrix(ortho.setOrtho(-1 / crop, 1 / crop, -1 / crop, 1 / crop, -1, 1),
                VertexSorting.ORTHOGRAPHIC_Z);
        var mv = RenderSystem.getModelViewStack();
        mv.pushMatrix();
        mv.identity();
        RenderSystem.applyModelViewMatrix();
        RenderSystem.disableDepthTest();
        RenderSystem.depthMask(false);
        RenderSystem.disableCull();
        // Level passes leave alpha 0 on terrain; the disc shader discards alpha 0.
        RenderSystem.colorMask(false, false, false, true);
        RenderSystem.clearColor(0, 0, 0, 1);
        RenderSystem.clear(GL11.GL_COLOR_BUFFER_BIT, Minecraft.ON_OSX);
        RenderSystem.colorMask(true, true, true, true);
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        try {
            shadow(hole[0], hole[1], hole[2]);
            if (label != null) {
                label.draw(driftX + LABEL_X, driftY - LABEL_Y, LABEL_H);
            }
        } finally {
            RenderSystem.disableBlend();
            RenderSystem.enableCull();
            RenderSystem.depthMask(true);
            RenderSystem.enableDepthTest();
            mv.popMatrix();
            RenderSystem.applyModelViewMatrix();
            RenderSystem.setProjectionMatrix(savedProjection, savedSorting);
            mc.getMainRenderTarget().bindWrite(true);
        }
    }

    /** Black outside the hole: soft ring {@code R -> R + SOFT}, then solid to beyond the corners. */
    private static void shadow(float cx, float cy, float r) {
        RenderSystem.setShader(GameRenderer::getPositionColorShader);
        BufferBuilder b = Tesselator.getInstance().begin(VertexFormat.Mode.TRIANGLE_STRIP,
                DefaultVertexFormat.POSITION_COLOR);
        float inner = Math.max(r, 0);
        float outer = inner + SOFT;
        float far = 4 + Math.abs(cx) + Math.abs(cy);
        for (int i = 0; i <= SEGMENTS; i++) {
            double t = 2 * Math.PI * i / SEGMENTS;
            float c = (float) Math.cos(t);
            float s = (float) Math.sin(t);
            b.addVertex(cx + c * inner, cy + s * inner, 0).setColor(0, 0, 0, r > 0 ? 0 : 255);
            b.addVertex(cx + c * outer, cy + s * outer, 0).setColor(0, 0, 0, 255);
        }
        BufferUploader.drawWithShader(b.buildOrThrow());
        b = Tesselator.getInstance().begin(VertexFormat.Mode.TRIANGLE_STRIP, DefaultVertexFormat.POSITION_COLOR);
        for (int i = 0; i <= SEGMENTS; i++) {
            double t = 2 * Math.PI * i / SEGMENTS;
            float c = (float) Math.cos(t);
            float s = (float) Math.sin(t);
            b.addVertex(cx + c * outer, cy + s * outer, 0).setColor(0, 0, 0, 255);
            b.addVertex(cx + c * far, cy + s * far, 0).setColor(0, 0, 0, 255);
        }
        BufferUploader.drawWithShader(b.buildOrThrow());
    }

    /**
     * After the optic's model is drawn: lens of {@link #optic} from {@code model} slot {@code lens}, and last pass's
     * picture on it. Model space = the model's rest-posed space.
     *
     * @param modelToF    model -> scope frame F
     * @param fToView     F -> this frame's view space
     * @param modelToView model -> this frame's view space (lens triangles; model view stack identity while drawn)
     * @param flexDeg     reticle drift {@code {yaw, pitch}} deg
     */
    public void draw(GemRenderGltfModel model, int lens, Matrix4fc modelToF, Matrix4fc fToView, Matrix4fc modelToView,
                     Matrix4fc proj, float[] flexDeg) {
        lensValid = false;
        Optic o = optic;
        if (o == null) {
            return;
        }
        long now = System.nanoTime();
        float dt = lastNanos == 0 ? 0 : Math.min((now - lastNanos) / 1e9F, 0.1F);
        lastNanos = now;
        magnification += (wanted - magnification) * Math.min(1, dt * LEVEL_RATE);
        if (lens < 0 || !box(model.bounds(), lens)) {
            return;
        }
        fl.set(modelToF).mul(rest(model)[lens]);
        float bx = (box[0] + box[3]) / 2, by = (box[1] + box[4]) / 2, bz = (box[2] + box[5]) / 2;
        fl.transformPosition(bx, by, bz, c);
        if (c.z > -1e-4F) {
            return;
        }
        // Lens radius = largest box half-axis across the F view axis.
        float r = 0;
        for (int i = 0; i < 3; i++) {
            float h = (box[i + 3] - box[i]) / 2;
            fl.transformDirection(i == 0 ? h : 0, i == 1 ? h : 0, i == 2 ? h : 0, v);
            r = Math.max(r, (float) Math.sqrt(v.x * v.x + v.y * v.y));
        }
        float dF = -c.z;
        if (r < 1e-6F) {
            return;
        }
        Float er = o.eyeRelief();
        float relief = er != null ? er : dF;
        fieldTan = r / relief;
        driftX = (float) Math.tan(Math.toRadians(flexDeg[0])) / fieldTan;
        driftY = (float) Math.tan(Math.toRadians(flexDeg[1])) / fieldTan;
        g.set(fToView);
        g.transformPosition(p.set(0, 0, relief - dF));
        g.transformPosition(c);
        float scale = g.transformDirection(x.set(1, 0, 0)).length();
        x.div(scale);
        g.transformDirection(y.set(0, 1, 0)).normalize();
        g.transformDirection(a.set(0, 0, -1)).normalize();
        r *= scale;
        if (c.z > -1e-4F) {
            return;
        }
        lensValid = true;
        fieldScale = r / (c.dot(a) * fieldTan);
        var win = Minecraft.getInstance().getWindow();
        lensPx[0] = (1 + proj.m00() * c.x / -c.z) / 2 * win.getWidth();
        lensPx[1] = (1 - proj.m11() * c.y / -c.z) / 2 * win.getHeight();
        lensPx[2] = r / -c.z * proj.m11() / 2 * win.getHeight();
        ScopeMath.shadow(p, a, x, y, r, relief * scale, hole);

        if (!rendered || target == null) {
            return;
        }
        lensTris = LENS_TRIS.computeIfAbsent(model, k -> new HashMap<>())
                .computeIfAbsent(lens, k -> lensTriangles(model, lens));
        lensView.set(modelToView);
        lens(o, r);
    }

    /** No lens this frame (model not loaded): no pass next frame. */
    public void miss() {
        lensValid = false;
    }

    /** Rest palette of {@code model}, cached. */
    public static Matrix4f[] rest(GemRenderGltfModel model) {
        Matrix4f[] r = REST.get(model);
        if (r == null) {
            if (REST.size() > 256) {
                REST.clear();
                LENS_TRIS.clear();
            }
            r = model.restPalette();
            REST.put(model, r);
        }
        return r;
    }

    /** Triangles whose dominant joint is {@code slot}, rest-posed, model space ({@code x0 y0 z0 x1 ..}). */
    private static float[] lensTriangles(GemRenderGltfModel model, int slot) {
        Matrix4f rest = rest(model)[slot];
        Vector3f lv = new Vector3f();
        List<Float> out = new ArrayList<>();
        for (Model.ConfiguredMesh c : model.model().meshes()) {
            if (!(c.mesh() instanceof GltfMesh gm)) {
                continue;
            }
            var g = gm.geometry();
            for (int t = 0; t + 2 < g.indexCount(); t += 3) {
                boolean on = true;
                for (int v = 0; v < 3 && on; v++) {
                    on = dominant(g, g.index(t + v)) == slot;
                }
                if (!on) {
                    continue;
                }
                for (int v = 0; v < 3; v++) {
                    int i = g.index(t + v);
                    rest.transformPosition(g.position(i, 0), g.position(i, 1), g.position(i, 2), lv);
                    out.add(lv.x);
                    out.add(lv.y);
                    out.add(lv.z);
                }
            }
        }
        float[] a = new float[out.size()];
        for (int i = 0; i < a.length; i++) {
            a[i] = out.get(i);
        }
        return a;
    }

    private static int dominant(MeshGeometry g, int i) {
        int packed = g.packedJoints(i), best = -1;
        float w = 0;
        for (int k = 0; k < BoneAttributeCodec.INFLUENCES; k++) {
            float wk = g.weightChannel(i, k);
            if (wk > w) {
                w = wk;
                best = BoneAttributeCodec.unpackJoint(packed, k);
            }
        }
        return best;
    }

    private boolean box(SkinnedBounds bounds, int slot) {
        for (int i = 0; i < bounds.size(); i++) {
            if (bounds.slot(i) == slot) {
                bounds.box(i, box);
                return true;
            }
        }
        return false;
    }

    /**
     * Picture, then reticle (drifted) / duplex and mask, on the lens triangles; uv = the vertex projected along its eye
     * ray onto the picture plane (centre {@link #c}, axes {@link #x} / {@link #y}, radius {@code r}, view space).
     */
    private void lens(Optic s, float r) {
        if (lensTris == null || lensTris.length == 0) {
            return;
        }
        var mv = RenderSystem.getModelViewStack();
        mv.pushMatrix();
        mv.identity();
        RenderSystem.applyModelViewMatrix();
        RenderSystem.enableDepthTest();
        RenderSystem.depthFunc(GL11.GL_LEQUAL);
        RenderSystem.enablePolygonOffset();
        RenderSystem.polygonOffset(OFFSET_FACTOR, OFFSET_UNITS);
        RenderSystem.depthMask(true);
        RenderSystem.disableBlend();
        RenderSystem.disableCull();
        RenderSystem.setShader(GameRenderer::getPositionTexShader);
        RenderSystem.setShaderTexture(0, target.getColorTextureId());
        // Colour attachment origin bottom-left = picture +y up.
        lensPass(r, crop, crop, 0, 0, fieldScale);
        RenderSystem.depthMask(false);
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        if (s.reticle() != null) {
            RenderSystem.setShaderTexture(0, ReticleTextures.id(s.reticle()));
        } else {
            RenderSystem.setShaderTexture(0, duplex());
        }
        // reticleSize > 0: image width in the world; reticleZoom > 0 => at that magnification (second focal plane).
        // Image narrower than the picture (k > 1) => fills it: true subtension only once the image spans the lens.
        float k = s.reticleSize() <= 0 ? 1 : Math.min(1, pictureTan
                * (s.reticleZoom() > 0 ? magnification / s.reticleZoom() : 1)
                / (float) Math.tan(s.reticleSize() * ReticleMath.MOA_RAD / 2));
        lensPass(r, k, -k, driftX, driftY, fieldScale);
        if (s.mask() != null) {
            RenderSystem.setShaderTexture(0, s.mask());
            lensPass(r, 1, -1, 0, 0, 1);
        }
        RenderSystem.disableBlend();
        RenderSystem.enableCull();
        RenderSystem.polygonOffset(0, 0);
        RenderSystem.disablePolygonOffset();
        RenderSystem.depthMask(true);
        mv.popMatrix();
        RenderSystem.applyModelViewMatrix();
    }

    /**
     * Lens triangles, uv = {@code 0.5 + 0.5 (sx (q lx - ox), sy (q ly - oy))}; lens coords {@code lx, ly} in radii,
     * {@code q} lens -> layer coords.
     */
    private void lensPass(float r, float sx, float sy, float ox, float oy, float q) {
        float[] tris = lensTris;
        float plane = c.dot(a);
        BufferBuilder b = Tesselator.getInstance().begin(VertexFormat.Mode.TRIANGLES, DefaultVertexFormat.POSITION_TEX);
        for (int i = 0; i < tris.length; i += 3) {
            lensView.transformPosition(tris[i], tris[i + 1], tris[i + 2], lv);
            float along = lv.dot(a);
            float k = Math.abs(along) < 1e-6F ? 1 : plane / along;
            float dx = lv.x * k - c.x, dy = lv.y * k - c.y, dz = lv.z * k - c.z;
            float lx = q * (dx * x.x + dy * x.y + dz * x.z) / r, ly = q * (dx * y.x + dy * y.y + dz * y.z) / r;
            b.addVertex(lv.x * PULL, lv.y * PULL, lv.z * PULL).setUv(0.5F + 0.5F * sx * (lx - ox), 0.5F + 0.5F * sy * (ly - oy));
        }
        BufferUploader.drawWithShader(b.buildOrThrow());
    }

    /** Default reticle texture: fine cross, heavy posts from 0.35 to the rim (lens coords over [-1, 1]). */
    private static ResourceLocation duplex() {
        if (!duplexReady) {
            com.mojang.blaze3d.platform.NativeImage img = new com.mojang.blaze3d.platform.NativeImage(DUPLEX_PX,
                    DUPLEX_PX, true);
            for (int y = 0; y < DUPLEX_PX; y++) {
                for (int x = 0; x < DUPLEX_PX; x++) {
                    float u = 2F * (x + 0.5F) / DUPLEX_PX - 1, v = 2F * (y + 0.5F) / DUPLEX_PX - 1;
                    float fine = 0.004F, post = 0.03F;
                    boolean line = Math.abs(v) < fine || Math.abs(u) < fine
                            || Math.abs(v) < post && Math.abs(u) > 0.35F || Math.abs(u) < post && Math.abs(v) > 0.35F;
                    img.setPixelRGBA(x, y, line ? 0xFF000000 : 0);
                }
            }
            Minecraft.getInstance().getTextureManager().register(DUPLEX, new DynamicTexture(img));
            duplexReady = true;
        }
        return DUPLEX;
    }

    /** {@code cpu ms/frame, gpu ms/frame, samples} since the last call; resets; first call starts GPU timing. */
    public String stats() {
        timing = true;
        String out = String.format(Locale.ROOT, "cpu %.3f ms (n=%d) gpu %.3f ms (n=%d)",
                cpuSamples == 0 ? 0.0 : cpuNanos / 1e6 / cpuSamples, cpuSamples,
                gpuSamples == 0 ? 0.0 : gpuNanos / 1e6 / gpuSamples, gpuSamples);
        cpuNanos = gpuNanos = 0;
        cpuSamples = gpuSamples = 0;
        return out;
    }

    public void close() {
        if (view != null) {
            view.close();
            view = null;
        }
        if (target != null) {
            target.destroyBuffers();
            target = null;
            Minecraft.getInstance().getMainRenderTarget().bindWrite(true);
        }
        if (queries != null) {
            GL15.glDeleteQueries(queries);
            queries = null;
            Arrays.fill(pending, false);
        }
        lensValid = false;
    }
}
