package com.wf.wflib.continuum;

import com.hbm.blocks.machine.Floodlight;
import com.hbm.tileentity.machine.TileEntityFloodlight;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.phys.Vec3;
import net.neoforged.fml.loading.FMLEnvironment;
import org.joml.Matrix4f;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Continuum's floodlight throws real light: each of its two lamps is a GemRender spot, a wide shadowed cone along the way the head points. Continuum's own
 * light blocks at the rays' ends are made dark ({@link #substitute}), so the GPU light is the only light. The lights are client only. The lamps and the beam come from the renderer's own pose ({@code FloodlightRenderer}), so the light leaves the lamp faces the model draws.
 */
final class FloodlightBridge {
    private FloodlightBridge() {
    }

    /** Blocks: Continuum's rays reach 64. */
    static final float RANGE = 64;
    /** Half angles, degrees: full brightness inside the inner, falling to nothing at the outer. */
    static final float INNER = 8, OUTER = 38;
    static final int RGB = 0xFFF2DE;
    /**
     * At night about 0.9 times a surface's own colour at 20 blocks and 6 at 5 ({@code I / (d^2 + softness^2)}): a lit pool, not a white-out. The softness
     * keeps the near field from clipping. In daylight the sun does the work: {@link #DAY} of it is left.
     */
    static final float INTENSITY = 400, SOFTNESS = 6, DAY = 0.12F;
    /** Glare size against a soldier's headlamp. */
    static final float FLARE = 2.5F;
    /** The glare's own cone, degrees: it shows looking into the beam, not from anywhere the light reaches (the flare adds half again past the outer). */
    static final float FLARE_INNER = 8, FLARE_OUTER = 25;
    /** The glare sits on the lamp's face, just off the glass: further out it drifts off the lamp seen at an angle. */
    static final float FLARE_OUT = 0.05F;
    /** Lit floodlights near the camera that get lights; GemRender carries 64 in all. */
    static final int MAX_FIXTURES = 12;
    /** The lamp faces in the head's own space (floodlight.obj, group Lamps): two plates side by side, facing +y. */
    static final float LAMP_Y = 0.625F, LAMP_Z = 0.688F;

    static void install() {
        if (!FMLEnvironment.dist.isClient()) return;
        Client.register();
        ContinuumBridge.LOG.info("Floodlights: each lit Continuum floodlight casts two GemRender spot cones");
    }

    /**
     * Continuum's beam blocks (the invisible full-light block it drops at each ray's end) give no light of their own: the GemRender spots are the light.
     * Same class, Continuum's own properties with the light level at 0, so its rays, its cleanup and old worlds keep working. A beam placed before this
     * keeps the light already stored with its chunk until something relights the spot.
     */
    static void substitute() {
        dev.golem.core.registry.Substitutions.block(ContinuumBridge.ID, "hbm:floodlight_beam",
                p -> new com.hbm.blocks.machine.FloodlightBeam(p.lightLevel(s -> 0)));
    }

    /** The head's pose in block space for the floodlight's old metadata (facing, +6 flipped) and its tilt, as {@code FloodlightRenderer} builds it. */
    static Matrix4f pose(int meta, float rotation) {
        Matrix4f m = new Matrix4f().translate(0.5F, 0.5F, 0.5F);
        switch (meta) {
            case 0, 6 -> m.rotateX((float) Math.PI);
            case 2 -> m.rotateX((float) Math.PI / 2).rotateZ((float) Math.PI);
            case 3 -> m.rotateX((float) Math.PI / 2);
            case 4 -> m.rotateX((float) Math.PI / 2).rotateZ((float) Math.PI / 2);
            case 5 -> m.rotateX((float) Math.PI / 2).rotateZ((float) Math.PI * 1.5F);
            default -> { }
        }
        m.translate(0F, -0.5F, 0F);
        if (meta != 0 && meta != 1) m.rotateY((float) Math.PI / 2);
        float tilt = rotation;
        if (meta == 0 || meta == 6) tilt -= 90;
        if (meta == 1 || meta == 7) tilt += 90;
        return m.translate(0F, 0.5F, 0F).rotateZ((float) Math.toRadians(tilt)).translate(0F, -0.5F, 0F);
    }

