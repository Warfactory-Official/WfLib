package com.wf.wflib.drone.client;

import com.wf.gemrender.ui.Canvas;
import com.wf.wflib.cam.api.client.CameraFeedCache;
import com.wf.wflib.cam.api.client.CameraLink;
import com.wf.wflib.drone.api.DroneTask;
import com.wf.wflib.drone.DroneActionPacket;
import com.wf.wflib.drone.DroneDispatchPacket;
import com.wf.wflib.drone.DroneProgramPacket;
import com.wf.wflib.drone.api.DroneProgram;
import com.wf.wflib.drone.api.DroneStatus;
import com.wf.wflib.drone.DroneStatusRequestPacket;
import com.wf.wflib.drone.api.PdaDrones;
import com.wf.wflib.drone.api.RemoteBody;
import com.wf.wflib.map.api.Marker;
import com.wf.wflib.ui.api.Binder;
import com.wf.wflib.ui.api.Signals;
import com.wf.wflib.ui.api.Subject;
import com.wf.wflib.ui.api.UiEval;
import com.wf.wflib.ui.api.Widget;
import com.wf.wflib.ui.api.WidgetDef;
import com.mojang.blaze3d.platform.InputConstants;
import com.wf.wflib.ui.api.ColorProp;
import com.wf.wflib.ui.api.UiSkin;
import com.wf.wflib.ui.api.input.Interactive;
import com.wf.wflib.ui.api.input.TextLine;
import com.wf.wflib.ui.api.input.UiKeyboard;
import com.wf.wflib.ui.api.theme.Chamfer;
import com.wf.wflib.ui.api.input.UiDrag;
import com.wf.wflib.ui.api.widget.Button;
import com.wf.wflib.ui.api.widget.Feed;
import com.wf.wflib.ui.api.widget.Point;
import com.wf.wflib.ui.api.widget.Sized;
import com.wf.wflib.ui.api.widget.TextField;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import org.lwjgl.glfw.GLFW;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.network.PacketDistributor;

import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import com.wf.wflib.map.api.client.MarkerClient;
import com.wf.wflib.map.api.client.WFMaps;
import com.wf.wflib.pda.api.client.PdaApp;
import com.wf.wflib.pda.api.client.PdaApps;
import com.wf.wflib.pda.api.client.PdaClient;
import com.wf.wflib.pda.api.client.PdaShell;
import com.wf.wflib.pda.api.client.PdaText;
import com.wf.wflib.pda.api.client.PdaLog;
import com.wf.wflib.pda.api.client.PdaTheme;
import com.wf.wflib.core.api.WFLib;

import static com.wf.wflib.ui.api.Ui.at;
import static com.wf.wflib.ui.api.Ui.drag;
import static com.wf.wflib.ui.api.Ui.list;
import static com.wf.wflib.ui.api.Ui.place;
import static com.wf.wflib.ui.api.Ui.rect;
import static com.wf.wflib.ui.api.Ui.stack;
import static com.wf.wflib.ui.api.Ui.text;
import static com.wf.wflib.ui.api.Ui.when;

/**
 * PDA Drones app, every linked body (wflib drone, other mods' UAVs): left list + camera pad (pan / zoom without
 * CONNECT); right FEED (feed, link, fuel, systems, task, ammo; CONNECT / REMOVE / FULL) or DISPATCH (mini feed;
 * selected autonomous body, else nearest pad: mission buttons, waypoint queue, HOLD / RTB / LAND / ALT for a linked
 * body, typed orders per {@link DroneCommand}). Routes and the queue on every map ({@link DroneMapLayer}).
 * Server state polled while open, slower while a PDA is carried (map routes).
 */
public final class DronesApp implements PdaApp {
    private static final ResourceLocation ID = ResourceLocation.fromNamespaceAndPath(WFLib.MODID, "drones");
    private static final int REFRESH_TICKS = 10;
    /** PDA carried, app closed: keeps map routes current. */
    private static final int CARRIED_REFRESH_TICKS = 40;
    /** Camera pad, per client tick while held: degrees at zoom 1, zoom steps. */
    private static final double CAM_STEP = 2.5;
    private static final double ZOOM_STEP = 0.25;
    /** Pad keys: left, up, down, right, zoom out, zoom in. */
    private static final String[] PAD = {"<", "^", "v", ">", "Z-", "Z+"};
    private static final int[] PAD_KEYS = {GLFW.GLFW_KEY_LEFT, GLFW.GLFW_KEY_UP, GLFW.GLFW_KEY_DOWN, GLFW.GLFW_KEY_RIGHT,
            GLFW.GLFW_KEY_PAGE_DOWN, GLFW.GLFW_KEY_PAGE_UP};
    /** Pad key held by the pointer; -1 = none. */
    private static int padHeld = -1;
    /** Drag units -> degrees at zoom 1. */
    private static final double DRAG_GAIN = 0.35;

    private static final DronesApp INSTANCE = new DronesApp();
    static final Subject<DronesApp> APP = Subject.register("pda_drones", DronesApp.class, () -> INSTANCE);
    static final Subject<Row> ROW = Subject.register("pda_drone", Row.class);
    static final Subject<Row> SELECTED = Subject.register("pda_dronesel", Row.class, DronesApp::current);

