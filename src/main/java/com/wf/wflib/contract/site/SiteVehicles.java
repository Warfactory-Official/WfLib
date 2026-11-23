package com.wf.wflib.contract.site;

import com.wf.wflib.contract.ContractSystem;
import com.wf.wflib.contract.api.VehicleProvider;
import com.wf.wflib.soldier.api.Site;
import com.wf.wflib.soldier.api.SoldierControl;
import com.wf.wflib.soldier.api.Sites;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;

/**
 * The vehicles of the bases. Slots are recorded when a base is built; once a {@link VehicleProvider} is registered each slot gets
 * a parked vehicle (when its chunk is loaded). When any soldier of the base has an enemy in sight the base is alerted: once per
 * alert each parked vehicle rolls {@link #MOUNT_CHANCE}, and on a hit the nearest free soldier runs to it and the provider seats
 * them. Server thread.
 */
public final class SiteVehicles {
    /** Chance, per vehicle per alert, that a soldier takes it. */
    public static final double MOUNT_CHANCE = 0.35;
    private static final int GIVE_UP = 600, CLOSE = 5;
    private static final Random RNG = new Random();
    private static volatile VehicleProvider provider;

    private SiteVehicles() {
    }

    public static void provider(VehicleProvider p) {
        provider = p;
    }

    private static final class Slot {
        String site, dimension, kind;
        double x, y, z;
        float yaw;
        UUID vehicle;
        // alert bookkeeping, not saved
        boolean rolled;
        UUID driver;
        long since;
    }

    static final class Book extends SavedData {
        final List<Slot> slots = new ArrayList<>();

        static Book get(MinecraftServer server) {
            return server.overworld().getDataStorage().computeIfAbsent(new SavedData.Factory<>(Book::new, (tag, reg) -> load(tag)), "wflib_site_vehicles");
        }

        static Book load(CompoundTag tag) {
            Book b = new Book();
            for (Tag t : tag.getList("slots", Tag.TAG_COMPOUND)) {
                CompoundTag c = (CompoundTag) t;
                Slot s = new Slot();
                s.site = c.getString("site");
                s.dimension = c.getString("dim");
                s.kind = c.getString("kind");
                s.x = c.getDouble("x");
                s.y = c.getDouble("y");
                s.z = c.getDouble("z");
                s.yaw = c.getFloat("yaw");
                if (c.hasUUID("vehicle")) s.vehicle = c.getUUID("vehicle");
                b.slots.add(s);
            }
            return b;
        }

        @Override
        public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
            ListTag list = new ListTag();
            for (Slot s : slots) {
                CompoundTag c = new CompoundTag();
                c.putString("site", s.site);
                c.putString("dim", s.dimension);
                c.putString("kind", s.kind);
                c.putDouble("x", s.x);
                c.putDouble("y", s.y);
                c.putDouble("z", s.z);
                c.putFloat("yaw", s.yaw);
                if (s.vehicle != null) c.putUUID("vehicle", s.vehicle);
                list.add(c);
            }
            tag.put("slots", list);
            return tag;
        }
    }

    /** Records the bays of a new base's site. */
    public static void register(MinecraftServer server, String siteId, ServerLevel level, List<BasePlan.Slot> slots) {
        if (slots.isEmpty()) return;
        Book book = Book.get(server);
        for (BasePlan.Slot p : slots) {
            Slot s = new Slot();
            s.site = siteId;
            s.dimension = level.dimension().location().toString();
            s.kind = p.kind();
            s.x = p.x() + 0.5;
            s.y = p.y();
            s.z = p.z() + 0.5;
            s.yaw = p.yaw();
            book.slots.add(s);
        }
        book.setDirty();
    }

    /** About once a second. */
    public static void tick(MinecraftServer server) {
        Book book = Book.get(server);
        if (book.slots.isEmpty()) return;
        var out = new ArrayList<Slot>();
        Map<String, Boolean> alerted = new HashMap<>();
        for (Slot s : book.slots) {
            var site = Sites.get(server, s.site);
            if (site.isEmpty()) {                                    // the job is over: the vehicle stays where it is, for whoever wants it
                book.setDirty();
                continue;
            }
            out.add(s);
            ServerLevel level = server.getLevel(ResourceKey.create(Registries.DIMENSION, ResourceLocation.parse(s.dimension)));
            if (level == null || !level.hasChunkAt(BlockPos.containing(s.x, s.y, s.z))) continue;
            VehicleProvider p = provider;
            if (p == null) continue;
            if (s.vehicle == null) {
                Entity v = p.park(level, new Vec3(s.x, s.y, s.z), s.yaw, s.kind);
                if (v != null) {
                    s.vehicle = v.getUUID();
                    book.setDirty();
                }
                continue;
            }
            Entity vehicle = level.getEntity(s.vehicle);
            if (vehicle == null || !vehicle.isAlive()) continue;
            boolean alert = alerted.computeIfAbsent(s.site, id -> isAlerted(site.get()));
            if (!alert) {
                s.rolled = false;
                continue;
            }
            if (s.driver == null && !s.rolled) {
                s.rolled = true;
                if (RNG.nextDouble() < MOUNT_CHANCE) claim(site.get(), s, vehicle, level.getGameTime());
            } else if (s.driver != null) {
                follow(server, s, vehicle, level.getGameTime(), p);
            }
        }
        if (out.size() != book.slots.size()) {
            book.slots.clear();
            book.slots.addAll(out);
        }
    }

    private static boolean isAlerted(Site site) {
        for (ServerPlayer m : site.members()) if (SoldierControl.target(m) != null) return true;
        return false;
    }

    /** The nearest soldier of the site that is not already doing something with a vehicle runs for it. */
    private static void claim(Site site, Slot s, Entity vehicle, long now) {
        ServerPlayer best = null;
        double bestD = 60 * 60;
        for (ServerPlayer m : site.members()) {
            if (!m.isAlive() || m.isPassenger()) continue;
            double d = m.distanceToSqr(vehicle);
            if (d < bestD) {
                best = m;
                bestD = d;
            }
        }
        if (best == null) return;
        s.driver = best.getUUID();
        s.since = now;
        SoldierControl.moveTo(best, vehicle.position(), CLOSE - 1);
    }

    private static void follow(MinecraftServer server, Slot s, Entity vehicle, long now, VehicleProvider p) {
        ServerPlayer driver = server.getPlayerList().getPlayer(s.driver);
        if (driver == null || !driver.isAlive() || now - s.since > GIVE_UP) {
            s.driver = null;
            return;
        }
        if (driver.distanceToSqr(vehicle) > CLOSE * CLOSE) return;
        var enemy = SoldierControl.target(driver);
        boolean seated;
        try {
            seated = p.mount(driver, vehicle, enemy == null ? null : enemy.position());
        } catch (RuntimeException e) {
            ContractSystem.LOGGER.warn("A vehicle provider failed to seat a soldier: {}", e.toString());
            seated = false;
        }
        s.driver = null;                                          // seated or not, this vehicle has had its chance this alert
    }
}
