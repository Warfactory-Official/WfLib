package com.wf.wflib.fire.client;

import com.wf.wflib.fire.api.FireLingeringEntity;
import com.wf.wflib.fire.api.FireType;
import com.wf.wflib.particle.api.XrGroups;
import com.wf.wflib.particle.api.client.XrParticles;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import net.neoforged.neoforge.event.level.LevelEvent;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import com.wf.wflib.core.api.WFLib;

/**
 * X-Ray flames for lingering fire (one group per {@link #CELL} m cell and type, over the union of the boxes in it) and
 * for burning living entities. Groups are capped and the nearest win.
 */
@EventBusSubscriber(modid = WFLib.MODID, value = Dist.CLIENT)
public final class FireXrEffects {
    private static final int INTERVAL = 5;
    private static final double CELL = 6.0D;
    private static final double CELL_Y = 4.0D;
    private static final int MAX_PATCHES = 16;
    private static final int MAX_BURNERS = 16;
    private static final double PATCH_RANGE = 64.0D;
    private static final double BURNER_RANGE = 32.0D;
    /** Range multiplier once started: no flicker at the edge. */
    private static final double HYSTERESIS = 1.25D;
    /** {@code static/zharka_static} is authored for a 3 m zone. */
    private static final double PATCH_ZONE = 3.0D;

    private record CellKey(int x, int y, int z, FireType type) {
    }

    private static final class Cell {
        double minX = Double.MAX_VALUE;
        double maxX = -Double.MAX_VALUE;
        double minY = Double.MAX_VALUE;
        double maxY = -Double.MAX_VALUE;
        double minZ = Double.MAX_VALUE;
        double maxZ = -Double.MAX_VALUE;

        void add(AABB b) {
            minX = Math.min(minX, b.minX);
            maxX = Math.max(maxX, b.maxX);
            minY = Math.min(minY, b.minY);
            maxY = Math.max(maxY, b.maxY);
            minZ = Math.min(minZ, b.minZ);
            maxZ = Math.max(maxZ, b.maxZ);
        }

        Vec3 origin() {
            return new Vec3((minX + maxX) * 0.5D, minY, (minZ + maxZ) * 0.5D);
        }

        float scale() {
            return (float) Mth.clamp(Math.max(maxX - minX, maxZ - minZ) / PATCH_ZONE, 0.5D, 2.2D);
        }

        float height() {
            return (float) Mth.clamp((maxY - minY) * 0.5D, 0.6D, 2.0D);
        }
    }

    private record Patch(XrParticles.Playing playing, Vec3 origin, float scale) {
    }

    private static final Map<CellKey, Patch> PATCHES = new HashMap<>();
    private static final Map<Integer, XrParticles.Playing> BURNERS = new HashMap<>();
    private static final Map<Integer, LivingEntity> BURNING = new HashMap<>();