    private static final List<Row> ROWS = new ArrayList<>();
    private static final List<PdaLog.Line> REPLIES = new ArrayList<>();
    private static final int REPLY_LINES = 40;
    private static final DroneTask.Kind[] KINDS = {DroneTask.Kind.MOVE_TO, DroneTask.Kind.LOITER,
            DroneTask.Kind.STRIKE, DroneTask.Kind.DELIVER, DroneTask.Kind.MINELAY};
    private static final boolean[] OFFERED = new boolean[KINDS.length];
    private static DroneStatus.Pad pad;
    private static String route = "";
    private static boolean dispatchTab;
    private static int kind;
    /** Crosshair reach for LOOK, blocks. */
    private static final double LOOK_RANGE = 512;
    private static final double ALT_STEP = 10;
    /** 0 = own position, 1 = crosshair, n = (n-1)-th marker in this dimension. */
    private static int target;
    /** Typed {@code target}; beats {@link #target} until the arrows move. */
    @Nullable
    private static Vec3 custom;
    private static final TextLine INPUT = new TextLine(160, DronesApp::submit);
    /** Unsent waypoint queue; SEND flies it as one program. */
    private static final List<DroneTask> QUEUE = new ArrayList<>();
    private static String targetLabel = "HERE";
    private static int selected;
    private static boolean open;
    private static int ticks;

    /** Strings resolved once per server update. */
    public record Row(DroneStatus.Entry entry, String name, String detail, String info, String ammo) {
        static Row of(DroneStatus.Entry e) {
            String name = e.name().toUpperCase(Locale.ROOT);
            if (!e.loaded()) {
               return new Row(e, name, e.status(), "", "");
            }
            String info = String.format(Locale.ROOT, "LINK %d%%   %s %d%%   %s", Math.round(e.link() * 100),
                    e.electric() ? "CHARGE" : "FUEL", Math.round(e.fuel() * 100), distance(e.distance()));
           return new Row(e, name, e.status() + "  " + distance(e.distance()), info, String.join("   ", e.ammo()));
        }

        boolean live() {
            return this.entry.feedId() != 0;
        }

        boolean autonomous() {
            return !this.entry.offers().isEmpty();
        }

        boolean connectable() {
            return this.entry.refusal().isEmpty() || this.entry.status().equals("ASLEEP");
        }
    }

    private DronesApp() {
    }

    public static void register() {
        Signals.num(APP, "count", a -> ROWS.size());
        Signals.items(APP, "rows", a -> ROWS, ROW);
        Signals.num(APP, "feed", a -> feed());
        Signals.bool(APP, "picture", a -> feed() != 0 && CameraFeedCache.drawable(feed()));
        Signals.text(ROW, "name", PdaShell.TEXT_MAX, Row::name);
        Signals.text(ROW, "detail", PdaShell.TEXT_MAX, Row::detail);
        Signals.bool(ROW, "selected", r -> r == current());
        Signals.bool(ROW, "live", Row::live);
        Signals.text(SELECTED, "name", PdaShell.TEXT_MAX, Row::name);
        Signals.text(SELECTED, "status", PdaShell.TEXT_MAX, r -> r.entry().status());
        Signals.text(SELECTED, "info", PdaShell.TEXT_MAX, Row::info);
        Signals.text(SELECTED, "task", PdaShell.TEXT_MAX, r -> r.entry().task().isEmpty() ? "" : "TASK " + r.entry().task());
        Signals.text(SELECTED, "ammo", PdaShell.TEXT_MAX, Row::ammo);
        Signals.text(SELECTED, "refusal", PdaShell.TEXT_MAX, r -> r.entry().refusal().toUpperCase(Locale.ROOT));
        Signals.bool(SELECTED, "live", Row::live);
        Signals.bool(SELECTED, "low", r -> r.entry().loaded() && r.entry().fuel() < 0.2f);
        Signals.bool(SELECTED, "connectable", Row::connectable);
        Signals.bool(APP, "dispatch", a -> dispatchTab);
        Signals.bool(APP, "routed", a -> !route.isEmpty());
        Signals.text(APP, "route", PdaShell.TEXT_MAX, a -> route);
        Signals.text(APP, "target", PdaShell.TEXT_MAX, a -> targetLabel);
        Signals.bool(APP, "ready", a -> !route.isEmpty() && (OFFERED[kind] || !QUEUE.isEmpty()));
        Signals.bool(APP, "queued", a -> !QUEUE.isEmpty());
        Signals.text(APP, "send", PdaShell.TEXT_MAX, a -> QUEUE.isEmpty() ? "SEND" : "SEND " + QUEUE.size());
        Signals.text(APP, "queue", PdaShell.TEXT_MAX, a -> queueLine());
        Signals.bool(APP, "direct", a -> current() != null && current().autonomous());
        for (int i = 0; i < KINDS.length; i++) {
            int k = i;
            Signals.bool(APP, "offer_" + KINDS[i].id(), a -> OFFERED[k]);
            Signals.bool(APP, "kind_" + KINDS[i].id(), a -> kind == k);
        }
        NeoForge.EVENT_BUS.addListener(DronesApp::onTick);
        PdaApps.register(INSTANCE, 960);
    }

    public static void accept(DroneStatus status) {
        ROWS.clear();
        for (DroneStatus.Entry e : status.drones()) {
            ROWS.add(Row.of(e));
        }
        selected = ROWS.isEmpty() ? 0 : Math.clamp(selected, 0, ROWS.size() - 1);
        pad = status.pad().orElse(null);
        offers();
        target(0);
        link();
        map();
    }

