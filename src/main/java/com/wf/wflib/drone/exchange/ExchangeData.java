package com.wf.wflib.drone.exchange;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import com.wf.wflib.drone.api.Exchange;

/** Every arranged exchange, live and recently finished. */
public final class ExchangeData extends SavedData {

    public static final String NAME = "wflib_exchanges";
    /** How long a finished or cancelled exchange is kept before being forgotten. */
    private static final long RETENTION_TICKS = 6000L;

    private final Map<UUID, Exchange> byId = new LinkedHashMap<>();
    private final Map<UUID, Long> retiredAt = new LinkedHashMap<>();

    public static ExchangeData get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(
                new SavedData.Factory<>(ExchangeData::new, (tag, reg) -> ExchangeData.load(tag)), NAME);
    }

    public static ExchangeData get(ServerLevel level) {
        return get(level.getServer());
    }

    public static ExchangeData load(CompoundTag tag) {
        ExchangeData registry = new ExchangeData();
        ListTag list = tag.getList("Exchanges", Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            Exchange exchange = Exchange.load(list.getCompound(i));
            registry.byId.put(exchange.id(), exchange);
        }
        return registry;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        ListTag list = new ListTag();
        for (Exchange exchange : byId.values()) {
            list.add(exchange.save());
        }
        tag.put("Exchanges", list);
        return tag;
    }

    public void add(Exchange exchange) {
        byId.put(exchange.id(), exchange);
        setDirty();
    }

    @Nullable
    public Exchange byId(UUID id) {
        return byId.get(id);
    }

    public List<Exchange> active() {
        List<Exchange> out = new ArrayList<>();
        for (Exchange exchange : byId.values()) {
            if (exchange.active()) {
                out.add(exchange);
            }
        }
        return out;
    }

    public List<Exchange> all() {
        return new ArrayList<>(byId.values());
    }

    /**
     * @return the live exchange this station is part of, or null. One at a time per station keeps the
     *      bookkeeping honest and stops a pad being made to fly a dozen collection runs at once.
     */
    @Nullable
    public Exchange activeFor(String code) {
        for (Exchange exchange : byId.values()) {
            if (exchange.active() && exchange.involves(code)) {
                return exchange;
            }
        }
        return null;
    }

    /**
     * Drop exchanges that finished long enough ago that nobody is still waiting to hear about them.
     */
    public void prune(long now) {
        boolean changed = false;
        for (Exchange exchange : new ArrayList<>(byId.values())) {
            if (exchange.active()) {
                retiredAt.remove(exchange.id());
                continue;
            }
            Long since = retiredAt.putIfAbsent(exchange.id(), now);
            if (since != null && now - since > RETENTION_TICKS) {
                byId.remove(exchange.id());
                retiredAt.remove(exchange.id());
                changed = true;
            }
        }
        if (changed) {
            setDirty();
        }
    }

    public void touch() {
        setDirty();
    }
}
