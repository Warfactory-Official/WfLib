package com.wf.wflib.contract.client;

import com.wf.wflib.contract.pda.PmcNet;
import com.wf.wflib.contract.pda.Snapshot;
import com.wf.wflib.pda.api.client.PdaApps;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.neoforge.network.PacketDistributor;

/** Client side of the PDA app: the latest state from the server and the messages back. Never loaded on a dedicated server. */
public final class PmcClient {
    private static volatile Snapshot state = Snapshot.empty();
    /** While the app is open it asks for the state this often (ticks), so a finished contract or a new offer shows without a click. */
    private static final int REFRESH_TICKS = 40;
    private static int ticks;

    private PmcClient() {
    }

    public static void init(IEventBus modBus) {
        net.neoforged.neoforge.common.NeoForge.EVENT_BUS.addListener(net.neoforged.neoforge.client.event.RegisterClientCommandsEvent.class, PmcClient::commands);
        net.neoforged.neoforge.common.NeoForge.EVENT_BUS.addListener(net.neoforged.neoforge.client.event.ClientTickEvent.Post.class, e -> {
            if (PmcApp.open() && ++ticks % REFRESH_TICKS == 0) request();
        });
        modBus.addListener(net.neoforged.neoforge.client.extensions.common.RegisterClientExtensionsEvent.class, com.wf.wflib.contract.item.client.PmcItemModels::register);
        if (com.wf.wflib.core.api.WFSystems.enabled("worldstats")) {
            com.wf.wflib.worldstats.api.WorldStats.tag("threat", () -> threat.isEmpty() ? null : threat);
            com.wf.wflib.worldstats.api.WorldStats.section("missions", PmcClient::missionLines);
        }
        modBus.addListener(FMLClientSetupEvent.class, e -> e.enqueueWork(() -> {
            PmcApp.register();
            PdaApps.register(PmcApp.INSTANCE, 700);
        }));
    }

    /**
     * {@code /pmcpda act <verb> [arg]} sends what the PDA buttons send, and {@code /pmcpda state} prints the last state received:
     * the app's whole round trip can be driven and checked without clicking.
     */
    static void commands(net.neoforged.neoforge.client.event.RegisterClientCommandsEvent e) {
        var say = (java.util.function.Consumer<String>) m -> net.minecraft.client.Minecraft.getInstance().gui.getChat().addMessage(net.minecraft.network.chat.Component.literal(m));
        e.getDispatcher().register(net.minecraft.commands.Commands.literal("pmcpose")
                .then(net.minecraft.commands.Commands.literal("cam").then(net.minecraft.commands.Commands.argument("view", com.mojang.brigadier.arguments.IntegerArgumentType.integer(0, 2)).executes(c -> {
                    var mc = net.minecraft.client.Minecraft.getInstance();
                    mc.options.setCameraType(net.minecraft.client.CameraType.values()[com.mojang.brigadier.arguments.IntegerArgumentType.getInteger(c, "view")]);
                    return 1;
                })))
                .then(net.minecraft.commands.Commands.literal("dump").executes(c -> {
                    for (var m : java.util.List.of(com.wf.wflib.contract.item.client.PmcItemModels.FIRST, com.wf.wflib.contract.item.client.PmcItemModels.THIRD))
                        m.forEach((k, v) -> say.accept(k + " " + v));
                    return 1;
                }))
                .then(net.minecraft.commands.Commands.argument("hand", com.mojang.brigadier.arguments.StringArgumentType.word())
                        .then(net.minecraft.commands.Commands.argument("item", com.mojang.brigadier.arguments.StringArgumentType.word())
                                .then(net.minecraft.commands.Commands.argument("v", com.mojang.brigadier.arguments.StringArgumentType.greedyString()).executes(c -> {
                                    String[] v = com.mojang.brigadier.arguments.StringArgumentType.getString(c, "v").trim().split("\\s+");
                                    var table = com.mojang.brigadier.arguments.StringArgumentType.getString(c, "hand").equals("first")
                                            ? com.wf.wflib.contract.item.client.PmcItemModels.FIRST : com.wf.wflib.contract.item.client.PmcItemModels.THIRD;
                                    String item = com.mojang.brigadier.arguments.StringArgumentType.getString(c, "item");
                                    var old = table.get(item);
                                    float[] f = {old.tx(), old.ty(), old.tz(), old.rx(), old.ry(), old.rz(), old.len()};
                                    for (int i = 0; i < v.length && i < 7; i++) if (!v[i].equals("_")) f[i] = Float.parseFloat(v[i]);
                                    table.put(item, new com.wf.wflib.contract.item.client.PmcItemModels.Pose(f[0], f[1], f[2], f[3], f[4], f[5], f[6]));
                                    return 1;
                                })))));
        e.getDispatcher().register(net.minecraft.commands.Commands.literal("pmcpda")
                .then(net.minecraft.commands.Commands.literal("state").executes(c -> {
                    Snapshot s = state;
                    say.accept("rank " + s.rank() + " rep " + s.rep() + ", " + s.offers().size() + " offers"
                            + (s.active() == null ? "" : ", active " + s.active().title()) + ", notice '" + s.notice() + "'");
                    return 1;
                }))
                .then(net.minecraft.commands.Commands.literal("tab").then(net.minecraft.commands.Commands.argument("n", com.mojang.brigadier.arguments.IntegerArgumentType.integer(0, 3)).executes(c -> {
                    PmcApp.showTab(com.mojang.brigadier.arguments.IntegerArgumentType.getInteger(c, "n"));
                    return 1;
                })))
                .then(net.minecraft.commands.Commands.literal("act")
                        .then(net.minecraft.commands.Commands.argument("verb", com.mojang.brigadier.arguments.StringArgumentType.word())
                                .executes(c -> {
                                    act(com.mojang.brigadier.arguments.StringArgumentType.getString(c, "verb"), "");
                                    return 1;
                                })
                                .then(net.minecraft.commands.Commands.argument("arg", com.mojang.brigadier.arguments.StringArgumentType.greedyString())
                                        .executes(c -> {
                                            act(com.mojang.brigadier.arguments.StringArgumentType.getString(c, "verb"),
                                                    com.mojang.brigadier.arguments.StringArgumentType.getString(c, "arg"));
                                            return 1;
                                        })))));
    }

