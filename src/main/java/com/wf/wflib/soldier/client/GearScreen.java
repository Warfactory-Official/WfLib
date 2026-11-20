package com.wf.wflib.soldier.client;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.wf.wflib.soldier.GearBook;
import com.wf.wflib.soldier.GearPackets;
import com.wf.wflib.soldier.SoldierCatalogue;
import com.wf.wflib.soldier.Want;
import com.wf.wflib.soldier.api.SoldierRole;
import com.wf.wflib.ui.api.Signals;
import com.wf.wflib.ui.api.Subject;
import com.wf.wflib.ui.api.UiContext;
import com.wf.wflib.ui.api.UiHud;
import com.wf.wflib.ui.api.UiScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.RemotePlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * Arsenal-style gear screen: category rail, what can be chosen with the force's stock, the loadout being built. A
 * loadout is built by hand or loaded from a preset and given to the selected soldiers; nothing is handed over here, the
 * soldiers fetch it from storage. Layout in {@link GearLayout}.
 */
public final class GearScreen {
    static final String[] RAIL = {"gun", "grenade", "head", "chest", "legs", "feet", "presets"};
    static final String[] RAIL_LABEL = {"Gun", "Grenades", "Head", "Body", "Legs", "Feet", "Presets"};

    record Row(SoldierCatalogue.Choice choice, int qty) {}

    record Line(String label, String slot, SoldierCatalogue.Choice choice, int have) {}

    record Kind(String key, String label, int count, int have) {}

    record Tab(int index, String label, ItemStack icon) {}

    static GearScreen current;
    static final Subject<GearScreen> GEAR = Subject.register("gear", GearScreen.class, () -> current);
    static final Subject<Row> ROW = Subject.register("gear_row", Row.class);
    static final Subject<GearBook.Preset> PRESET = Subject.register("gear_preset", GearBook.Preset.class);
    static final Subject<Line> LINE = Subject.register("gear_line", Line.class);
    static final Subject<Kind> KIND = Subject.register("gear_kind", Kind.class);
    static final Subject<Tab> TAB = Subject.register("gear_tab", Tab.class);
    static final Subject<String> NEED = Subject.register("gear_need", String.class);

    private static List<UUID> pending = List.of();

    private final List<UUID> targets;
    private final SoldierCatalogue catalogue = SoldierCatalogue.create();
    private final Map<String, Integer> stock = new LinkedHashMap<>();
    private final List<GearBook.Preset> presets = new ArrayList<>();
    private final List<String> needLines = new ArrayList<>();
    private final List<Tab> tabs = new ArrayList<>();

    private String gun = "", role = "";
    private final List<String> grenades = new ArrayList<>();
    private final Map<String, String> armour = new LinkedHashMap<>();
    private int spares = -1, rail = 0;
    private String message = "";
    private boolean claims;
    private List<Row> rows = List.of();
    private String rowsFor = "";
    private List<Line> lines = List.of();
    private List<Kind> kinds = List.of();
    private int loadoutRev = 1, linesRev;
    private SoldierCatalogue.Choice hovered;
    private long hoveredFrame = -1;
    private RemotePlayer preview;
    private String previewSig = "";
    private UiScreen screen;
    float yaw = -20;

