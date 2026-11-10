package com.wf.wflib.soldier.client;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.wf.wflib.soldier.GearBook;
import com.wf.wflib.soldier.GearPackets;
import com.wf.wflib.soldier.SoldierCatalogue;
import com.wf.wflib.soldier.Want;
import com.wf.wflib.soldier.api.SoldierRole;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * The gear screen, in the manner of Arma 3's arsenal but inside one box: a rail of categories on the left, a list of what
 * can be chosen with how many the force has in storage, and the loadout being built on the right. A loadout is either
 * built by hand and given to the selected soldiers, or loaded from a saved preset. Nothing is handed over here: the
 * soldiers go and find it in storage.
 */
public final class GearScreen extends Screen {
    private static final int BG = 0xF0101418, PANEL = 0xFF1A2129, HEAD = 0xFF222B35, INK = 0xFFE4EDF7, DIM = 0xFF8C9BAA,
            ACCENT = 0xFF6ED5BD, HAVE = 0xFF7BD88F, NONE = 0xFFE06060, ROW = 0xFF202A34, ROW_ON = 0xFF2C4A47;
    private static final String[] RAIL = {"gun", "grenade", "head", "chest", "legs", "feet", "presets"};
    private static final String[] RAIL_LABEL = {"Gun", "Grenades", "Head", "Body", "Legs", "Feet", "Presets"};
    private static final int ROW_H = 20, TOP = 22, BOTTOM = 24, RAIL_W = 26;

    private static List<UUID> pending = List.of();

    private record Act(int x0, int y0, int x1, int y1, Runnable left, Runnable right) {
        boolean has(double x, double y) {
            return x >= x0 && x < x1 && y >= y0 && y < y1;
        }
    }

    private record Row(SoldierCatalogue.Choice choice, int qty) {}

    private final List<UUID> targets;
    private final SoldierCatalogue catalogue = SoldierCatalogue.create();
    private final Map<String, Integer> stock = new LinkedHashMap<>();
    private final List<GearBook.Preset> presets = new ArrayList<>();
    private final Map<String, List<String>> needs = new LinkedHashMap<>();
    private final List<Act> acts = new ArrayList<>();

    private String gun = "", role = "";
    private final List<String> grenades = new ArrayList<>();
    private final Map<String, String> armour = new LinkedHashMap<>();
    private int spares = -1, rail = 0, scroll = 0, presetScroll = 0;
    private String message = "";
    private boolean claims;
    private List<Row> rows = List.of();
    private String rowsFor = "";
    private EditBox search, nameBox;
    /** One piece of real equipment per category, drawn at random each time the screen opens; a vanilla stand-in where nothing is installed. */
    private ItemStack[] icons;
    private int px, py, pw, ph;

    // ---- opening and talking to the server

    /** Asks the server for the screen; {@code soldiers} are the body UUIDs the squad screen lists. */
    public static void open(List<UUID> soldiers) {
        pending = List.copyOf(soldiers);
        send("open", new JsonObject(), pending);
    }

    private static void send(String action, JsonObject data, List<UUID> ids) {
        var arr = new JsonArray();
        ids.forEach(i -> arr.add(i.toString()));
        data.add("ids", arr);
        GearPackets.request(action, data);
    }

    public static void receive(String json) {
        Minecraft mc = Minecraft.getInstance();
        var j = JsonParser.parseString(json).getAsJsonObject();
        if (mc.screen instanceof GearScreen s) s.take(j);
        else if (j.get("open").getAsBoolean()) {
            var s = new GearScreen(pending);
            s.take(j);
            mc.setScreen(s);
        }
    }

    private GearScreen(List<UUID> targets) {
        super(Component.literal("Soldier gear"));
        this.targets = targets;
    }

    private void take(JsonObject j) {
        String said = j.get("message").getAsString();
        if (!said.isEmpty()) message = said;
        claims = j.get("claims").getAsBoolean();
        stock.clear();
        j.getAsJsonObject("stock").entrySet().forEach(e -> stock.put(e.getKey(), e.getValue().getAsInt()));
        presets.clear();
        for (var e : j.getAsJsonArray("presets")) presets.add(GearBook.Preset.read(e.getAsJsonObject()));
        needs.clear();
        for (var e : j.getAsJsonArray("targets")) {
            var t = e.getAsJsonObject();
            List<String> list = new ArrayList<>();
            t.getAsJsonArray("needs").forEach(n -> list.add(n.getAsString()));
            if (!list.isEmpty()) needs.put(t.get("name").getAsString(), list);
        }
        rowsFor = "";
    }

