package com.wf.wflib.armor.api;

import com.wf.wflib.armor.ArmorConfig;
import com.wf.wflib.armor.ArmorResolver;
import com.wf.wflib.core.api.OrderedRegistry;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Fire/gas/field contact tracking. Reference: STALKER (no lingering drain; acute splash on contact +
 * sustained cost while contact holds, see {@link ArmorResolver#acuteWear} / {@link ArmorResolver#sustainedWear}).
 */
public final class ArmorExposure {

    /** A hazard, queried per entity per interval. */
    public interface Source {
        /**
         * @return cost of {@code type} to this entity, condition points per exposure interval, before the
         *         suit's own tier is subtracted; 0 = none
         */
        double tier(LivingEntity entity, ProtectionType type);
    }

    /** Polled hazards, priority descending; {@code ArmorSystem} registers fire and lava. */
    public static final OrderedRegistry<Source> SOURCES = OrderedRegistry.create("armor", "armor exposure sources");
    private static final Map<LivingEntity, Contact> CONTACTS = new ConcurrentHashMap<>();

    /** Intervals with no contact and nothing owed before an entry is dropped. */
    private static final int IDLE_INTERVALS_TO_FORGET = 4;

    private ArmorExposure() {
    }

    /**
     * Push model, not poll: the hazard (e.g. a cloud) knows who's in it; polling every hazard against
     * every entity every interval would invert that.
     *
     * @param tier hazard's rate, condition points per exposure interval (not per tick or per second)
     */
    public static void contact(LivingEntity entity, ProtectionType type, double tier) {
        if (entity == null || type == null || tier <= 0.0D || !type.takesExposure()) {
            return;
        }
        if (entity.level().isClientSide) {
            return;
        }
        Contact contact = CONTACTS.computeIfAbsent(entity, e -> new Contact());
        contact.pushed.merge(type, tier, Math::max);
    }

    public static void tick(MinecraftServer server) {
        int now = server.getTickCount();
        boolean accrue = now % Math.max(1, ArmorConfig.get().exposureIntervalTicks()) == 0;
        boolean persist = now % Math.max(1, ArmorConfig.get().persistIntervalTicks()) == 0;
        if (!accrue && !persist) {
            return;
        }

        if (accrue) {
            // Players always tracked even with nothing pushed yet; others only enter via push/track.
            for (ServerPlayer player : server.getPlayerList().getPlayers()) {
                CONTACTS.computeIfAbsent(player, e -> new Contact());
            }
        }

        Iterator<Map.Entry<LivingEntity, Contact>> it = CONTACTS.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<LivingEntity, Contact> entry = it.next();
            LivingEntity entity = entry.getKey();
            Contact contact = entry.getValue();
            if (entity.isRemoved() || !entity.isAlive()) {
                it.remove();
                continue;
            }
            if (accrue) {
                accrue(entity, contact);
            }
            if (persist) {
                contact.flush(entity);
            }
            if (contact.idle >= IDLE_INTERVALS_TO_FORGET && contact.pending.isEmpty()) {
                it.remove();
            }
        }
    }

    /** Watches {@code entity} with no hazard of its own yet, so polled sources (e.g. fire) can find it. Dropped after {@link #IDLE_INTERVALS_TO_FORGET} idle intervals if nothing matches. */
    public static void track(LivingEntity entity) {
        if (entity == null || entity.level().isClientSide) {
            return;
        }
        CONTACTS.computeIfAbsent(entity, e -> new Contact());
    }

    /**
     * Writes owed wear and keeps watching. Must not forget: writing wear changes the stack, which fires
     * {@code LivingEquipmentChangeEvent}, which re-enters here; forgetting contact would re-charge the
     * acute splash every interval forever.
     */
    public static void flush(LivingEntity entity) {
        Contact contact = CONTACTS.get(entity);
        if (contact != null) {
            contact.flush(entity);
        }
    }

    /** Writes owed wear and stops watching; death/logout have no next interval. */
    public static void forget(LivingEntity entity) {
        Contact contact = CONTACTS.remove(entity);
        if (contact != null) {
            contact.flush(entity);
        }
    }

    /**
     * Drops owed wear for a slot without writing it: {@code LivingEquipmentChangeEvent} fires after the
     * swap, so the old stack is already gone and the event hands back only a copy. Loses at most one
     * persist interval.
     */
    public static void discard(LivingEntity entity, EquipmentSlot slot) {
        Contact contact = CONTACTS.get(entity);
        if (contact != null) {
            contact.pending.remove(slot);
        }
    }

    /** Test seam. */
    public static void clear() {
        CONTACTS.clear();
    }

    private static void accrue(LivingEntity entity, Contact contact) {
        EnumSet<ProtectionType> active = EnumSet.noneOf(ProtectionType.class);
        for (ProtectionType type : ProtectionType.VALUES) {
            if (!type.takesExposure()) {
                continue;
            }
            double tier = contact.pushed.getOrDefault(type, 0.0D);
            for (Source source : SOURCES.values()) {
                tier = Math.max(tier, source.tier(entity, type));
            }
            if (tier <= 0.0D) {
                continue;
            }
            active.add(type);
            if (!contact.lastActive.contains(type)) {
                // First contact = acute splash, charged immediately, not accrued.
                ArmorResolution.acute(entity, type, ArmorConfig.get().acuteFraction());
                continue;
            }
            sustain(entity, contact, type, tier);
        }
        contact.pushed.clear();
        contact.lastActive = active;
        contact.idle = active.isEmpty() ? contact.idle + 1 : 0;
    }

    private static void sustain(LivingEntity entity, Contact contact, ProtectionType type, double tier) {
        for (EquipmentSlot slot : ArmorResolution.SLOTS) {
            WornArmor piece = ArmorStacks.worn(entity, slot, 1.0D);
            if (piece == null) {
                continue;
            }
            int[] owed = null;
            for (int i = 0; i < piece.layers().size(); i++) {
                int points = ArmorResolver.sustainedWear(piece.layers().get(i), type, tier);
                if (points <= 0) {
                    continue;
                }
                if (owed == null) {
                    owed = contact.pending.computeIfAbsent(slot, s -> new int[piece.layers().size()]);
                    if (owed.length < piece.layers().size()) {
                        owed = java.util.Arrays.copyOf(owed, piece.layers().size());
                        contact.pending.put(slot, owed);
                    }
                }
                owed[i] += points;
            }
        }
    }

    /** What one entity owes, and what it was in contact with last interval. */
    private static final class Contact {
        private final EnumMap<ProtectionType, Double> pushed = new EnumMap<>(ProtectionType.class);
        private final EnumMap<EquipmentSlot, int[]> pending = new EnumMap<>(EquipmentSlot.class);
        private EnumSet<ProtectionType> lastActive = EnumSet.noneOf(ProtectionType.class);
        private int idle;

        private void flush(LivingEntity entity) {
            if (pending.isEmpty()) {
                return;
            }
            List<Map.Entry<EquipmentSlot, int[]>> owed = new ArrayList<>(pending.entrySet());
            pending.clear();
            for (Map.Entry<EquipmentSlot, int[]> entry : owed) {
                WornArmor piece = ArmorStacks.worn(entity, entry.getKey(), 1.0D);
                if (piece == null) {
                    continue;
                }
                int[] points = entry.getValue();
                boolean insertsChanged = false;
                int layers = Math.min(points.length, piece.layers().size());
                for (int i = 0; i < layers; i++) {
                    if (points[i] <= 0) {
                        continue;
                    }
                    ItemStack stack = piece.stackFor(i);
                    insertsChanged |= ArmorStacks.wear(stack, points[i]) > 0
                            && piece.layerToInsert()[i] >= 0;
                }
                if (insertsChanged) {
                    ArmorStacks.setInserts(piece.garment(), piece.inserts());
                }
            }
        }
    }
}