    public static void register() {
        Signals.text(GEAR, "title", 64, s -> "Gear  -  " + s.targetLabel());
        Signals.text(GEAR, "storage", 48, s -> s.claims ? "Storage: your WarForge claims + depots" : "Storage: marked depots");
        Signals.text(GEAR, "message", 64, s -> s.message);
        Signals.text(GEAR, "assign", 48, s -> "ASSIGN TO " + s.targetLabel().toUpperCase(Locale.ROOT));
        Signals.text(GEAR, "role_text", 16, s -> s.role.isEmpty() ? "unchanged" : s.role);
        Signals.text(GEAR, "mags_text", 12, s -> s.spares < 0 ? "unchanged" : Integer.toString(s.spares));
        Signals.num(GEAR, "yaw", s -> s.yaw);
        Signals.bool(GEAR, "presets_tab", s -> RAIL[s.rail].equals("presets"));
        Signals.bool(GEAR, "rows_empty", s -> s.rows().isEmpty());
        Signals.bool(GEAR, "presets_empty", s -> s.presets.isEmpty());
        Signals.bool(GEAR, "kinds_empty", s -> s.kinds().isEmpty());
        Signals.bool(GEAR, "needs_any", s -> !s.needLines.isEmpty());
        Signals.items(GEAR, "rows", GearScreen::rows, ROW);
        Signals.items(GEAR, "presets", s -> s.presets, PRESET);
        Signals.items(GEAR, "slots", GearScreen::lines, LINE);
        Signals.items(GEAR, "kinds", GearScreen::kinds, KIND);
        Signals.items(GEAR, "tabs", s -> s.tabs, TAB);
        Signals.items(GEAR, "needs", s -> s.needLines, NEED);

        Signals.text(ROW, "label", 64, r -> r.choice.label());
        Signals.text(ROW, "qty", 8, r -> Integer.toString(r.qty));
        Signals.text(ROW, "count", 8, r -> {
            int n = current == null ? 0 : current.count(r.choice);
            return n > 0 ? "x" + n : "";
        });
        Signals.bool(ROW, "have", r -> r.qty > 0);
        Signals.bool(ROW, "chosen", r -> current != null && current.isChosen(r.choice));
        Signals.bool(ROW, "counted", r -> current != null && current.count(r.choice) > 0);

        Signals.text(PRESET, "name", 32, GearBook.Preset::name);
        Signals.text(PRESET, "summary", 120, p -> current == null ? "" : current.summary(p));

        Signals.text(LINE, "label", 12, Line::label);
        Signals.text(LINE, "name", 64, l -> l.choice == null ? "" : l.choice.label());
        Signals.text(LINE, "have", 8, l -> Integer.toString(l.have));
        Signals.bool(LINE, "set", l -> l.choice != null);
        Signals.bool(LINE, "ok", l -> l.have > 0);

        Signals.text(KIND, "label", 64, Kind::label);
        Signals.text(KIND, "have", 8, k -> Integer.toString(k.have));
        Signals.bool(KIND, "ok", k -> k.have >= k.count);

        Signals.bool(TAB, "on", t -> current != null && current.rail == t.index);

        Signals.text(NEED, "line", 160, line -> line);
    }

    /** Asks the server for the screen; {@code soldiers} are the body UUIDs the squad screen lists. */
    public static void open(List<UUID> soldiers) {
        pending = List.copyOf(soldiers);
        send("open", new JsonObject(), pending);
    }

    static void send(String action, JsonObject data, List<UUID> ids) {
        var arr = new JsonArray();
        ids.forEach(i -> arr.add(i.toString()));
        data.add("ids", arr);
        GearPackets.request(action, data);
    }

    public static void receive(String json) {
        var j = JsonParser.parseString(json).getAsJsonObject();
        if (current != null && Minecraft.getInstance().screen == current.screen) current.take(j);
        else if (j.get("open").getAsBoolean()) {
            var s = new GearScreen(pending);
            s.take(j);
            s.show();
        }
    }

    private GearScreen(List<UUID> targets) {
        this.targets = targets;
        ItemStack[] icons = pickIcons();
        for (int i = 0; i < RAIL.length; i++) tabs.add(new Tab(i, RAIL_LABEL[i], icons[i]));
    }

    private void show() {
        current = this;
        GearLayout.SEARCH.set("");
        GearLayout.NAME.set("");
        screen = UiScreen.open(GearLayout.layout(), UiContext.EMPTY, Component.literal("Soldier gear"),
                new UiScreen.Options(false, false, null, () -> {
                    if (current == this) current = null;
                }));
    }

    private void take(JsonObject j) {
        String said = j.get("message").getAsString();
        if (!said.isEmpty()) message = said;
        claims = j.get("claims").getAsBoolean();
        stock.clear();
        j.getAsJsonObject("stock").entrySet().forEach(e -> stock.put(e.getKey(), e.getValue().getAsInt()));
        presets.clear();
        for (var e : j.getAsJsonArray("presets")) presets.add(GearBook.Preset.read(e.getAsJsonObject()));
        needLines.clear();
        for (var e : j.getAsJsonArray("targets")) {
            var t = e.getAsJsonObject();
            List<String> list = new ArrayList<>();
            t.getAsJsonArray("needs").forEach(n -> list.add(n.getAsString()));
            if (!list.isEmpty()) {
                needLines.add(t.get("name").getAsString().replace('_', ' ') + ": " + String.join(", ", list));
            }
        }
        rowsFor = "";
        loadoutRev++;
    }

    private int qty(String key, String slot) {
        return stock.getOrDefault(key + "|" + slot, 0);
    }

    private SoldierCatalogue.Choice choice(String key) {
        return key.isEmpty() ? null : catalogue.get(key);
    }

    private Want want() {
        return new Want(gun, grenades, armour);
    }

    private JsonObject assignData() {
        var d = new JsonObject();
        d.add("want", want().json());
        d.addProperty("role", role);
        d.addProperty("spares", spares);
        return d;
    }

