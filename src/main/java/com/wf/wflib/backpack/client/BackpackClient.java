package com.wf.wflib.backpack.client;

import com.mojang.blaze3d.platform.InputConstants;
import com.wf.wflib.backpack.BackpackPackets;
import com.wf.wflib.backpack.api.Backpacks;
import com.wf.wflib.backpack.api.client.BackpackLayer;
import com.wf.wflib.probe.api.ProbeAction;
import com.wf.wflib.probe.api.ProbeContext;
import com.wf.wflib.probe.api.ProbeNode;
import com.wf.wflib.probe.api.ProbeProviders;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.resources.PlayerSkin;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;
import net.neoforged.neoforge.client.settings.KeyConflictContext;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.network.PacketDistributor;
import org.lwjgl.glfw.GLFW;

import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;
import com.wf.wflib.core.api.WFLib;

public final class BackpackClient {
    private static final int NODE = 0xFF9ACD60;
    private static final int LOCKED = 0xFFE0B040;
    private static final Map<Entity, Drawn> BACKS = new WeakHashMap<>();

    public static final KeyMapping WEAR = new KeyMapping("key.wflib.backpack", KeyConflictContext.IN_GAME,
            InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_B, "key.categories.wflib");

    private BackpackClient() {
    }

    @SubscribeEvent
    public static void keys(RegisterKeyMappingsEvent e) {
        e.register(WEAR);
    }

    @SubscribeEvent
    public static void screens(RegisterMenuScreensEvent e) {
        e.register(Backpacks.MENU.get(), BackpackScreen::new);
    }

    @SubscribeEvent
    public static void layers(EntityRenderersEvent.AddLayers e) {
        for (PlayerSkin.Model skin : e.getSkins()) {
            if (e.getSkin(skin) instanceof LivingEntityRenderer<?, ?> r) {
                addLayer(r);
            }
        }
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static void addLayer(LivingEntityRenderer r) {
        r.addLayer(new BackpackLayer<>(r, p -> Backpacks.worn((Player) p),
                (p, at) -> BACKS.put((Entity) p, new Drawn((Vec3) at, ((Entity) p).level().getGameTime()))));
    }

    @SubscribeEvent
    public static void setup(FMLClientSetupEvent e) {
        Backpacks.PANEL_SHOWN.set(WFLib.MODID, BackpackPanel::shown);
        NeoForge.EVENT_BUS.addListener((ClientTickEvent.Post t) -> {
            while (WEAR.consumeClick()) {
                PacketDistributor.sendToServer(new BackpackPackets.Action(BackpackPackets.Action.Kind.TOGGLE_WORN));
            }
        });
        NeoForge.EVENT_BUS.addListener(BackpackPanel::background);
        NeoForge.EVENT_BUS.addListener(BackpackPanel::foreground);
        NeoForge.EVENT_BUS.addListener(BackpackPanel::scrolled);
        NeoForge.EVENT_BUS.addListener(BackpackPanel::pressed);
        NeoForge.EVENT_BUS.addListener(BackpackPanel::dragged);
        NeoForge.EVENT_BUS.addListener(BackpackPanel::released);
        if (com.wf.wflib.core.api.WFSystems.enabled("probe")) {
            ProbeProviders.addEntityNodes(ResourceLocation.fromNamespaceAndPath(WFLib.MODID, "backpack"),
                    entity -> entity instanceof Player p && !Backpacks.worn(p).isEmpty(),
                    BackpackClient::node, ProbeProviders.CAPABILITY_PRIORITY);
        }
    }

    /** Node on the drawn pack (last {@link BackpackLayer} draw); not drawn lately => none. */
    private static void node(List<ProbeNode> out, ProbeContext ctx, Entity entity) {
        Drawn at = BACKS.get(entity);
        if (entity == ctx.player() || at == null || ctx.level().getGameTime() - at.tick > 2) {
            return;
        }
        ItemStack worn = Backpacks.worn((Player) entity);
        out.add(new ProbeNode("backpack", at.pos, info -> {
            info.title(worn.getHoverName());
            ProbeAction open = ProbeAction.of(Backpacks.OPEN,
                    Component.translatable("wflib.backpack.open", worn.getHoverName()));
            info.action(Backpacks.locked(worn) ? open.unavailable(Component.translatable("wflib.backpack.locked")) : open);
        }).colour(Backpacks.locked(worn) ? LOCKED : NODE).reach(Backpacks.VIEW_RANGE + 1).radius(0.25));
    }

    private record Drawn(Vec3 pos, long tick) {
    }
}
