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
        if (com.wf.wflib.core.api.WFSystems.enabled("worldstats")) com.wf.wflib.worldstats.api.WorldStats.tag("threat", () -> threat.isEmpty() ? null : threat);
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
