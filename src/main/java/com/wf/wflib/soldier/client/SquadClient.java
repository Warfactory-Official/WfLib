package com.wf.wflib.soldier.client;

import com.mojang.blaze3d.platform.InputConstants;
import com.wf.wflib.soldier.Companion;
import com.wf.wflib.soldier.SquadPackets;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.LayeredDraw;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.client.settings.KeyConflictContext;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.network.PacketDistributor;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;

/**
 * The player's side of a squad: what the server says their soldiers are doing, which group is selected, and the keys that
 * order them. Hold {@code G} for the command wheel, tap {@code X} to send the selected group to the crosshair,
 * {@code V} to change group.
 */
public final class SquadClient {
    private static final String CATEGORY = "key.categories.wflib";
    static final KeyMapping WHEEL = key("squad_wheel", GLFW.GLFW_KEY_G), QUICK = key("squad_move", GLFW.GLFW_KEY_X),
            NEXT = key("squad_group", GLFW.GLFW_KEY_V);
    /** How long an order's marker and labels stay up (ticks). */
    static final int SHOWN = 120;
    static final double AIM_RANGE = 128;

    /** The roster the server last sent, and the selected group (-1: everyone, else a fire team). */
    static volatile List<SquadPackets.Member> members = List.of();
    static int group = -1;
    private static final List<Marker> MARKERS = new ArrayList<>();
    static String toast = "";
    static long toastAt = Long.MIN_VALUE;

    /** An order that was just given: where it points and who took it. */
    record Marker(SquadPackets.Echo echo, long born) {}

    private SquadClient() {}

    private static KeyMapping key(String name, int code) {
        return new KeyMapping("key.wflib." + name, KeyConflictContext.IN_GAME, InputConstants.Type.KEYSYM, code, CATEGORY);
    }

    public static void register(IEventBus modBus) {
        modBus.addListener(RegisterKeyMappingsEvent.class, e -> {
            e.register(WHEEL);
            e.register(QUICK);
            e.register(NEXT);
        });
        modBus.addListener(RegisterGuiLayersEvent.class, e ->
                e.registerAboveAll(ResourceLocation.fromNamespaceAndPath("wflib", "squad_hud"), (LayeredDraw.Layer) new SquadHud()));
        NeoForge.EVENT_BUS.addListener(ClientTickEvent.Post.class, SquadClient::onTick);
        NeoForge.EVENT_BUS.addListener(net.neoforged.neoforge.client.event.RenderLevelStageEvent.class, OrderMarkers::onRenderLevel);
    }

    public static void roster(List<SquadPackets.Member> list) {
        members = List.copyOf(list);
        if (group >= 0 && list.stream().noneMatch(m -> m.team() == group)) group = -1;
        if (list.isEmpty()) MARKERS.clear();
    }

    public static void echo(SquadPackets.Echo echo) {
        var mc = Minecraft.getInstance();
        if (mc.level == null) return;
        MARKERS.add(new Marker(echo, mc.level.getGameTime()));
        toast = (group < 0 ? "All" : Companion.TEAMS[group]) + ": " + echo.label() + (echo.entities().size() > 1 ? " (" + echo.entities().size() + ")" : "");
        toastAt = mc.level.getGameTime();
    }

    static List<Marker> markers() {
        return MARKERS;
    }

    static String target() {
        return group < 0 ? "all" : Companion.TEAMS[group].toLowerCase(java.util.Locale.ROOT);
    }

    /** The point the crosshair is on, or null. */
    static Vec3 aim() {
        var mc = Minecraft.getInstance();
        if (mc.player == null) return null;
        HitResult hit = mc.player.pick(AIM_RANGE, 1f, false);
        return hit.getType() == HitResult.Type.MISS ? null : hit.getLocation();
    }

    static void send(String kind, Vec3 pos) {
        PacketDistributor.sendToServer(new SquadPackets.Command(target(), kind, pos != null,
                pos == null ? 0 : pos.x, pos == null ? 0 : pos.y, pos == null ? 0 : pos.z, ""));
    }

    /** Everyone, then each fire team that has soldiers. */
    static void cycleGroup() {
        List<Integer> options = new ArrayList<>(List.of(-1));
        for (int t = 0; t < Companion.TEAMS.length; t++) {
            int team = t;
            if (members.stream().anyMatch(m -> m.team() == team)) options.add(t);
        }
        group = options.get((Math.max(0, options.indexOf(group)) + 1) % options.size());
    }

    static void selectGroup(int g) {
        if (g < 0 || members.stream().anyMatch(m -> m.team() == g)) group = g;
    }

    private static void onTick(ClientTickEvent.Post e) {
        var mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) {
            members = List.of();
            MARKERS.clear();
            return;
        }
        long now = mc.level.getGameTime();
        MARKERS.removeIf(m -> now - m.born() > SHOWN);
        if (mc.screen != null || members.isEmpty()) return;
        while (NEXT.consumeClick()) cycleGroup();
        while (QUICK.consumeClick()) {
            Vec3 at = aim();
            if (at != null) send("move", at);
            else mc.player.displayClientMessage(net.minecraft.network.chat.Component.translatable("wflib.squad.no_point"), true);
        }
        if (WHEEL.isDown()) mc.setScreen(new CommandWheel(aim()));
    }

    /** True while the wheel's key is held (a screen gets no key state of its own). */
    static boolean wheelHeld() {
        var mc = Minecraft.getInstance();
        return InputConstants.isKeyDown(mc.getWindow().getWindow(), WHEEL.getKey().getValue());
    }

    static BlockPos blockOf(Vec3 v) {
        return BlockPos.containing(v);
    }
}