    private int count(SoldierCatalogue.Choice c) {
        return c.slot().equals("grenade") ? Collections.frequency(grenades, c.key()) : 0;
    }

    private boolean isChosen(SoldierCatalogue.Choice c) {
        return switch (c.slot()) {
            case "gun" -> gun.equals(c.key());
            case "grenade" -> grenades.contains(c.key());
            default -> c.key().equals(armour.get(c.slot()));
        };
    }

    private String targetLabel() {
        if (targets.size() == 1) {
            return SquadClient.members.stream().filter(m -> m.uuid().equals(targets.get(0))).map(m -> m.name().replace('_', ' ')).findFirst().orElse("1 soldier");
        }
        return targets.size() + " soldiers";
    }

    private String summary(GearBook.Preset p) {
        List<String> bits = new ArrayList<>();
        var gc = choice(p.want().gun());
        if (gc != null) bits.add(gc.label());
        if (!p.want().grenades().isEmpty()) bits.add(p.want().grenades().size() + " grenades");
        if (!p.want().armour().isEmpty()) bits.add(p.want().armour().size() + " armour");
        if (!p.role().isEmpty()) bits.add(p.role());
        return bits.isEmpty() ? "empty" : String.join(", ", bits);
    }

    /** One piece of real equipment per category, random per open; a vanilla stand-in where nothing is installed. */
    private ItemStack[] pickIcons() {
        ItemStack[] stand = {new ItemStack(Items.CROSSBOW), new ItemStack(Items.FIRE_CHARGE), new ItemStack(Items.IRON_HELMET),
                new ItemStack(Items.IRON_CHESTPLATE), new ItemStack(Items.IRON_LEGGINGS), new ItemStack(Items.IRON_BOOTS), new ItemStack(Items.BOOK)};
        var random = new java.util.Random();
        for (int i = 0; i < RAIL.length - 1; i++) {
            String slot = RAIL[i];
            List<SoldierCatalogue.Choice> of = catalogue.all().stream().filter(c -> c.slot().equals(slot)).toList();
            if (!of.isEmpty()) stand[i] = of.get(random.nextInt(of.size())).stack();
        }
        return stand;
    }

    private List<Row> rows() {
        String slot = RAIL[rail];
        String q = GearLayout.SEARCH.text().toString().toLowerCase(Locale.ROOT).strip();
        String sig = slot + "|" + q + "|" + stock.hashCode();
        if (sig.equals(rowsFor)) return rows;
        List<Row> out = new ArrayList<>();
        for (var c : catalogue.all()) {
            if (!c.slot().equals(slot)) continue;
            String hay = (c.label() + " " + c.key()).toLowerCase(Locale.ROOT);
            if (!q.isEmpty() && !hay.contains(q)) continue;
            out.add(new Row(c, qty(c.key(), c.slot())));
        }
        out.sort(Comparator.comparingInt((Row r) -> -r.qty).thenComparing(r -> r.choice.label()));
        rows = out;
        rowsFor = sig;
        return rows;
    }

    private void rebuildLoadout() {
        if (linesRev == loadoutRev) return;
        linesRev = loadoutRev;
        List<Line> l = new ArrayList<>();
        var g = choice(gun);
        l.add(new Line("Gun", "gun", g, g == null ? 0 : qty(g.key(), "gun")));
        for (int i = 0; i < Want.SLOTS.size(); i++) {
            String s = Want.SLOTS.get(i);
            var c = choice(armour.getOrDefault(s, ""));
            l.add(new Line(RAIL_LABEL[2 + i], s, c, c == null ? 0 : qty(c.key(), s)));
        }
        lines = l;
        Map<String, Integer> counts = new LinkedHashMap<>();
        grenades.forEach(k -> counts.merge(k, 1, Integer::sum));
        List<Kind> k = new ArrayList<>();
        counts.forEach((key, n) -> {
            var c = choice(key);
            k.add(new Kind(key, (c == null ? key : c.label()) + " x" + n, n, c == null ? 0 : qty(c.key(), "grenade")));
        });
        kinds = k;
    }

    private List<Line> lines() {
        rebuildLoadout();
        return lines;
    }

    private List<Kind> kinds() {
        rebuildLoadout();
        return kinds;
    }

    // ---- actions, called by the layout

    void tab(int index) {
        rail = index;
        rowsFor = "";
    }

