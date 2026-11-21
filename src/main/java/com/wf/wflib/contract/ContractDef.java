package com.wf.wflib.contract;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.wf.wflib.contract.profile.Rank;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.List;

/**
 * A contract as data (a datapack file {@code data/<ns>/wflib/contracts/<name>.json}): who may take it, what stands at
 * the site, what has to be done, what it pays (money through the registered {@link com.wf.wflib.soldier.api.Wallet}; {@code rewards.items} instead when the server has no economy). {@code employer} is the faction whose side of the board this contract is on
 * (empty: neutral); an operator that faction considers too hot is not offered it. Validated on load; a bad file is refused with the reason.
 */
public record ContractDef(ResourceLocation id, String title, String description, Rank gate, int minDistance, int maxDistance,
                          int timeLimitMinutes, int radius, List<Squad> squads, List<Objective> objectives, int money, int rep,
                          int penaltyRep, String employer, int buyIn, List<Cargo> cargo, String source, List<Cargo> items, String base) {
    public static final int MAX_SQUADS = 8, MAX_SQUAD_SIZE = 60, MAX_MONEY = 10_000_000, MAX_REP = 1000;

    /**
     * @param faction the soldier faction (editor preset group) to raise from, when {@code units} is empty
     * @param units explicit variant ids, drawn in turn
     * @param mode "guard" or "patrol"
     */
    public record Squad(String faction, List<String> units, int count, String mode) {
        public Squad {
            units = List.copyOf(units);
        }
    }

    /** Goods issued to the operator when a smuggling run is accepted. */
    public record Cargo(ResourceLocation item, int count) {}

    public sealed interface Objective permits KillTarget, RetrieveItem, ClearArea, DeliverItem {
        String type();
    }

    /** Everyone in squad {@code squad} (0-based) is dead. */
    public record KillTarget(int squad) implements Objective {
        public String type() {
            return "kill_target";
        }
    }

    /** A participant carries {@code count} of {@code item}; it is placed in a container at the site and handed in on completion. */
    public record RetrieveItem(ResourceLocation item, int count) implements Objective {
        public String type() {
            return "retrieve_item";
        }
    }

    /** A participant stands within the site's radius carrying {@code count} of {@code item}; the goods are handed over there. */
    public record DeliverItem(ResourceLocation item, int count) implements Objective {
        public String type() {
            return "deliver_item";
        }
    }

    /** Every soldier of the site is dead. */
    public record ClearArea() implements Objective {
        public String type() {
            return "clear_area";
        }
    }

    public ContractDef {
        squads = List.copyOf(squads);
        objectives = List.copyOf(objectives);
    }

    public static ContractDef parse(ResourceLocation id, JsonObject j) {
        String title = string(j, "title");
        if (title.length() > 48) throw new IllegalArgumentException("title is at most 48 characters");
        String description = j.has("description") ? string(j, "description") : "";
        Rank gate = Rank.UNKNOWN;
        if (j.has("gate")) {
            String r = string(j.getAsJsonObject("gate"), "rank");
            gate = Rank.byId(r).orElseThrow(() -> new IllegalArgumentException("unknown rank '" + r + "'"));
        }
        JsonObject offer = obj(j, "offer");
        JsonArray dist = offer.getAsJsonArray("distance");
        if (dist == null || dist.size() != 2) throw new IllegalArgumentException("offer.distance is [min, max]");
        int lo = dist.get(0).getAsInt(), hi = dist.get(1).getAsInt();
        if (lo < 50 || hi < lo || hi > 5000) throw new IllegalArgumentException("offer.distance is 50-5000 with min <= max");
        int limit = range(offer, "time_limit_min", 1, 240);

        JsonObject site = obj(j, "site");
        int radius = range(site, "radius", 4, 64);
        List<Squad> squads = new ArrayList<>();
        for (JsonElement e : array(site, "squads")) {
            JsonObject s = e.getAsJsonObject();
            List<String> units = new ArrayList<>();
            if (s.has("units")) s.getAsJsonArray("units").forEach(u -> units.add(u.getAsString()));
            String faction = s.has("faction") ? s.get("faction").getAsString() : "";
            if (units.isEmpty() && faction.isEmpty()) throw new IllegalArgumentException("a squad needs a faction or units");
            String mode = s.has("mode") ? s.get("mode").getAsString() : "guard";
            if (!mode.equals("guard") && !mode.equals("patrol")) throw new IllegalArgumentException("squad mode is guard or patrol");
            squads.add(new Squad(faction, units, range(s, "count", 1, MAX_SQUAD_SIZE), mode));
        }
        if (squads.isEmpty() || squads.size() > MAX_SQUADS) throw new IllegalArgumentException("a site has 1-" + MAX_SQUADS + " squads");

        List<Objective> objectives = new ArrayList<>();
        for (JsonElement e : array(j, "objectives")) {
            JsonObject o = e.getAsJsonObject();
            String type = string(o, "type");
            objectives.add(switch (type) {
                case "kill_target" -> {
                    int squad = range(o, "squad", 0, squads.size() - 1);
                    yield new KillTarget(squad);
                }
                case "retrieve_item" -> new RetrieveItem(ResourceLocation.parse(string(o, "item")), o.has("count") ? range(o, "count", 1, 64) : 1);
                case "deliver_item" -> new DeliverItem(ResourceLocation.parse(string(o, "item")), o.has("count") ? range(o, "count", 1, 64) : 1);
                case "clear_area" -> new ClearArea();
                default -> throw new IllegalArgumentException("unknown objective type '" + type + "'");
            });
        }
        if (objectives.isEmpty()) throw new IllegalArgumentException("a contract needs an objective");

        JsonObject rewards = obj(j, "rewards");
        int money = range(rewards, "money", 0, MAX_MONEY);
        int rep = range(rewards, "rep", 0, MAX_REP);
        int penalty = j.has("failure") ? range(j.getAsJsonObject("failure"), "penalty_rep", 0, MAX_REP) : 0;
        String employer = j.has("employer") ? string(j, "employer") : "";
        int buyIn = 0;
        List<Cargo> cargo = new ArrayList<>();
        if (j.has("smuggling")) {
            JsonObject sm = j.getAsJsonObject("smuggling");
            buyIn = range(sm, "buy_in", 0, MAX_MONEY);
            for (JsonElement e : array(sm, "cargo")) {
                JsonObject c = e.getAsJsonObject();
                cargo.add(new Cargo(ResourceLocation.parse(string(c, "item")), range(c, "count", 1, 64)));
            }
        }
        boolean delivers = objectives.stream().anyMatch(o -> o instanceof DeliverItem);
        if (delivers != !cargo.isEmpty()) throw new IllegalArgumentException("a delivery objective and a smuggling cargo go together");
        List<Cargo> items = new ArrayList<>();
        if (rewards.has("items")) {
            for (JsonElement e : array(rewards, "items")) {
                JsonObject c = e.getAsJsonObject();
                items.add(new Cargo(ResourceLocation.parse(string(c, "item")), range(c, "count", 1, 64)));
            }
        }
        String source = j.has("source") ? string(j, "source") : "board";
        if (!source.equals("board") && !source.equals("intel")) throw new IllegalArgumentException("source is board or intel");
        return new ContractDef(id, title, description, gate, lo, hi, limit, radius, squads, objectives, money, rep, penalty, employer, buyIn, cargo, source, items, site.has("base") ? baseId(site) : "");
    }

    /** The file form of this contract: {@code parse(id, toJson())} gives it back. Daily contracts are saved this way. */
    public JsonObject toJson() {
        JsonObject j = new JsonObject();
        j.addProperty("title", title);
        if (!description.isEmpty()) j.addProperty("description", description);
        JsonObject g = new JsonObject();
        g.addProperty("rank", gate.id());
        j.add("gate", g);
        JsonObject offer = new JsonObject();
        JsonArray d = new JsonArray();
        d.add(minDistance);
        d.add(maxDistance);
        offer.add("distance", d);
        offer.addProperty("time_limit_min", timeLimitMinutes);
        j.add("offer", offer);
        JsonObject site = new JsonObject();
        site.addProperty("radius", radius);
        JsonArray sq = new JsonArray();
        for (Squad s : squads) {
            JsonObject o = new JsonObject();
            if (!s.faction().isEmpty()) o.addProperty("faction", s.faction());
            if (!s.units().isEmpty()) {
                JsonArray u = new JsonArray();
                s.units().forEach(u::add);
                o.add("units", u);
            }
            o.addProperty("count", s.count());
            o.addProperty("mode", s.mode());
            sq.add(o);
        }
        site.add("squads", sq);
        if (!base.isEmpty()) site.addProperty("base", base);
        j.add("site", site);
        JsonArray obj = new JsonArray();
        for (Objective o : objectives) {
            JsonObject x = new JsonObject();
            x.addProperty("type", o.type());
            switch (o) {
                case KillTarget k -> x.addProperty("squad", k.squad());
                case RetrieveItem r -> {
                    x.addProperty("item", r.item().toString());
                    x.addProperty("count", r.count());
                }
                case DeliverItem dl -> {
                    x.addProperty("item", dl.item().toString());
                    x.addProperty("count", dl.count());
                }
                case ClearArea c -> {
                }
            }
            obj.add(x);
        }
        j.add("objectives", obj);
        JsonObject rewards = new JsonObject();
        rewards.addProperty("money", money);
        rewards.addProperty("rep", rep);
        if (!items.isEmpty()) {
            JsonArray it = new JsonArray();
            for (Cargo c : items) {
                JsonObject o = new JsonObject();
                o.addProperty("item", c.item().toString());
                o.addProperty("count", c.count());
                it.add(o);
            }
            rewards.add("items", it);
        }
        j.add("rewards", rewards);
        JsonObject failure = new JsonObject();
        failure.addProperty("penalty_rep", penaltyRep);
        j.add("failure", failure);
        if (!employer.isEmpty()) j.addProperty("employer", employer);
        if (!cargo.isEmpty() || buyIn > 0) {
            JsonObject sm = new JsonObject();
            sm.addProperty("buy_in", buyIn);
            JsonArray cg = new JsonArray();
            for (Cargo c : cargo) {
                JsonObject o = new JsonObject();
                o.addProperty("item", c.item().toString());
                o.addProperty("count", c.count());
                cg.add(o);
            }
            sm.add("cargo", cg);
            j.add("smuggling", sm);
        }
        j.addProperty("source", source);
        return j;
    }

    /** The base archetype a site names ("outpost", "compound", "fortress"); anything else is refused when the file loads. */
    private static String baseId(JsonObject site) {
        String id = string(site, "base");
        com.wf.wflib.contract.site.BaseKind.byId(id);
        return id;
    }

    private static String string(JsonObject j, String key) {
        JsonElement e = j.get(key);
        if (e == null || !e.isJsonPrimitive() || e.getAsString().isBlank()) throw new IllegalArgumentException("missing '" + key + "'");
        return e.getAsString();
    }

    private static JsonObject obj(JsonObject j, String key) {
        JsonElement e = j.get(key);
        if (e == null || !e.isJsonObject()) throw new IllegalArgumentException("missing object '" + key + "'");
        return e.getAsJsonObject();
    }

    private static JsonArray array(JsonObject j, String key) {
        JsonElement e = j.get(key);
        if (e == null || !e.isJsonArray()) throw new IllegalArgumentException("missing list '" + key + "'");
        return e.getAsJsonArray();
    }

    private static int range(JsonObject j, String key, int lo, int hi) {
        JsonElement e = j.get(key);
        if (e == null || !e.isJsonPrimitive()) throw new IllegalArgumentException("missing number '" + key + "'");
        int v = e.getAsInt();
        if (v < lo || v > hi) throw new IllegalArgumentException("'" + key + "' is " + lo + "-" + hi);
        return v;
    }
}
