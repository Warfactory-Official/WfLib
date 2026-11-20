package com.wf.wflib.contract.daily;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.wf.wflib.contract.ContractDef;
import com.wf.wflib.contract.Contracts;
import com.wf.wflib.contract.SessionBook;
import com.wf.wflib.contract.difficulty.Difficulty;
import com.wf.wflib.contract.difficulty.DifficultyConfig;
import com.wf.wflib.soldier.api.RandomForces;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

/**
 * The day's randomised contracts, and the day's soldiers. Each in-game day the board is redrawn (offers from yesterday that someone is
 * still working stay until they finish), the generated soldier pool is redrawn, and the new contracts are published to
 * {@link Contracts}. Saved with the world, so a restart keeps the day's board. The same world seed and day give the same board.
 */
public final class DailyBoard extends SavedData {
    /** The soldier pools were checked since this server started. */
    private static boolean started;
    private long day = -1;
    /** Today's offers, then yesterday's that a session still needs. */
    private final Map<ResourceLocation, ContractDef> defs = new LinkedHashMap<>();

    public static DailyBoard get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(new SavedData.Factory<>(DailyBoard::new, (tag, reg) -> load(tag)), "wflib_daily");
    }

    public long day() {
        return day;
    }

    /** Server stopped: the next one checks the soldier pools again. */
    public static void stopped() {
        started = false;
    }

    /** About once a second: redraw when the day has turned. */
    public static void tick(MinecraftServer server) {
        DailyBoard board = get(server);
        long today = Difficulty.day(server);
        if (!started) {
            started = true;
            for (String faction : List.of("enemy", "friendly")) RandomForces.ensureCurrent(server, faction);
        }
        if (board.day != today) {
            board.redraw(server, today);
        } else if (Contracts.get(board.defs.keySet().stream().findFirst().orElse(ResourceLocation.parse("wflib:none"))).isEmpty() && !board.defs.isEmpty()) {
            Contracts.setDaily(board.defs.values());                 // after a restart or a reload
        }
    }

    /** Draws {@code day}'s board now (ops can force it). */
    public void redraw(MinecraftServer server, long today) {
        Difficulty difficulty = Difficulty.get(server);
        double world = difficulty.worldTier(server);
        for (String faction : List.of("enemy", "friendly")) RandomForces.regenerate(server, faction, today);
        // keep yesterday's offers that someone is working
        List<ContractDef> held = new ArrayList<>();
        var sessions = SessionBook.get(server).all();
        for (ContractDef d : defs.values()) {
            if (sessions.stream().anyMatch(s -> s.def().equals(d.id()))) held.add(d);
        }
        defs.clear();
        Random rnd = new Random(server.overworld().getSeed() ^ (today * 0x9E3779B97F4A7C15L));
        List<DailyGen.Kind> kinds = new ArrayList<>();
        int n = DifficultyConfig.DAILY_OFFERS.get();
        DailyGen.Forces forces = (faction, grade) -> RandomForces.units(server, faction, grade);
        for (int i = 0; i < n; i++) {
            DailyGen.Kind kind = DailyGen.pick(rnd, kinds);
            kinds.add(kind);
            // easy to hard: the day's tier, a little under it up to well over it
            double tier = Math.max(0, Math.min(difficulty.curve().max(), world + rnd.nextDouble() * 3 - 1));
            ResourceLocation id = ResourceLocation.fromNamespaceAndPath(com.wf.wflib.core.api.WFLib.MODID, "daily/d" + today + "_" + i);
            try {
                ContractDef def = DailyGen.generate(id, kind, rnd, tier, difficulty.rewardScale(), forces);
                defs.put(id, def);
            } catch (IllegalArgumentException e) {
                com.wf.wflib.contract.ContractSystem.LOGGER.warn("A daily contract was not made: {}", e.getMessage());
            }
        }
        held.forEach(d -> defs.put(d.id(), d));
        day = today;
        setDirty();
        Contracts.setDaily(defs.values());
        com.wf.wflib.contract.ContractSystem.LOGGER.info("Day {}: {} randomised contracts at world {}", today + 1, defs.size() - held.size(), com.wf.wflib.contract.difficulty.Tier.label(world));
    }

    private static DailyBoard load(CompoundTag tag) {
        DailyBoard b = new DailyBoard();
        b.day = tag.getLong("day");
        JsonArray list = JsonParser.parseString(tag.getString("defs")).getAsJsonArray();
        for (JsonElement e : list) {
            JsonObject o = e.getAsJsonObject();
            ResourceLocation id = ResourceLocation.parse(o.get("id").getAsString());
            try {
                b.defs.put(id, ContractDef.parse(id, o.getAsJsonObject("def")));
            } catch (RuntimeException ex) {
                com.wf.wflib.contract.ContractSystem.LOGGER.warn("Daily contract {} was not restored: {}", id, ex.getMessage());
            }
        }
        return b;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        tag.putLong("day", day);
        JsonArray list = new JsonArray();
        for (ContractDef d : defs.values()) {
            JsonObject o = new JsonObject();
            o.addProperty("id", d.id().toString());
            o.add("def", d.toJson());
            list.add(o);
        }
        tag.putString("defs", list.toString());
        return tag;
    }
}