    private static void map() {
        if (Minecraft.getInstance().player != null) {
            Row row = current();
            Vec3 from = row != null && row.autonomous() && !row.entry().route().isEmpty()
                    ? row.entry().route().getFirst() : Minecraft.getInstance().player.position();
            DroneMapLayer.publish(ROWS, row, QUEUE, from);
        }
    }

    /** Selected autonomous body takes the order, else the nearest pad. */
    private static void offers() {
        Arrays.fill(OFFERED, false);
        Row row = current();
        List<String> offered;
        if (row != null && row.autonomous()) {
            offered = row.entry().offers();
            route = row.name() + " (DIRECT)";
        } else if (pad != null) {
            offered = pad.offers();
            route = String.format(Locale.ROOT, "PAD %d %d %d   %d m", pad.pos().getX(), pad.pos().getY(),
                    pad.pos().getZ(), pad.distance());
        } else {
            route = "";
            return;
        }
        for (int i = 0; i < KINDS.length; i++) {
            OFFERED[i] = offered.contains(KINDS[i].id());
        }
    }

    public static void reply(String line) {
        REPLIES.add(new PdaLog.Line(line, line.startsWith("err") ? 0xFFD0584A : 0xFFA9D46C));
        if (REPLIES.size() > REPLY_LINES) {
            REPLIES.removeFirst();
        }
    }

    private static String distance(int metres) {
        return metres >= 1000 ? String.format(Locale.ROOT, "%.1f km", metres / 1000.0)
                : String.format(Locale.ROOT, "%d m", metres);
    }

    /** Side markers in this dimension. */
    private static List<Marker> markers() {
        Minecraft mc = Minecraft.getInstance();
        List<Marker> out = new ArrayList<>();
        if (mc.level != null) {
            for (Marker m : MarkerClient.markers()) {
                if (m.dimension().equals(mc.level.dimension().location())) {
                    out.add(m);
                }
            }
        }
        return out;
    }

    private static void target(int step) {
        List<Marker> markers = markers();
        if (step != 0) {
            custom = null;
        }
        target = Math.floorMod(target + step, markers.size() + 2);
        if (custom != null) {
            targetLabel = String.format(Locale.ROOT, "COORDS  %d %d %d", (int) Math.floor(custom.x),
                    (int) Math.floor(custom.y), (int) Math.floor(custom.z));
        } else if (target == 0) {
            targetLabel = "HERE (OWN POSITION)";
        } else if (target == 1) {
            targetLabel = "LOOK (CROSSHAIR)";
        } else {
            Marker m = markers.get(target - 2);
            targetLabel = String.format(Locale.ROOT, "%s  %d %d", m.label().toUpperCase(Locale.ROOT),
                    m.pos().getX(), m.pos().getZ());
        }
    }

    private static Vec3 centre(Marker m) {
        return new Vec3(m.pos().getX() + 0.5, m.pos().getY(), m.pos().getZ() + 0.5);
    }

    /** Typed names, against the local player; only used while one exists. */
    private static final DroneCommand.Places PLACES = new DroneCommand.Places() {
        @Override
        public Vec3 self() {
            return Minecraft.getInstance().player.position();
        }

        @Nullable
        @Override
        public Vec3 look() {
            HitResult hit = Minecraft.getInstance().player.pick(LOOK_RANGE, 1.0f, false);
            return hit.getType() == HitResult.Type.MISS ? null : hit.getLocation();
        }

        @Nullable
        @Override
        public Vec3 marker(String name) {
            List<Marker> markers = markers();
            if (name.matches("#\\d{1,3}")) {
                int i = Integer.parseInt(name.substring(1)) - 1;
                return i < markers.size() ? centre(markers.get(i)) : null;
            }
            for (Marker m : markers) {
                if (m.label().equalsIgnoreCase(name)) {
                    return centre(m);
                }
            }
            return null;
        }

        @Override
        public Vec3 picked() {
            if (custom != null) {
                return custom;
            }
            List<Marker> markers = markers();
            if (target == 1) {
                Vec3 look = look();
                if (look == null) {
                    throw new IllegalArgumentException("nothing under the crosshair");
                }
                return look;
            }
            return target >= 2 && target - 2 < markers.size() ? centre(markers.get(target - 2)) : self();
        }
    };

    /** SEND: the queue if any, else the picked mission at the picked target. */
    private static void send() {
        if (!QUEUE.isEmpty()) {
            sendQueue();
            return;
        }
        if (Minecraft.getInstance().player == null || !OFFERED[kind]) {
            return;
        }
        try {
            Vec3 at = PLACES.picked();
            reply(String.format(Locale.ROOT, "> %s at %d %d", KINDS[kind].id(), (int) Math.floor(at.x),
                    (int) Math.floor(at.z)));
            program(List.of(KINDS[kind].at(at)));
        } catch (IllegalArgumentException e) {
            reply("err: " + e.getMessage());
        }
    }

    /** +WP: picked mission at the picked target onto the queue. */
    private static void enqueue() {
        if (Minecraft.getInstance().player == null) {
            return;
        }
        try {
            enqueue(KINDS[kind].at(PLACES.picked()));
        } catch (IllegalArgumentException e) {
            reply("err: " + e.getMessage());
        }
    }

    private static void enqueue(DroneTask task) {
        if (QUEUE.size() >= DroneTask.MAX_STEPS) {
            reply("err: queue full (" + DroneTask.MAX_STEPS + ")");
            return;
        }
        QUEUE.add(task);
        reply("queued " + QUEUE.size() + ": " + step(task));
        map();
    }