    private FireXrEffects() {
    }

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        ClientLevel level = mc.level;
        if (level == null || mc.player == null || mc.player.tickCount % INTERVAL != 0) {
            return;
        }
        Vec3 eye = mc.gameRenderer.getMainCamera().getPosition();
        patches(level, eye);
        burners(level, mc, eye);
    }

    private static void patches(ClientLevel level, Vec3 eye) {
        Map<CellKey, Cell> cells = new HashMap<>();
        for (Entity e : level.entitiesForRendering()) {
            if (!(e instanceof FireLingeringEntity fire) || fire.isRemoved()) {
                continue;
            }
            AABB box = fire.getBoundingBox();
            for (int cx = Mth.floor(box.minX / CELL); cx <= Mth.floor(box.maxX / CELL); cx++) {
                for (int cz = Mth.floor(box.minZ / CELL); cz <= Mth.floor(box.maxZ / CELL); cz++) {
                    AABB part = box.intersect(new AABB(cx * CELL, box.minY, cz * CELL, (cx + 1) * CELL, box.maxY,
                            (cz + 1) * CELL));
                    if (part.maxX > part.minX && part.maxZ > part.minZ) {
                        cells.computeIfAbsent(new CellKey(cx, Mth.floor(box.minY / CELL_Y), cz, fire.getFireType()),
                                k -> new Cell()).add(part);
                    }
                }
            }
        }
        List<Map.Entry<CellKey, Cell>> wanted = new ArrayList<>();
        for (Map.Entry<CellKey, Cell> c : cells.entrySet()) {
            double range = PATCH_RANGE * (PATCHES.containsKey(c.getKey()) ? HYSTERESIS : 1.0D);
            if (c.getValue().origin().distanceToSqr(eye) < range * range) {
                wanted.add(c);
            }
        }
        wanted.sort(Comparator.comparingDouble(c -> c.getValue().origin().distanceToSqr(eye)));
        if (wanted.size() > MAX_PATCHES) {
            wanted.subList(MAX_PATCHES, wanted.size()).clear();
        }
        Map<CellKey, Patch> next = new HashMap<>();
        for (Map.Entry<CellKey, Cell> c : wanted) {
            Cell cell = c.getValue();
            Vec3 origin = cell.origin();
            float scale = cell.scale();
            Patch old = PATCHES.remove(c.getKey());
            if (old != null && Math.abs(old.scale - scale) < 0.3F * scale && old.origin.distanceToSqr(origin) < 2.25D) {
                next.put(c.getKey(), old);
                continue;
            }
            if (old != null) {
                old.playing.stop();
            }
            XrParticles.play(XrGroups.ZHARKA_STATIC, origin, scale, cell.height(), true).ifPresent(p -> {
                tint(p, c.getKey().type());
                next.put(c.getKey(), new Patch(p, origin, scale));
            });
        }
        PATCHES.values().forEach(p -> p.playing.stop());
        PATCHES.clear();
        PATCHES.putAll(next);
    }

    private static void burners(ClientLevel level, Minecraft mc, Vec3 eye) {
        List<LivingEntity> burning = new ArrayList<>();
        for (Entity e : level.entitiesForRendering()) {
            if (e instanceof LivingEntity living && living.isOnFire() && !living.isSpectator() && !living.isInvisible()
                    && !(living == mc.player && !mc.gameRenderer.getMainCamera().isDetached())) {
                double range = BURNER_RANGE * (BURNERS.containsKey(living.getId()) ? HYSTERESIS : 1.0D);
                if (living.position().distanceToSqr(eye) < range * range) {
                    burning.add(living);
                }
            }
        }
        burning.sort(Comparator.comparingDouble(e -> e.position().distanceToSqr(eye)));
        if (burning.size() > MAX_BURNERS) {
            burning.subList(MAX_BURNERS, burning.size()).clear();
        }
        BURNING.clear();
        for (LivingEntity e : burning) {
            BURNING.put(e.getId(), e);
        }
        BURNERS.entrySet().removeIf(b -> {
            if (BURNING.containsKey(b.getKey())) {
                return false;
            }
            b.getValue().stop();
            return true;
        });
        for (LivingEntity e : burning) {
            if (!BURNERS.containsKey(e.getId())) {
                XrParticles.play(XrGroups.ARTEFACT_ZHARKA, e.position(), e.getBbWidth(), e.getBbHeight(), true)
                        .ifPresent(p -> BURNERS.put(e.getId(), p));
            }
        }
    }

    @SubscribeEvent
    public static void onRenderStage(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_ENTITIES || BURNERS.isEmpty()) {
            return;
        }
        float partial = event.getPartialTick().getGameTimeDeltaPartialTick(false);
        for (Map.Entry<Integer, XrParticles.Playing> b : BURNERS.entrySet()) {
            LivingEntity e = BURNING.get(b.getKey());
            if (e != null && !e.isRemoved()) {
                b.getValue().trailTo(e.getPosition(partial));
            }
        }
    }

    @SubscribeEvent
    public static void onLevelUnload(LevelEvent.Unload event) {
        if (event.getLevel().isClientSide()) {
            PATCHES.clear();
            BURNERS.clear();
            BURNING.clear();
        }
    }

    private static void tint(XrParticles.Playing p, FireType type) {
        switch (type) {
            case NORMAL -> {
            }
            case PHOSPHORUS -> p.tint(0.85F, 1.4F, 1.25F).glow(1.3F);
            case THERMITE -> p.tint(0.9F, 1.5F, 2.2F).glow(1.3F);
        }
    }

    /** Live patch + burner groups, for debug output. */
    public static String report() {
        return "fire xray: " + PATCHES.size() + " patch(es), " + BURNERS.size() + " burner(s)";
    }
}
