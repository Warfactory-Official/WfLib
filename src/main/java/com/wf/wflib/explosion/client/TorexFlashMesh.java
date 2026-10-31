package com.wf.wflib.explosion.client;

import com.mojang.math.Axis;
import dev.engine_room.flywheel.api.material.CardinalLightingMode;
import dev.engine_room.flywheel.api.material.Transparency;
import dev.engine_room.flywheel.api.material.WriteMask;
import dev.engine_room.flywheel.api.model.Model;
import dev.engine_room.flywheel.api.vertex.MutableVertexList;
import dev.engine_room.flywheel.lib.material.FogShaders;
import dev.engine_room.flywheel.lib.material.SimpleMaterial;
import dev.engine_room.flywheel.lib.model.QuadMesh;
import dev.engine_room.flywheel.lib.model.SingleMeshModel;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.joml.Vector4f;
import org.joml.Vector4fc;

import java.util.Random;
import com.wf.wflib.core.api.WFLib;

/** Flash starburst at unit intensity x scale: 300 seeded cones, each fan = 3 degenerate quads. */
final class TorexFlashMesh implements QuadMesh {

    static final ResourceLocation WHITE = ResourceLocation.fromNamespaceAndPath(WFLib.MODID, "textures/effect/white.png");
    static final Model MODEL = new SingleMeshModel(new TorexFlashMesh(), SimpleMaterial.builder()
            .texture(WHITE)
            .transparency(Transparency.LIGHTNING)
            .writeMask(WriteMask.COLOR)
            .fog(FogShaders.NONE)
            .useLight(false)
            .useOverlay(false)
            .backfaceCulling(true)
            .cardinalLightingMode(CardinalLightingMode.OFF)
            .mipmap(false)
            .build());

    private static final int CONES = 300;
    private static final int VERTICES = CONES * 3 * 4;

    private final float[] xyz = new float[VERTICES * 3];
    private final float[] alpha = new float[VERTICES];
    private final Vector4f sphere;

    private TorexFlashMesh() {
        Random random = new Random(432L);
        Matrix4f m = new Matrix4f();
        Vector3f[] fan = new Vector3f[4];
        float reach = 0F;
        int v = 0;
        for (int i = 0; i < CONES; i++) {
            m.identity()
                    .rotate(Axis.XP.rotationDegrees(random.nextFloat() * 360.0F))
                    .rotate(Axis.YP.rotationDegrees(random.nextFloat() * 360.0F))
                    .rotate(Axis.ZP.rotationDegrees(random.nextFloat() * 360.0F))
                    .rotate(Axis.XP.rotationDegrees(random.nextFloat() * 360.0F))
                    .rotate(Axis.YP.rotationDegrees(random.nextFloat() * 360.0F));
            float vert1 = random.nextFloat() * 20.0F + 15.0F;
            float vert2 = random.nextFloat() * 2.0F + 3.0F;
            fan[0] = m.transformPosition(new Vector3f((float) (-0.866D * vert2), vert1, (float) (-0.5D * vert2)));
            fan[1] = m.transformPosition(new Vector3f((float) (0.866D * vert2), vert1, (float) (-0.5D * vert2)));
            fan[2] = m.transformPosition(new Vector3f(0.0F, vert1, vert2));
            fan[3] = fan[0];
            reach = Math.max(reach, (float) Math.hypot(vert1, vert2));
            for (int t = 0; t < 3; t++) {
                v = put(v, new Vector3f(), 1F);
                v = put(v, fan[t], 0F);
                v = put(v, fan[t + 1], 0F);
                v = put(v, fan[t + 1], 0F);
            }
        }
        sphere = new Vector4f(0F, 0F, 0F, reach);
    }

    private int put(int v, Vector3f p, float a) {
        xyz[3 * v] = p.x;
        xyz[3 * v + 1] = p.y;
        xyz[3 * v + 2] = p.z;
        alpha[v] = a;
        return v + 1;
    }

    @Override
    public int vertexCount() {
        return VERTICES;
    }

    @Override
    public void write(MutableVertexList list) {
        for (int v = 0; v < VERTICES; v++) {
            list.x(v, xyz[3 * v]);
            list.y(v, xyz[3 * v + 1]);
            list.z(v, xyz[3 * v + 2]);
            list.r(v, 1F);
            list.g(v, 1F);
            list.b(v, 1F);
            list.a(v, alpha[v]);
            list.u(v, 0.5F);
            list.v(v, 0.5F);
            list.overlay(v, OverlayTexture.NO_OVERLAY);
            list.light(v, 0xF000F0);
            list.normalX(v, 0F);
            list.normalY(v, 1F);
            list.normalZ(v, 0F);
        }
    }

    @Override
    public Vector4fc boundingSphere() {
        return sphere;
    }
}