    static void sendQueue() {
        if (QUEUE.isEmpty()) {
            reply("err: queue empty");
            return;
        }
        reply("> send " + QUEUE.size() + " steps");
        if (program(List.copyOf(QUEUE))) {
            QUEUE.clear();
            map();
        }
    }

    static void clearQueue() {
        QUEUE.clear();
        map();
    }

    static int queued() {
        return QUEUE.size();
    }

    /** "MOVETO 100 200", "HOLD 300 400", "RTB". */
    private static String step(DroneTask task) {
        Vec3 at = task.at();
        String what = task instanceof DroneTask.Exfil ? "RTB" : task instanceof DroneTask.Loiter l && l.stationary()
                ? "HOLD" : task.kind().id().toUpperCase(Locale.ROOT);
        return at == null ? what : String.format(Locale.ROOT, "%s %d %d", what, (int) Math.floor(at.x),
                (int) Math.floor(at.z));
    }

    private static String queueLine() {
        if (QUEUE.isEmpty()) {
            return "QUEUE EMPTY  (+WP ADDS MISSION AT TARGET)";
        }
        List<String> steps = new ArrayList<>(QUEUE.size());
        for (int i = 0; i < QUEUE.size(); i++) {
            steps.add((i + 1) + " " + step(QUEUE.get(i)));
        }
        return String.join("  >  ", steps);
    }

    /** Control row: a linked body's own position stands in for the unused target. */
    private static void control(String verb, double arg, String echo) {
        if (Minecraft.getInstance().player != null) {
            reply("> " + echo);
            order(verb, PLACES.self(), arg);
        }
    }

    private static Optional<UUID> recipientId() {
        Row row = current();
        return row != null && row.autonomous() ? Optional.of(row.entry().id()) : Optional.empty();
    }

    /** Selected autonomous body or pad in reach. */
    static boolean orderable() {
        return !route.isEmpty();
    }

    static String recipient() {
        return route.isEmpty() ? "none" : route;
    }

    private static void order(String verb, Vec3 at, double arg) {
        if (route.isEmpty()) {
            reply("err: no autonomous drone or pad");
            return;
        }
        PacketDistributor.sendToServer(new DroneDispatchPacket(verb, at.x, at.y, at.z, arg, recipientId()));
    }

    /** @return false = nothing to send it to */
    private static boolean program(List<DroneTask> tasks) {
        if (route.isEmpty()) {
            reply("err: no autonomous drone or pad");
            return false;
        }
        PacketDistributor.sendToServer(new DroneProgramPacket(DroneProgram.of(tasks), recipientId()));
        return true;
    }

    /** Map right-click: {@code verb} at {@code at}, now or onto the queue. */
    static void mapOrder(String verb, Vec3 at, boolean queue) {
        DroneTask task = DroneCommand.task(new DroneCommand.Order(verb, at, 0));
        if (queue) {
            enqueue(task);
        } else {
            reply("> " + step(task) + " (map)");
            program(List.of(task));
        }
    }

    static void mapTarget(Vec3 at) {
        custom = at;
        target(0);
    }

    /** Command line under the log. */
    private static void submit(String line) {
        String cmd = line.trim();
        if (cmd.isEmpty() || Minecraft.getInstance().player == null) {
            return;
        }
        reply("> " + cmd);
        String[] w = cmd.toLowerCase(Locale.ROOT).split("\\s+");
        switch (w[0]) {
            case "help", "?" -> DroneCommand.HELP.forEach(DronesApp::reply);
            case "clear" -> REPLIES.clear();
            case "send" -> sendQueue();
            case "cancel" -> {
                clearQueue();
                reply("queue cleared");
            }
            case "undo" -> {
                if (QUEUE.isEmpty()) {
                    reply("err: queue empty");
                } else {
                    reply("removed " + step(QUEUE.removeLast()));
                    map();
                }
            }
            case "queue" -> reply(queueLine());
            case "connect" -> {
                Row row = current();
                if (row == null || !row.connectable()) {
                    reply("err: " + (row == null ? "no drone selected" : row.entry().refusal()));
                } else {
                    act(PdaDrones.Action.CONNECT);
                    PdaClient.lower();
                }
            }
            case "select" -> {
                Row row = w.length == 2 ? rowNamed(w[1]) : null;
                if (row == null) {
                    reply("err: select <#n|name>");
                } else {
                    select(row);
                    reply(row.name() + " selected");
                }
            }
            default -> {
                try {
                    boolean add = w[0].equals("add");
                    List<DroneCommand.Order> orders = DroneCommand.parseAll(add ? cmd.substring(3) : cmd, PLACES);
                    DroneCommand.Order first = orders.getFirst();
                    if (orders.size() == 1 && first.verb().equals("target")) {
                        custom = first.at();
                        target(0);
                    } else if (!add && orders.size() == 1 && PdaDrones.CONTROLS.contains(first.verb())) {
                        order(first.verb(), first.at(), first.arg());
                    } else {
                        List<DroneTask> tasks = new ArrayList<>(orders.size());
                        for (DroneCommand.Order o : orders) {
                            tasks.add(DroneCommand.task(o));
                        }
                        if (add) {
                            tasks.forEach(DronesApp::enqueue);
                        } else {
                            program(tasks);
                        }
                    }
                } catch (IllegalArgumentException e) {
                    reply("err: " + e.getMessage());
                }
            }
        }
    }

