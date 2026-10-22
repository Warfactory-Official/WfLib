package com.wf.wflib.client.render;

import com.wf.wflib.MissileEntity;
import com.wf.wflib.ModEntities;
import com.wf.wflib.WFLib;
import com.wf.wflib.drone.DroneEntity;
import com.wf.wflib.network.SimGhostSyncPacket;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;

import java.util.UUID;

/**
 * {@link SimGhostSyncPacket} -> render-only entities in the client level (fresh UUID, ghost id; Boxy draws and
 * ticks them past render distance like any {@code boxy:distant} type). Position = last sample + velocity x ticks
 * since; real entity with the sim's UUID joins => ghost removed.
 */
@EventBusSubscriber(modid = WFLib.MODID, value = Dist.CLIENT)
public final class SimGhostEntities {

    private static final class Ghost {
        final UUID sim;
        final Entity entity;
        Vec3 at = Vec3.ZERO;
        Vec3 velocity = Vec3.ZERO;
        int age;

        Ghost(UUID sim, Entity entity) {
            this.sim = sim;
            this.entity = entity;
        }
    }

    private static final Int2ObjectOpenHashMap<Ghost> GHOSTS = new Int2ObjectOpenHashMap<>();
    /** Ghost entity id -> real entity that replaced it; while the real one lives. */
    private static final Int2ObjectOpenHashMap<Entity> REPLACED = new Int2ObjectOpenHashMap<>();
    private static ClientLevel level;

    private SimGhostEntities() {
    }

    public static int count() {
        return GHOSTS.size();
    }

    /** Lock holders: replaced ghost -> its real entity; anything else unchanged. */
    public static Entity current(Entity e) {
        return e.isRemoved() ? REPLACED.getOrDefault(e.getId(), e) : e;
    }

    public static void accept(SimGhostSyncPacket packet) {
        ClientLevel current = Minecraft.getInstance().level;
        if (current != level) {
            clear();
            level = current;
        }
        if (level == null) {
            return;
        }
        for (int handle : packet.removed()) {
            remove(handle);
        }
        for (SimGhostSyncPacket.Added a : packet.added()) {
            Entity e = switch (a.kind()) {
                case MISSILE -> {
                    MissileEntity m = new MissileEntity(ModEntities.STEALTH_MISSILE.get(), level);
                    m.setModelId(a.model());
                    m.setExhaustColor(a.exhaust());
                    yield m;
                }
                case DRONE -> {
                    DroneEntity d = new DroneEntity(ModEntities.DRONE.get(), level);
                    d.setModelId(a.model());
                    yield d;
                }
            };
            e.setUUID(UUID.randomUUID());
            e.setId(SimGhostSyncPacket.GHOST_ID_BASE + a.handle());
            remove(a.handle());
            GHOSTS.put(a.handle(), new Ghost(a.sim(), e));
        }
        for (SimGhostSyncPacket.Moved m : packet.moved()) {
            Ghost g = GHOSTS.get(m.handle());
            if (g == null) {
                continue;
            }
            g.at = new Vec3(m.x(), m.y(), m.z());
            g.velocity = new Vec3(m.vx(), m.vy(), m.vz());
            g.age = 0;
            if (!g.entity.isAddedToLevel()) {
                place(g.entity, g.at, g.velocity);
                g.entity.setOldPosAndRot();
                level.addEntity(g.entity);
            }
        }
    }

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        REPLACED.values().removeIf(Entity::isRemoved);
        if (GHOSTS.isEmpty()) {
            return;
        }
        if (Minecraft.getInstance().level != level) {
            clear();
            return;
        }
        for (Ghost g : GHOSTS.values()) {
            if (!g.entity.isAddedToLevel()) {
                continue;
            }
            g.age++;
            g.entity.setOldPosAndRot();
            place(g.entity, g.at.add(g.velocity.scale(g.age)), g.velocity);
        }
    }

    @SubscribeEvent
    public static void onJoin(EntityJoinLevelEvent event) {
        if (!event.getLevel().isClientSide() || GHOSTS.isEmpty() || event.getEntity().getId() < 0) {
            return;
        }
        Entity real = event.getEntity();
        UUID id = real.getUUID();
        GHOSTS.int2ObjectEntrySet().removeIf(e -> {
            if (e.getValue().sim.equals(id)) {
                discard(e.getValue());
                REPLACED.put(e.getValue().entity.getId(), real);
                return true;
            }
            return false;
        });
    }

    private static void place(Entity e, Vec3 at, Vec3 velocity) {
        e.setPos(at);
        e.setDeltaMovement(velocity);
        double horizontal = velocity.horizontalDistance();
        if (horizontal > 1.0e-4 || Math.abs(velocity.y) > 1.0e-4) {
            e.setYRot((float) (Mth.atan2(-velocity.x, velocity.z) * Mth.RAD_TO_DEG));
            e.setXRot((float) (-Mth.atan2(velocity.y, horizontal) * Mth.RAD_TO_DEG));
        }
    }

    private static void remove(int handle) {
        Ghost g = GHOSTS.remove(handle);
        if (g != null) {
            discard(g);
        }
    }

    private static void discard(Ghost g) {
        if (g.entity.isAddedToLevel() && level != null) {
            level.removeEntity(g.entity.getId(), Entity.RemovalReason.DISCARDED);
        }
    }

    private static void clear() {
        for (Ghost g : GHOSTS.values()) {
            discard(g);
        }
        GHOSTS.clear();
        REPLACED.clear();
    }
}