    private static volatile String threat = "";

    /** The server's word on this operator's tier; shown by WFLib's world-stats overlay as {threat}. */
    public static void threat(String text) {
        threat = text;
    }

    private record Mission(PmcNet.Mission data, long seenAt) {}

    private static volatile java.util.List<Mission> missions = java.util.List.of();

    /** The contracts this player is on (waypoints and the mission log). Client thread. */
    public static void missions(java.util.List<PmcNet.Mission> list) {
        var mc = net.minecraft.client.Minecraft.getInstance();
        long now = mc.level == null ? 0 : mc.level.getGameTime();
        missions = list.stream().map(m -> new Mission(m, now)).toList();
        com.wf.wflib.map.api.client.WFMaps.waypoints("missions", list.stream().map(m -> new com.wf.wflib.map.api.client.MapWaypoint(
                m.title(), net.minecraft.resources.ResourceKey.create(net.minecraft.core.registries.Registries.DIMENSION,
                net.minecraft.resources.ResourceLocation.parse(m.dimension())), new net.minecraft.core.BlockPos(m.x(), m.y(), m.z()), 0xFFD24A)).toList());
    }

    /** The mission log's lines for the overlay: a header, then each contract with how far off it is and how long is left. */
    static java.util.List<String> missionLines() {
        var mc = net.minecraft.client.Minecraft.getInstance();
        if (missions.isEmpty() || mc.player == null || mc.level == null) return null;
        java.util.List<String> out = new java.util.ArrayList<>();
        out.add("\u00a76Missions");
        long now = mc.level.getGameTime();
        for (Mission m : missions) {
            var d = m.data();
            String where;
            if (!d.dimension().equals(mc.level.dimension().location().toString())) {
                where = "other dimension";
            } else {
                double dx = d.x() + 0.5 - mc.player.getX(), dz = d.z() + 0.5 - mc.player.getZ();
                int dist = (int) Math.round(Math.sqrt(dx * dx + dz * dz));
                where = (dist >= 1000 ? String.format(java.util.Locale.ROOT, "%.1f km", dist / 1000.0) : dist + " m") + " " + compass(dx, dz);
            }
            long left = Math.max(0, d.ticksLeft() - (now - m.seenAt())) / 20;
            out.add("\u00a7e" + d.title() + " \u00a7f" + where + " \u00a77" + (left / 60) + ":" + String.format(java.util.Locale.ROOT, "%02d", left % 60));
        }
        return out;
    }

    private static String compass(double dx, double dz) {
        String[] names = {"E", "SE", "S", "SW", "W", "NW", "N", "NE"};
        double a = Math.toDegrees(Math.atan2(dz, dx));
        return names[(int) Math.floorMod(Math.round(a / 45.0), 8)];
    }

    public static void accept(Snapshot s) {
        state = s;
        PmcApp.changed(s);
    }

    public static Snapshot state() {
        return state;
    }

    public static void request() {
        PacketDistributor.sendToServer(new PmcNet.Request());
    }

    public static void act(String verb, String arg) {
        PacketDistributor.sendToServer(new PmcNet.Act(verb, arg));
    }
}