    /** {@code #n} / {@code n} = list position, else callsign. */
    @Nullable
    private static Row rowNamed(String ref) {
        String digits = ref.startsWith("#") ? ref.substring(1) : ref;
        if (digits.matches("\\d{1,3}")) {
            int i = Integer.parseInt(digits) - 1;
            return i >= 0 && i < ROWS.size() ? ROWS.get(i) : null;
        }
        for (Row row : ROWS) {
            if (row.name().equalsIgnoreCase(ref)) {
                return row;
            }
        }
        return null;
    }

    /** Linked bodies, last server status. */
    public static List<Row> rows() {
        return ROWS;
    }

    private static Row current() {
        return selected < ROWS.size() ? ROWS.get(selected) : null;
    }

    /** Selected body's feed while it holds {@link CameraLink}; else 0. */
    private static int feed() {
        Row row = current();
        return row != null && row.live() && CameraLink.active() && CameraLink.feedId() == row.entry().feedId()
                ? row.entry().feedId() : 0;
    }

    /** Open app => {@link CameraLink} follows the selected live body. */
    private static void link() {
        if (!open) {
            return;
        }
        Row row = current();
        int wanted = row == null ? 0 : row.entry().feedId();
        if (wanted != 0 && !(CameraLink.active() && CameraLink.feedId() == wanted)) {
            CameraLink.open(wanted);
        } else if (wanted == 0 && CameraLink.active() && owned()) {
            CameraLink.close();
        }
    }

    private static boolean owned() {
        for (Row row : ROWS) {
            if (row.live() && row.entry().feedId() == CameraLink.feedId()) {
                return true;
            }
        }
        return false;
    }

    private static void select(Object item) {
        int index = ROWS.indexOf(item);
        if (index < 0 || index == selected) {
            return;
        }
        selected = index;
        PacketDistributor.sendToServer(new DroneActionPacket(PdaDrones.Action.SELECT, index));
        offers();
        link();
    }

    @Nullable
    private static Row rowOf(int feedId) {
        for (Row row : ROWS) {
            if (row.live() && row.entry().feedId() == feedId) {
                return row;
            }
        }
        return null;
    }

    /** Linked body's name; null = no Drones row shows {@code feedId}. */
    @Nullable
    public static String name(int feedId) {
        Row row = rowOf(feedId);
        return row == null ? null : row.name();
    }

    public static boolean controllable(int feedId) {
        Row row = rowOf(feedId);
        return row != null && row.connectable();
    }

    /** CONNECT the body behind {@code feedId}; PDA lowered. */
    public static void control(int feedId) {
        Row row = rowOf(feedId);
        if (row != null && row.connectable()) {
            select(row);
            PacketDistributor.sendToServer(new DroneActionPacket(PdaDrones.Action.CONNECT, ROWS.indexOf(row)));
            PdaClient.lower();
        }
    }

    private static void act(PdaDrones.Action action) {
        if (current() != null) {
            PacketDistributor.sendToServer(new DroneActionPacket(action, selected));
        }
    }

    private static void onTick(ClientTickEvent.Post event) {
        ticks++;
        padTick();
        boolean carried = PdaClient.carried() && Minecraft.getInstance().getConnection() != null;
        if (open ? ticks % REFRESH_TICKS == 0 : carried && ticks % CARRIED_REFRESH_TICKS == 0) {
            PacketDistributor.sendToServer(DroneStatusRequestPacket.INSTANCE);
        } else if (!carried && !ROWS.isEmpty() && !open) {
            ROWS.clear();
            WFMaps.clear(DroneMapLayer.LAYER);
        }
    }

    /**
     * Camera pad: pans / zooms the linked feed (no CONNECT needed) while a pad key is held on screen, or an arrow /
     * Page key on the keyboard with the PDA in hand and no text field focused.
     */
    private static void padTick() {
        if (!CameraLink.active()) {
            padHeld = -1;
            return;
        }
        if (padHeld >= 0) {
            pad(padHeld);
        }
        if (PdaClient.held() && UiKeyboard.focused() == null && Minecraft.getInstance().screen == null) {
            long window = Minecraft.getInstance().getWindow().getWindow();
            for (int i = 0; i < PAD_KEYS.length; i++) {
                if (i != padHeld && InputConstants.isKeyDown(window, PAD_KEYS[i])) {
                    pad(i);
                }
            }
        }
    }

    private static void pad(int key) {
        double step = CAM_STEP / Math.max(1.0, CameraLink.zoom());
        switch (key) {
            case 0 -> CameraLink.pan(-step, 0);
            case 1 -> CameraLink.pan(0, -step);
            case 2 -> CameraLink.pan(0, step);
            case 3 -> CameraLink.pan(step, 0);
            case 4 -> CameraLink.zoomBy(-ZOOM_STEP);
            default -> CameraLink.zoomBy(ZOOM_STEP);
        }
    }

    @Override
    public ResourceLocation id() {
        return ID;
    }

    @Override
    public Component title() {
        return Component.translatable("wflib.pda.app.drones");
    }

    @Override
    public boolean visible(LocalPlayer player) {
        return PdaClient.held();
    }

    @Override
    public void opened() {
        open = true;
        ticks = 0;
        PacketDistributor.sendToServer(DroneStatusRequestPacket.INSTANCE);
        link();
    }

    @Override
    public void closed() {
        open = false;
        if (feed() != 0) {
            CameraLink.close();
        }
    }