    // ---- loadout

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

    private void load(GearBook.Preset p) {
        gun = p.want().gun();
        grenades.clear();
        grenades.addAll(p.want().grenades());
        armour.clear();
        armour.putAll(p.want().armour());
        role = p.role();
        spares = p.spares();
        nameBox.setValue(p.name());
        message = "Loaded " + p.name();
    }

    private void clear() {
        gun = "";
        grenades.clear();
        armour.clear();
        role = "";
        spares = -1;
        message = "Cleared";
    }

    private void pick(SoldierCatalogue.Choice c, boolean add) {
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
    }

    private String targetLabel() {
        if (targets.size() == 1) {
            return SquadClient.members.stream().filter(m -> m.uuid().equals(targets.get(0))).map(m -> m.name().replace('_', ' ')).findFirst().orElse("1 soldier");
        }
        return targets.size() + " soldiers";
    }

    // ---- layout

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

    @Override
    protected void init() {
        if (icons == null) icons = pickIcons();
        pw = Math.min(width - 16, 520);
        ph = Math.min(height - 16, 300);
        px = (width - pw) / 2;
        py = (height - ph) / 2;
        int listX = px + RAIL_W + 8;
        search = new EditBox(font, listX, py + TOP + 2, listW() - 4, 14, Component.literal("Search"));
        search.setHint(Component.literal("Search").withStyle(net.minecraft.ChatFormatting.DARK_GRAY));
        search.setResponder(s -> {
            rowsFor = "";
            scroll = 0;
        });
        addRenderableWidget(search);
        nameBox = new EditBox(font, rightX() + 2, py + ph - BOTTOM - 20, rightW() - 4, 14, Component.literal("Preset name"));
        nameBox.setHint(Component.literal("Preset name").withStyle(net.minecraft.ChatFormatting.DARK_GRAY));
        nameBox.setMaxLength(32);
        addRenderableWidget(nameBox);
    }

    private int rightW() {
        return 178;
    }

    private int rightX() {
        return px + pw - rightW() - 6;
    }

    private int listW() {
        return rightX() - 6 - (px + RAIL_W + 8);
    }

    private int listY() {
        return py + TOP + 20 + 14;
    }

    private int listBottom() {
        return py + ph - BOTTOM - 4;
    }

