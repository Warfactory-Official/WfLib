package com.wf.wflib.soldier.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.network.chat.Component;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;

/**
 * What an order looks like in the world: a pulsing ring and a post at the point it names, a line to it from each soldier
 * that took it, and the order's name floating over each of them. Fades over the last quarter of its time.
 */
final class OrderMarkers {
    private static final int SEGMENTS = 40;

    private OrderMarkers() {}

    static void onRenderLevel(RenderLevelStageEvent e) {
        if (e.getStage() != RenderLevelStageEvent.Stage.AFTER_PARTICLES || SquadClient.markers().isEmpty()) return;
        var mc = Minecraft.getInstance();
        if (mc.level == null) return;
        Vec3 cam = e.getCamera().getPosition();
        PoseStack ps = e.getPoseStack();
        var buffers = mc.renderBuffers().bufferSource();
        VertexConsumer lines = buffers.getBuffer(RenderType.lines());
        long now = mc.level.getGameTime();
        float partial = e.getPartialTick().getGameTimeDeltaPartialTick(false);
        for (var marker : SquadClient.markers()) {
            float age = now + partial - marker.born();
            float fade = age > SquadClient.SHOWN * 0.75f ? Math.max(0, (SquadClient.SHOWN - age) / (SquadClient.SHOWN * 0.25f)) : 1f;
            var echo = marker.echo();
            int[] rgb = colour(echo.kind());
            Vec3 point = echo.hasPos() ? new Vec3(echo.x(), echo.y(), echo.z()) : null;
            if (point != null) {
                float pulse = 1f + 0.12f * (float) Math.sin(age * 0.35f);
                ring(ps, lines, point.subtract(cam).add(0, 0.05, 0), 1.3f * pulse, rgb, fade);
                ring(ps, lines, point.subtract(cam).add(0, 0.05, 0), 0.7f * pulse, rgb, fade);
                line(ps, lines, point.subtract(cam), point.subtract(cam).add(0, 3, 0), rgb, fade);
            }
            for (int id : echo.entities()) {
                var entity = mc.level.getEntity(id);
                if (entity == null) continue;
                Vec3 at = entity.getPosition(e.getPartialTick().getGameTimeDeltaPartialTick(false)).subtract(cam);
                if (point != null) line(ps, lines, at.add(0, 0.1, 0), point.subtract(cam).add(0, 0.1, 0), rgb, fade * 0.6f);
                ring(ps, lines, at.add(0, 0.05, 0), 0.5f, rgb, fade);
            }
        }
        buffers.endBatch(RenderType.lines());
        Font font = mc.font;
        for (var marker : SquadClient.markers()) {
            float age = now + partial - marker.born();
            if (age > 60) continue;                                           // the name over each head is brief
            int alpha = (int) (255 * Math.min(1f, (60 - age) / 20f));
            if (alpha < 8) continue;
            for (int id : marker.echo().entities()) {
                var entity = mc.level.getEntity(id);
                if (entity == null) continue;
                Vec3 at = entity.getPosition(e.getPartialTick().getGameTimeDeltaPartialTick(false)).subtract(cam).add(0, entity.getBbHeight() + 0.9, 0);
                ps.pushPose();
                ps.translate(at.x, at.y, at.z);
                ps.mulPose(e.getCamera().rotation());
                ps.scale(-0.03f, -0.03f, 0.03f);
                Component text = Component.literal(marker.echo().label().toUpperCase(java.util.Locale.ROOT));
                font.drawInBatch(text, -font.width(text) / 2f, 0, (alpha << 24) | 0xFFFFFF, false, ps.last().pose(), buffers,
                        Font.DisplayMode.SEE_THROUGH, (alpha / 3) << 24, 0xF000F0);
                ps.popPose();
            }
        }
        buffers.endBatch();
    }

    private static int[] colour(String kind) {
        return switch (kind) {
            case "attack", "suppress" -> new int[]{255, 70, 60};
            case "move", "regroup" -> new int[]{90, 200, 255};
            case "hold", "guard" -> new int[]{255, 210, 80};
            case "patrol" -> new int[]{120, 230, 120};
            case "resupply" -> new int[]{80, 140, 255};
            default -> new int[]{255, 255, 255};
        };
    }

    private static void ring(PoseStack ps, VertexConsumer c, Vec3 centre, float radius, int[] rgb, float fade) {
        for (int i = 0; i < SEGMENTS; i++) {
            double a0 = Math.PI * 2 * i / SEGMENTS, a1 = Math.PI * 2 * (i + 1) / SEGMENTS;
            line(ps, c, centre.add(Math.cos(a0) * radius, 0, Math.sin(a0) * radius), centre.add(Math.cos(a1) * radius, 0, Math.sin(a1) * radius), rgb, fade);
        }
    }

    private static void line(PoseStack ps, VertexConsumer c, Vec3 a, Vec3 b, int[] rgb, float fade) {
        var pose = ps.last();
        Vec3 d = b.subtract(a);
        float len = (float) d.length();
        float nx = len < 1e-6 ? 0 : (float) (d.x / len), ny = len < 1e-6 ? 1 : (float) (d.y / len), nz = len < 1e-6 ? 0 : (float) (d.z / len);
        int alpha = (int) (255 * fade);
        c.addVertex(pose.pose(), (float) a.x, (float) a.y, (float) a.z).setColor(rgb[0], rgb[1], rgb[2], alpha).setNormal(pose, nx, ny, nz);
        c.addVertex(pose.pose(), (float) b.x, (float) b.y, (float) b.z).setColor(rgb[0], rgb[1], rgb[2], alpha).setNormal(pose, nx, ny, nz);
    }
}