    @Override
    public WidgetDef build() {
        float listW = 128;
        float h = PdaShell.APP_HEIGHT - 16;
        float rowW = listW - 8;
        WidgetDef row = new Button(DronesApp::select, "true", "pda_drone.selected ? " + PdaShell.PRESS + " : 0",
                PdaShell.HOVER, PdaShell.PRESS, "0", 0, 0, 0, 0, rowW, 24, Point.TOP_LEFT,
                new Sized(Float.toString(rowW), "24", Point.TOP_LEFT, false, stack(
                        at("5", "3", PdaShell.fit("{pda_drone.name}", "pda_drone.live ? " + PdaShell.ACCENT + " : "
                                + PdaShell.TEXT, rowW - 10)),
                        at("5", "13", PdaShell.fit("{pda_drone.detail}", PdaShell.DIM, rowW - 10)))));
        float viewX = listW + 16;
        float viewW = PdaShell.APP_WIDTH - viewX - 8;
        float feedW = viewW - 8;
        float feedH = Math.min((float) Math.floor(feedW * 9 / 16), h - 104);
        UiDrag pan = new UiDrag() {
            @Override
            public void drag(Object item, float dx, float dy) {
                if (feed() != 0) {
                    double gain = DRAG_GAIN / Math.max(1.0, CameraLink.zoom());
                    CameraLink.pan(dx * gain, dy * gain);
                }
            }

            @Override
            public boolean wheel(Object item, float amount) {
                if (feed() == 0) {
                    return false;
                }
                CameraLink.zoomBy(amount);
                return true;
            }
        };
        float infoY = 24 + feedH + 4;
        float buttonsY = h - 14;
        String vx = Float.toString(viewX + 4);
        float right = viewX + viewW - 4;
        WidgetDef feedView = stack(
                when("pda_dronesel.present", place(Float.toString(right - 112), "13", Point.TOP_RIGHT,
                        PdaShell.fit("{pda_dronesel.status}", "pda_dronesel.live ? " + PdaShell.ACCENT + " : "
                                + PdaShell.DIM, 34))),
                at(vx, "24", feed(feedW, feedH, pan)),
                when("pda_dronesel.present", stack(
                        at(vx, Float.toString(infoY), PdaShell.fit("{pda_dronesel.info}",
                                "pda_dronesel.low ? " + PdaShell.ALERT + " : " + PdaShell.TEXT, viewW - 8)),
                        at(vx, Float.toString(infoY + 11), new Cells(feedW)),
                        at(vx, Float.toString(infoY + 24), PdaShell.fit("{pda_dronesel.task}", PdaShell.DIM, viewW - 8)),
                        at(vx, Float.toString(infoY + 35), PdaShell.fit("{pda_dronesel.ammo}", PdaShell.DIM, viewW - 8)),
                        when("!pda_dronesel.connectable", place(Float.toString(right), Float.toString(infoY + 46),
                                Point.TOP_RIGHT, PdaShell.fit("{pda_dronesel.refusal}", PdaShell.DIM, viewW - 8))))),
                at(vx, Float.toString(buttonsY), button("CONNECT", "pda_dronesel.present && pda_dronesel.connectable",
                        () -> {
                            act(PdaDrones.Action.CONNECT);
                            PdaClient.lower();
                        })),
                at(Float.toString(viewX + 80), Float.toString(buttonsY), button("REMOVE", "pda_dronesel.present",
                        () -> act(PdaDrones.Action.REMOVE))),
                place(Float.toString(right), Float.toString(buttonsY), Point.TOP_RIGHT, button("FULL",
                        "pda_drones.feed != 0", () -> PdaClient.fullscreen(feed()))));
        WidgetDef[] kinds = new WidgetDef[KINDS.length];
        for (int i = 0; i < KINDS.length; i++) {
            int k = i;
            String id = KINDS[i].id();
            kinds[i] = at(col(viewX, i), "50", chip(id.toUpperCase(Locale.ROOT), "pda_drones.offer_" + id,
                    "pda_drones.kind_" + id + " ? " + PdaShell.PRESS + " : " + PdaShell.PANEL, () -> kind = k));
        }
        String direct = "pda_drones.direct";
        String queued = "pda_drones.queued";
        float logY = 129;
        float fieldY = h - 10;
        WidgetDef dispatchView = stack(
                at(vx, "26", when("pda_drones.routed", PdaShell.fit("{pda_drones.route}", PdaShell.TEXT, viewW - 8),
                        PdaShell.fit("NO AUTONOMOUS DRONE OR PAD", PdaShell.ALERT, viewW - 8))),
                at(vx, "38", text("MISSION", PdaShell.DIM)),
                place(Float.toString(right), "36", Point.TOP_RIGHT, new Button(item -> send(), "pda_drones.ready",
                        PdaShell.PANEL, PdaShell.HOVER, PdaShell.PRESS, PdaShell.EDGE, 1, 0, 8, 2, 0, 0, Point.CENTER,
                        text("{pda_drones.send}", "pda_drones.ready ? " + PdaShell.ACCENT + " : " + PdaShell.DIM))),
                stack(kinds),
                at(vx, "64", text("TARGET", PdaShell.DIM)),
                at(vx, "74", small("<", () -> target(-1))),
                at(Float.toString(viewX + 18), "76", PdaShell.fit("{pda_drones.target}", PdaShell.TEXT, viewW - 48)),
                place(Float.toString(right), "74", Point.TOP_RIGHT, small(">", () -> target(1))),
                at(col(viewX, 0), "89", chip("HOLD", direct, PdaShell.PANEL, () -> control("stop", 0, "hold"))),
                at(col(viewX, 1), "89", chip("RTB", direct, PdaShell.PANEL, () -> control("rtb", 0, "rtb"))),
                at(col(viewX, 2), "89", chip("LAND", direct, PdaShell.PANEL, () -> control("land", 0, "land"))),
                at(col(viewX, 3), "89", chip("ALT -" + (int) ALT_STEP, direct, PdaShell.PANEL,
                        () -> control("climb", -ALT_STEP, "alt -" + (int) ALT_STEP))),
                at(col(viewX, 4), "89", chip("ALT +" + (int) ALT_STEP, direct, PdaShell.PANEL,
                        () -> control("climb", ALT_STEP, "alt +" + (int) ALT_STEP))),
                at(col(viewX, 0), "103", chip("+WP", "pda_drones.routed", PdaShell.PANEL, DronesApp::enqueue)),
                at(col(viewX, 1), "103", chip("UNDO", queued, PdaShell.PANEL, () -> submit("undo"))),
                at(col(viewX, 2), "103", chip("CLEAR Q", queued, PdaShell.PANEL, () -> submit("cancel"))),
                at(col(viewX, 3), "103", chip("MAP", "true", PdaShell.PANEL, () -> {
                    if (!WFMaps.openFullscreen()) {
                        reply("err: no fullscreen map installed");
                    }
                })),
                at(vx, "117", PdaShell.fit("{pda_drones.queue}", queued + " ? " + PdaShell.ACCENT + " : " + PdaShell.DIM,
                        viewW - 8)),
                at(vx, Float.toString(logY), new PdaLog(REPLIES, viewW - 8, fieldY - logY - 2)),
                at(vx, Float.toString(fieldY), new TextField(INPUT, "> ", viewW - 8, 13, 0xFF101210, 0xFF787F76,
                        PdaTheme.accent(), PdaTheme.text(), PdaTheme.accent())));
        float miniW = listW - 8;
        float miniH = (float) Math.floor(miniW * 9 / 16);
        float camY = h - 30;
        WidgetDef[] cam = new WidgetDef[PAD.length];
        for (int i = 0; i < cam.length; i++) {
            cam[i] = at(Float.toString(12 + i * 20), Float.toString(camY), new Hold(i));
        }
        return stack(
                at("8", "8", PdaShell.panel(listW, h)),
                at("14", "13", text("DRONES  {pda_drones.count}", PdaShell.DIM)),
                when("pda_drones.dispatch",
                        stack(at("12", "26", list("pda_drones.rows", 24, 1, Float.toString(rowW), "72", PdaShell.EDGE,
                                        row)),
                                at("12", "102", feed(miniW, miniH, pan))),
                        at("12", "26", list("pda_drones.rows", 24, 1, Float.toString(rowW),
                                Float.toString(camY - 30), PdaShell.EDGE, row))),
                when("pda_drones.count == 0", stack(at("14", "34", PdaShell.fit("NO DRONES LINKED", PdaShell.DIM, listW - 12)),
                        at("14", "46", PdaShell.wrap("SNEAK+USE PDA ON ONE\nOR DISPATCH FROM A PAD", PdaShell.DIM, listW - 12, 6)))),
                stack(cam),
                at("12", Float.toString(camY + 15), button("MODE", "pda_drones.feed != 0", CameraLink::cycleMode)),
                place(Float.toString(listW + 4), Float.toString(camY + 15), Point.TOP_RIGHT, button("CONNECT",
                        "pda_dronesel.present && pda_dronesel.connectable", () -> {
                            act(PdaDrones.Action.CONNECT);
                            PdaClient.lower();
                        })),
                at(Float.toString(viewX), "8", PdaShell.panel(viewW, h)),
                when("pda_dronesel.present",
                        at(Float.toString(viewX + 6), "13", PdaShell.fit("{pda_dronesel.name}", PdaShell.TEXT, 72))),
                tab("FEED", "!pda_drones.dispatch", () -> dispatchTab = false, right - 74, 10),
                tab("DISPATCH", "pda_drones.dispatch", () -> dispatchTab = true, right, 10),
                when("pda_drones.dispatch", dispatchView, feedView));
    }