    private List<Row> rows() {
        String slot = RAIL[rail];
        String q = search.getValue().toLowerCase(Locale.ROOT).strip();
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

    // ---- drawing

    @Override
    public void renderBackground(GuiGraphics g, int mx, int my, float pt) {
        g.fill(0, 0, width, height, 0x99000000);
        g.fill(px, py, px + pw, py + ph, BG);
        frame(g, px, py, px + pw, py + ph, 0xFF3A4654);
    }

    private static void frame(GuiGraphics g, int x0, int y0, int x1, int y1, int c) {
        g.fill(x0, y0, x1, y0 + 1, c);
        g.fill(x0, y1 - 1, x1, y1, c);
        g.fill(x0, y0, x0 + 1, y1, c);
        g.fill(x1 - 1, y0, x1, y1, c);
    }

    @Override
    public void render(GuiGraphics g, int mx, int my, float pt) {
        acts.clear();
        super.render(g, mx, my, pt);
        title(g);
        rail(g, mx, my);
        boolean presetsTab = RAIL[rail].equals("presets");
        search.visible = !presetsTab;
        if (presetsTab) presetList(g, mx, my);
        else itemList(g, mx, my);
        loadout(g, mx, my);
        bar(g, mx, my);
    }

    private void title(GuiGraphics g) {
        g.fill(px + 1, py + 1, px + pw - 1, py + TOP, HEAD);
        g.drawString(font, "Gear  -  " + targetLabel(), px + 8, py + 7, INK, false);
        String where = claims ? "Storage: your WarForge claims + depots" : "Storage: marked depots";
        g.drawString(font, where, px + pw - 8 - font.width(where), py + 7, DIM, false);
    }

    private void rail(GuiGraphics g, int mx, int my) {
        for (int i = 0; i < RAIL.length; i++) {
            int x0 = px + 4, y0 = py + TOP + 4 + i * (RAIL_W + 2);
            boolean on = i == rail, over = mx >= x0 && mx < x0 + RAIL_W && my >= y0 && my < y0 + RAIL_W;
            g.fill(x0, y0, x0 + RAIL_W, y0 + RAIL_W, on ? ROW_ON : over ? 0xFF2A3541 : PANEL);
            if (on) g.fill(x0, y0, x0 + 2, y0 + RAIL_W, ACCENT);
            g.renderItem(icons[i], x0 + 5, y0 + 5);
            final int idx = i;
            acts.add(new Act(x0, y0, x0 + RAIL_W, y0 + RAIL_W, () -> {
                rail = idx;
                scroll = 0;
                rowsFor = "";
            }, null));
            if (over) g.renderTooltip(font, Component.literal(RAIL_LABEL[i]), mx, my);
        }
    }

    private void itemList(GuiGraphics g, int mx, int my) {
        int x0 = px + RAIL_W + 8, x1 = x0 + listW(), y0 = listY(), y1 = listBottom();
        g.fill(x0, py + TOP + 20, x1, y0, HEAD);
        g.drawString(font, "Qty", x0 + 24, py + TOP + 24, DIM, false);
        g.drawString(font, "Name", x0 + 64, py + TOP + 24, DIM, false);
        g.fill(x0, y0, x1, y1, PANEL);
        List<Row> list = rows();
        scroll = Math.max(0, Math.min(scroll, Math.max(0, list.size() * ROW_H - (y1 - y0))));
        if (list.isEmpty()) g.drawCenteredString(font, "Nothing installed for this slot", (x0 + x1) / 2, y0 + 12, DIM);
        g.enableScissor(x0, y0, x1, y1);
        ItemStack hover = null;
        for (int i = 0; i < list.size(); i++) {
            int ry = y0 + i * ROW_H - scroll;
            if (ry + ROW_H < y0 || ry > y1) continue;
            Row r = list.get(i);
            var c = r.choice();
            boolean on = isChosen(c), over = mx >= x0 && mx < x1 && my >= Math.max(ry, y0) && my < Math.min(ry + ROW_H, y1);
            g.fill(x0, ry, x1, ry + ROW_H - 1, on ? ROW_ON : over ? 0xFF28333F : ROW);
            g.renderItem(c.stack(), x0 + 3, ry + 2);
            String q = r.qty() > 0 ? Integer.toString(r.qty()) : "0";
            g.drawString(font, q, x0 + 56 - font.width(q), ry + 6, r.qty() > 0 ? HAVE : NONE, false);
            int count = c.slot().equals("grenade") ? java.util.Collections.frequency(grenades, c.key()) : 0;
            String label = font.plainSubstrByWidth(c.label(), x1 - x0 - 64 - (count > 0 ? 24 : 6));
            g.drawString(font, label, x0 + 64, ry + 6, r.qty() > 0 ? INK : DIM, false);
            if (count > 0) g.drawString(font, "x" + count, x1 - 4 - font.width("x" + count), ry + 6, ACCENT, false);
            if (over) hover = c.stack();
            if (ry + ROW_H > y0 && ry < y1) {
                acts.add(new Act(x0, Math.max(ry, y0), x1, Math.min(ry + ROW_H, y1), () -> pick(c, true), () -> pick(c, false)));
            }
        }
        g.disableScissor();
        if (list.size() * ROW_H > y1 - y0) {                                   // a scroll bar
            int total = list.size() * ROW_H, bar = Math.max(12, (y1 - y0) * (y1 - y0) / total);
            int by = y0 + (int) ((long) (y1 - y0 - bar) * scroll / Math.max(1, total - (y1 - y0)));
            g.fill(x1 - 3, by, x1 - 1, by + bar, 0xFF55657A);
        }
        if (hover != null) g.renderTooltip(font, hover, mx, my);
    }

    private boolean isChosen(SoldierCatalogue.Choice c) {
        return switch (c.slot()) {
            case "gun" -> gun.equals(c.key());
            case "grenade" -> grenades.contains(c.key());
            default -> c.key().equals(armour.get(c.slot()));
        };
    }

    private void presetList(GuiGraphics g, int mx, int my) {
        int x0 = px + RAIL_W + 8, x1 = x0 + listW(), y0 = py + TOP + 4, y1 = listBottom();
        g.fill(x0, y0, x1, y1, PANEL);
        if (presets.isEmpty()) {
            g.drawCenteredString(font, "No saved presets", (x0 + x1) / 2, y0 + 14, DIM);
            g.drawCenteredString(font, "Build a loadout, name it, and save it", (x0 + x1) / 2, y0 + 28, DIM);
            return;
        }
        int rowH = 30;
        presetScroll = Math.max(0, Math.min(presetScroll, Math.max(0, presets.size() * rowH - (y1 - y0))));
        g.enableScissor(x0, y0, x1, y1);
        for (int i = 0; i < presets.size(); i++) {
            int ry = y0 + i * rowH - presetScroll;
            if (ry + rowH < y0 || ry > y1) continue;
            var p = presets.get(i);
            boolean over = mx >= x0 && mx < x1 - 22 && my >= Math.max(ry, y0) && my < Math.min(ry + rowH, y1);
            g.fill(x0, ry, x1, ry + rowH - 2, over ? 0xFF28333F : ROW);
            g.drawString(font, font.plainSubstrByWidth(p.name(), x1 - x0 - 30), x0 + 6, ry + 5, INK, false);
            g.drawString(font, font.plainSubstrByWidth(summary(p), x1 - x0 - 30), x0 + 6, ry + 16, DIM, false);
            g.drawString(font, "x", x1 - 14, ry + 10, over ? DIM : NONE, false);
            acts.add(new Act(x0, Math.max(ry, y0), x1 - 22, Math.min(ry + rowH - 2, y1), () -> load(p), null));
            acts.add(new Act(x1 - 22, Math.max(ry, y0), x1, Math.min(ry + rowH - 2, y1), () -> {
                var d = new JsonObject();
                d.addProperty("name", p.name());
                send("delete", d, targets);
            }, null));
        }
        g.disableScissor();
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

    private void loadout(GuiGraphics g, int mx, int my) {
        int x0 = rightX(), x1 = x0 + rightW(), y = py + TOP + 4, bottom = py + ph - BOTTOM - 4;
        g.fill(x0, y, x1, bottom, PANEL);
        g.drawString(font, "Loadout", x0 + 6, y + 4, ACCENT, false);
        y += 18;
        y = slotLine(g, mx, my, "Gun", gun, "gun", x0, x1, y, () -> gun = "");
        for (int i = 0; i < Want.SLOTS.size(); i++) {
            String s = Want.SLOTS.get(i);
            y = slotLine(g, mx, my, RAIL_LABEL[2 + i], armour.getOrDefault(s, ""), s, x0, x1, y, () -> armour.remove(s));
        }
        g.drawString(font, "Grenades", x0 + 6, y + 2, DIM, false);
        y += 11;
        Map<String, Integer> kinds = new LinkedHashMap<>();
        grenades.forEach(k -> kinds.merge(k, 1, Integer::sum));
        if (kinds.isEmpty()) {
            g.drawString(font, "unchanged", x0 + 10, y, 0xFF5E6B78, false);
            y += 11;
        }
        for (var e : kinds.entrySet()) {
            var c = choice(e.getKey());
            int have = c == null ? 0 : qty(c.key(), "grenade");
            String text = (c == null ? e.getKey() : c.label()) + " x" + e.getValue();
            g.drawString(font, font.plainSubstrByWidth(text, x1 - x0 - 50), x0 + 10, y, INK, false);
            String h = Integer.toString(have);
            g.drawString(font, h, x1 - 18 - font.width(h), y, have >= e.getValue() ? HAVE : NONE, false);
            final String key = e.getKey();
            g.drawString(font, "x", x1 - 11, y, NONE, false);
            acts.add(new Act(x1 - 14, y - 2, x1 - 2, y + 10, () -> grenades.removeIf(k -> k.equals(key)), null));
            y += 11;
        }
        y += 4;
        String roleText = role.isEmpty() ? "unchanged" : role;
        g.drawString(font, "Role", x0 + 6, y + 2, DIM, false);
        int rb = x0 + 50;
        g.fill(rb, y, x1 - 6, y + 12, 0xFF28333F);
        g.drawString(font, roleText, rb + 4, y + 2, INK, false);
        acts.add(new Act(rb, y, x1 - 6, y + 12, this::nextRole, this::prevRole));
        y += 16;
        g.drawString(font, "Mags", x0 + 6, y + 2, DIM, false);
        g.fill(rb, y, x1 - 6, y + 12, 0xFF28333F);
        g.drawString(font, spares < 0 ? "unchanged" : Integer.toString(spares), rb + 4, y + 2, INK, false);
        acts.add(new Act(rb, y, x1 - 6, y + 12, () -> spares = spares >= 12 ? -1 : spares + 1, () -> spares = spares < 0 ? 12 : spares - 1));
        y += 18;
        if (!needs.isEmpty() && y + 10 < bottom - 24) {
            g.drawString(font, "Still to collect", x0 + 6, y, DIM, false);
            y += 10;
            for (var e : needs.entrySet()) {
                if (y + 9 > bottom - 24) break;
                g.drawString(font, font.plainSubstrByWidth(e.getKey().replace('_', ' ') + ": " + String.join(", ", e.getValue()), x1 - x0 - 12), x0 + 8, y, 0xFFE0B040, false);
                y += 9;
            }
        }
    }

    private int slotLine(GuiGraphics g, int mx, int my, String label, String key, String slot, int x0, int x1, int y, Runnable clear) {
        g.drawString(font, label, x0 + 6, y + 2, DIM, false);
        var c = choice(key);
        if (c == null) {
            g.drawString(font, "unchanged", x0 + 50, y + 2, 0xFF5E6B78, false);
        } else {
            int have = qty(c.key(), slot);
            g.renderItem(c.stack(), x0 + 44, y - 2);
            String h = Integer.toString(have);
            g.drawString(font, h, x1 - 18 - font.width(h), y + 2, have > 0 ? HAVE : NONE, false);
            g.drawString(font, font.plainSubstrByWidth(c.label(), x1 - 18 - font.width(h) - (x0 + 62) - 4), x0 + 62, y + 2, INK, false);
            g.drawString(font, "x", x1 - 11, y + 2, NONE, false);
            acts.add(new Act(x1 - 14, y, x1 - 2, y + 12, clear, null));
            if (mx >= x0 + 44 && mx < x1 - 18 && my >= y - 2 && my < y + 14) g.renderTooltip(font, c.stack(), mx, my);
        }
        return y + 17;
    }

    private void nextRole() {
        var all = SoldierRole.values();
        if (role.isEmpty()) role = all[0].id;
        else {
            int i = 0;
            while (i < all.length && !all[i].id.equals(role)) i++;
            role = i + 1 >= all.length ? "" : all[i + 1].id;
        }
    }

    private void prevRole() {
        var all = SoldierRole.values();
        if (role.isEmpty()) role = all[all.length - 1].id;
        else {
            int i = 0;
            while (i < all.length && !all[i].id.equals(role)) i++;
            role = i - 1 < 0 ? "" : all[i - 1].id;
        }
    }

    private void bar(GuiGraphics g, int mx, int my) {
        int y0 = py + ph - BOTTOM, y1 = py + ph - 1;
        g.fill(px + 1, y0, px + pw - 1, y1, HEAD);
        int x = px + 8;
        x = word(g, mx, my, "CLOSE", x, y0, this::onClose) + 14;
        x = word(g, mx, my, "CLEAR", x, y0, this::clear) + 14;
        x = word(g, mx, my, "SAVE PRESET", x, y0, () -> {
            var d = new JsonObject();
            var preset = new GearBook.Preset(nameBox.getValue().isBlank() ? "Preset" : nameBox.getValue(), want(), role, spares);
            send("save", preset.json(), targets);
            message = "Saving...";
        }) + 14;
        String go = "ASSIGN TO " + targetLabel().toUpperCase(Locale.ROOT);
        int w = font.width(go);
        word(g, mx, my, go, px + pw - 8 - w, y0, () -> {
            send("assign", assignData(), targets);
            message = "Sending...";
        });
        if (!message.isEmpty()) {
            int room = px + pw - 8 - w - 20 - x;
            String m = font.plainSubstrByWidth(message, Math.max(0, room));
            g.drawString(font, m, px + pw - 8 - w - 14 - font.width(m), y0 + 8, 0xFFE0B040, false);
        }
    }

    private int word(GuiGraphics g, int mx, int my, String text, int x, int y0, Runnable run) {
        int w = font.width(text);
        boolean over = mx >= x - 3 && mx < x + w + 3 && my >= y0 && my < y0 + BOTTOM;
        g.drawString(font, text, x, y0 + 8, over ? ACCENT : INK, false);
        acts.add(new Act(x - 3, y0, x + w + 3, y0 + BOTTOM, run, null));
        return x + w;
    }

    // ---- input

    @Override
    public boolean mouseClicked(double x, double y, int button) {
        if (super.mouseClicked(x, y, button)) return true;
        for (int i = acts.size() - 1; i >= 0; i--) {
            Act a = acts.get(i);
            if (!a.has(x, y)) continue;
            Runnable r = button == 1 && a.right() != null ? a.right() : button == 0 ? a.left() : null;
            if (r == null) return false;
            r.run();
            return true;
        }
        return false;
    }

    @Override
    public boolean mouseScrolled(double x, double y, double dx, double dy) {
        if (RAIL[rail].equals("presets")) presetScroll -= (int) (dy * 18);
        else scroll -= (int) (dy * 20);
        return true;
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