    /** Share of the night-time intensity at a sky brightness of {@code skyDarken} ({@code ClientLevel.getSkyDarken}: 0.2 at night, 1 at noon). */
    static float dayScale(float skyDarken) {
        float day = Math.max(0F, Math.min(1F, (skyDarken - 0.2F) / 0.8F));
        return 1F - (1F - DAY) * day;
    }

    /** The beam, a unit vector in the world: the lamp faces' normal. */
    static Vector3f beam(Matrix4f pose) {
        return pose.transformDirection(new Vector3f(0, 1, 0)).normalize();
    }

    /** Client side: kept apart so a server never loads GemRender's light classes. */
    static final class Client {
        private static final Set<TileEntityFloodlight> NEAR = new LinkedHashSet<>();
        private static long scanned = -1000;                                   // not Long.MIN_VALUE: now - scanned would overflow and never scan
        private static int lastNear = -1, lastLit = -1;
        /** Chunks round the camera searched for floodlights, once a second. */
        private static final int SCAN_CHUNKS = 6;

        static void register() {
            com.wf.gemrender.light.Lights.register(Client::collect);
        }

        private static void collect(com.wf.gemrender.light.LightSink sink, float partialTick) {
            Minecraft mc = Minecraft.getInstance();
            ClientLevel level = mc.level;
            if (level == null || mc.player == null || !com.wf.gemrender.light.Lights.shading()) {
                NEAR.clear();
                return;
            }
            long now = level.getGameTime();
            if (now - scanned >= 20 || now < scanned) {
                scanned = now;
                scan(level, new ChunkPos(mc.player.blockPosition()));
            }
            Vec3 cam = mc.gameRenderer.getMainCamera().getPosition();
            NEAR.removeIf(be -> be.isRemoved() || be.getLevel() != level);
            List<TileEntityFloodlight> lit = new ArrayList<>();
            for (TileEntityFloodlight be : NEAR) if (be.isOn) lit.add(be);
            lit.sort(Comparator.comparingDouble(be -> be.getBlockPos().distToCenterSqr(cam)));
            float intensity = INTENSITY * dayScale(level.getSkyDarken(partialTick));
            for (TileEntityFloodlight be : lit.subList(0, Math.min(MAX_FIXTURES, lit.size()))) {
                Matrix4f pose = pose(Floodlight.meta(be.getBlockState()), be.rotation);
                Vector3f dir = beam(pose), up = pose.transformDirection(new Vector3f(0, 0, 1)).normalize();
                for (int side = -1; side <= 1; side += 2) {
                    Vector3f face = pose.transformPosition(new Vector3f(0, LAMP_Y, side * LAMP_Z));
                    Vector3f at = new Vector3f(dir).mul(0.15F).add(face);                                            // the light: just off the glass
                    double x = be.getBlockPos().getX() + at.x, y = be.getBlockPos().getY() + at.y, z = be.getBlockPos().getZ() + at.z;
                    sink.spot(x, y, z, dir, up, RANGE, INNER, OUTER, RGB, intensity, null, true, false, SOFTNESS);
                    double fx = be.getBlockPos().getX() + face.x + dir.x * FLARE_OUT, fy = be.getBlockPos().getY() + face.y + dir.y * FLARE_OUT,
                            fz = be.getBlockPos().getZ() + face.z + dir.z * FLARE_OUT;
                    com.wf.wflib.soldier.client.LampFlare.add(fx, fy, fz, dir, RGB,
                            FLARE_INNER, FLARE_OUTER, RANGE, FLARE, false);   // the glare GemRender's spots do not draw: round core and halo only, no screen-turned star
                }
            }
        }

        private static void scan(ClientLevel level, ChunkPos centre) {
            NEAR.clear();
            for (int dx = -SCAN_CHUNKS; dx <= SCAN_CHUNKS; dx++) {
                for (int dz = -SCAN_CHUNKS; dz <= SCAN_CHUNKS; dz++) {
                    LevelChunk chunk = level.getChunkSource().getChunk(centre.x + dx, centre.z + dz, false);
                    if (chunk == null) continue;
                    for (var be : chunk.getBlockEntities().values()) if (be instanceof TileEntityFloodlight f) NEAR.add(f);
                }
            }
            int lit = (int) NEAR.stream().filter(f -> f.isOn).count();
            if (NEAR.size() != lastNear || lit != lastLit) ContinuumBridge.LOG.debug("Floodlights: {} near, {} lit", NEAR.size(), lit);
            lastNear = NEAR.size();
            lastLit = lit;
        }
    }
}