    /** Selected body's feed, {@code w} x {@code h}: drag pans, wheel zooms; status text while no picture. */
    private static WidgetDef feed(float w, float h, UiDrag pan) {
        String fw = Float.toString(w);
        String fh = Float.toString(h);
        return drag(fw, fh, pan, stack(
                rect(fw, fh, "#ff101210"),
                new Feed("pda_drones.feed", fw, fh),
                when("!pda_drones.picture && pda_dronesel.present", place(Float.toString(w / 2),
                        Float.toString(h / 2 - 4), Point.TOP, PdaShell.fit("{pda_dronesel.status}", PdaShell.DIM,
                                w - 12)))));
    }

    /** Chip column {@code i} of the right view. */
    private static String col(float viewX, int i) {
        return Float.toString(viewX + 4 + i * 45);
    }

    private static WidgetDef button(String label, String enabled, Runnable action) {
        return new Button(item -> action.run(), enabled, PdaShell.PANEL, PdaShell.HOVER, PdaShell.PRESS, PdaShell.EDGE,
                1, 0, 8, 2, 0, 0, Point.CENTER, text(label, PdaShell.TEXT));
    }

    /** Mission / control chip, 42 wide. */
    private static WidgetDef chip(String label, String enabled, String fill, Runnable action) {
        return new Button(item -> action.run(), enabled, fill, PdaShell.HOVER, PdaShell.PRESS, PdaShell.EDGE, 1, 0, 3, 2,
                42, 0, Point.CENTER, new com.wf.wflib.ui.api.widget.Text(label, enabled + " ? " + PdaShell.TEXT + " : "
                        + PdaShell.DIM, false, "0.8", "36"));
    }