    void pick(SoldierCatalogue.Choice c, boolean add) {
        String slot = c.slot();
        switch (slot) {
            case "gun" -> gun = gun.equals(c.key()) ? "" : c.key();
            case "grenade" -> {
                if (add) {
                    if (grenades.size() < Want.MAX_GRENADES) grenades.add(c.key());
                } else grenades.remove(c.key());
            }
            default -> {
                if (c.key().equals(armour.get(slot))) armour.remove(slot);
                else armour.put(slot, c.key());
            }
        }
        loadoutRev++;
    }

    void clearSlot(String slot) {
        if (slot.equals("gun")) gun = "";
        else armour.remove(slot);
        loadoutRev++;
    }

    void dropGrenades(String key) {
        grenades.removeIf(k -> k.equals(key));
        loadoutRev++;
    }

    void load(GearBook.Preset p) {
        gun = p.want().gun();
        grenades.clear();
        grenades.addAll(p.want().grenades());
        armour.clear();
        armour.putAll(p.want().armour());
        role = p.role();
        spares = p.spares();
        GearLayout.NAME.set(p.name());
        message = "Loaded " + p.name();
        loadoutRev++;
    }

    void clear() {
        gun = "";
        grenades.clear();
        armour.clear();
        role = "";
        spares = -1;
        message = "Cleared";
        loadoutRev++;
    }

    void deletePreset(GearBook.Preset p) {
        var d = new JsonObject();
        d.addProperty("name", p.name());
        send("delete", d, targets);
    }

    void save() {
        String name = GearLayout.NAME.text().toString();
        var preset = new GearBook.Preset(name.isBlank() ? "Preset" : name, want(), role, spares);
        send("save", preset.json(), targets);
        message = "Saving...";
    }

    void assign() {
        send("assign", assignData(), targets);
        message = "Sending...";
    }

    void close() {
        screen.onClose();
    }

    void stepRole(int by) {
        var all = SoldierRole.values();
        int i = -1;
        for (int k = 0; k < all.length; k++) if (all[k].id.equals(role)) i = k;
        int next = i + by;
        if (role.isEmpty()) next = by > 0 ? 0 : all.length - 1;
        role = next < 0 || next >= all.length ? "" : all[next].id;
    }

    void stepSpares(int by) {
        if (by > 0) spares = spares >= 12 ? -1 : spares + 1;
        else spares = spares < 0 ? 12 : spares - 1;
    }

    void hover(SoldierCatalogue.Choice c) {
        hovered = c;
        hoveredFrame = UiHud.frame();
    }

    // ---- preview model

    /** A player-shaped figure wearing the loadout, with the hovered item tried on in place of what it covers. */
    RemotePlayer previewEntity() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return null;
        if (preview == null) {
            var team = new net.minecraft.world.scores.PlayerTeam(new net.minecraft.world.scores.Scoreboard(), "gear_preview");
            team.setNameTagVisibility(net.minecraft.world.scores.Team.Visibility.NEVER);
            preview = new RemotePlayer(mc.level, new com.mojang.authlib.GameProfile(UUID.nameUUIDFromBytes("wflib:gear_preview".getBytes(java.nio.charset.StandardCharsets.UTF_8)), "GearPreview")) {
                @Override
                public net.minecraft.world.scores.PlayerTeam getTeam() {
                    return team;
                }
            };
        }
        dress(hoveredFrame == UiHud.frame() && !RAIL[rail].equals("presets") ? hovered : null);
        return preview;
    }

    private void dress(SoldierCatalogue.Choice candidate) {
        String sig = gun + "|" + armour + "|" + (candidate == null ? "" : candidate.key() + candidate.slot());
        if (sig.equals(previewSig)) return;
        previewSig = sig;
        for (var s : new EquipmentSlot[]{EquipmentSlot.MAINHAND, EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET}) preview.setItemSlot(s, ItemStack.EMPTY);
        var weapon = choice(gun);
        if (weapon != null) preview.setItemSlot(EquipmentSlot.MAINHAND, weapon.stack().copy());
        armour.forEach((slot, key) -> {
            var c = choice(key);
            if (c != null) preview.setItemSlot(SoldierCatalogue.slot(slot), c.stack().copy());
        });
        if (candidate == null || candidate.slot().equals("grenade")) return;
        if (candidate.slot().equals("gun")) {
            preview.setItemSlot(EquipmentSlot.MAINHAND, candidate.stack().copy());
            return;
        }
        armour.forEach((slot, key) -> {
            var old = choice(key);
            if (old != null && !Collections.disjoint(old.covers(), candidate.covers())) preview.setItemSlot(SoldierCatalogue.slot(slot), ItemStack.EMPTY);
        });
        preview.setItemSlot(SoldierCatalogue.slot(candidate.slot()), candidate.stack().copy());
    }
}