    private static WidgetDef tab(String label, String active, Runnable action, float right, float y) {
        return place(Float.toString(right), Float.toString(y), Point.TOP_RIGHT, new Button(item -> { com.wf.wflib.pda.api.client.PdaSounds.tab(); action.run(); }, "true",
                active + " ? " + PdaShell.PRESS + " : " + PdaShell.PANEL, PdaShell.HOVER, PdaShell.PRESS,
                PdaShell.EDGE, 1, 0, 4, 1, 0, 0, Point.TOP_RIGHT, text(label, PdaShell.TEXT)));
    }

    private static WidgetDef small(String label, Runnable action) {
        return new Button(item -> action.run(), "true", PdaShell.PANEL, PdaShell.HOVER, PdaShell.PRESS,
                PdaShell.EDGE, 1, 0, 3, 1, 0, 0, Point.TOP_LEFT, text(label, PdaShell.TEXT));
    }

    /** Camera pad key {@code key}: held => {@link #padTick} drives it; same look as the chips. */
    private record Hold(int key) implements WidgetDef {
        private static final float W = 18;
        private static final float H = 12;

        @Override
        public Widget create(Binder binder) {
            return new Node(binder.color(PdaShell.PANEL), binder.color(PdaShell.HOVER), binder.color(PdaShell.PRESS),
                    binder.color(PdaShell.EDGE), binder.color(PdaShell.TEXT), binder.color(PdaShell.DIM));
        }

        private final class Node extends Widget implements Interactive {
            private final ColorProp back;
            private final ColorProp over;
            private final ColorProp down;
            private final ColorProp edge;
            private final ColorProp text;
            private final ColorProp dim;

            Node(ColorProp back, ColorProp over, ColorProp down, ColorProp edge, ColorProp text, ColorProp dim) {
                this.back = back;
                this.over = over;
                this.down = down;
                this.edge = edge;
                this.text = text;
                this.dim = dim;
            }

            @Override
            public void measure(UiEval ev) {
                width = W;
                height = H;
            }

            @Override
            public void draw(UiEval ev, float x, float y, float w, float h) {
                boolean on = feed() != 0;
                int fill = back.get(ev);
                if (on && ev.input != null) {
                    ev.input.hit(x, y, x + W, y + H, this, null);
                    if (ev.input.hovered(this, null)) {
                        fill = over.get(ev);
                    }
                }
                if (padHeld == key) {
                    fill = down.get(ev);
                }
                Canvas canvas = ev.canvas;
                float cut = UiSkin.current().inPda() ? Chamfer.cut(W, H) : 0;
                if (cut > 0) {
                    Chamfer.fill(canvas, x, y, x + W, y + H, cut, fill);
                    Chamfer.frame(canvas, x + 0.5f, y + 0.5f, x + W - 0.5f, y + H - 0.5f, cut, 1, edge.get(ev));
                } else {
                    canvas.fill(x, y, x + W, y + H, fill);
                    canvas.frame(x, y, x + W, y + H, 1, edge.get(ev));
                }
                String label = PAD[key];
                PdaText.line(ev, label, x + (W - canvas.width(label)) / 2, y + 2, W, on ? text.get(ev) : dim.get(ev));
            }

            @Override
            public void press(Object item, float px, float py) {
                padHeld = key;
                pad(key);
            }

            @Override
            public void release(Object item, float px, float py, boolean inside) {
                if (padHeld == key) {
                    padHeld = -1;
                }
            }
        }
    }

    /** Selected body's system cells, wrapped to {@code w}: green >= 75 %, amber >= 40 %, orange > 0, red. */
    private record Cells(float w) implements WidgetDef {
        private static final float CELL_H = 10;
        private static final float GAP = 2;

        private static int colour(float ratio) {
            return ratio >= 0.75f ? 0xFF6CC24A : ratio >= 0.4f ? 0xFFE0C040 : ratio > 0 ? 0xFFE07830 : 0xFFC03030;
        }

        @Override
        public Widget create(Binder binder) {
            return new Widget() {
                @Override
                public void measure(UiEval ev) {
                    width = w;
                    height = CELL_H;
                }

                @Override
                public void draw(UiEval ev, float x, float y, float bw, float bh) {
                    Row row = current();
                    if (row == null) {
                        return;
                    }
                    Canvas canvas = ev.canvas;
                    float cx = x;
                    for (RemoteBody.Cell cell : row.entry().cells()) {
                        float cw = Math.min(w, canvas.width(cell.label()) + 6);
                        if (cx + cw > x + w) {
                            break;
                        }
                        canvas.fill(cx, y, cx + cw, y + CELL_H, colour(cell.ratio()));
                        PdaText.line(ev, cell.label(), cx + 3, y + 1, cw - 6, cell.ratio() <= 0 ? 0xFFFFFFFF : 0xFF202020);
                        cx += cw + GAP;
                    }
                    if (ev.input != null && ev.input.over(x, y, x + w, y + CELL_H))
                        com.wf.wflib.pda.api.client.PdaPopup.show("SYSTEMS", "", String.join("; ",
                                row.entry().cells().stream().map(cell -> cell.label() + " " + Math.round(cell.ratio() * 100) + "%").toList()));
                }
            };
        }
    }
}
