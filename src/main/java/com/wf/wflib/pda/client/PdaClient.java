package com.wf.wflib.pda.client;

import com.wf.wflib.client.cam.CameraLink;
import com.wf.wflib.client.screen.EmbeddedScreen;
import com.wf.wflib.WFLib;
import com.wf.wflib.pda.Pda;
import com.wf.wflib.ui.UiSurfaces;
import com.wf.wflib.ui.input.UiInput;
import com.wf.wflib.ui.input.UiKeyboard;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.PauseScreen;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.InteractionHand;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.InputEvent;
import net.neoforged.neoforge.client.event.RenderFrameEvent;
import net.neoforged.neoforge.client.event.RenderGuiLayerEvent;
import net.neoforged.neoforge.client.event.ScreenEvent;
import net.neoforged.neoforge.client.gui.VanillaGuiLayers;
import org.lwjgl.glfw.GLFW;

/**
 * Local PDA state: carried / held (main hand), focus (raised: mouse drives the screen cursor, WASD still walks)
 * and the open app. No Screen while focused: {@code setScreen} releases movement keys and the mouse grab.
 */
@EventBusSubscriber(modid = WFLib.MODID, value = Dist.CLIENT)
public final class PdaClient {
    /** Raise / lower per tick, 0..1. */
    private static final float RAISE_STEP = 0.25f;
    /** Surface units per {@code LocalPlayer.turn} unit (~1 mouse count at default sensitivity). */
    private static final float CURSOR_GAIN = 0.55f;

    private static boolean carried;
    private static boolean held;
    private static boolean focused;
    /** Presentation only: the actual PDA never leaves its source inventory/Curios slot. */
    private static boolean virtualHand;
    private static int focusSlot = -1;
    private static LocalPlayer focusPlayer;

    /** Use key still down since the last toggle: {@code startUseItem} repeats while held. */
    private static boolean useLatch;
    private static float raise;
    private static float raiseO;
    private static float cursorX = PdaShell.WIDTH / 2;
    private static float cursorY = PdaShell.HEIGHT / 2;
    private static boolean pressed;
    /** Press seen since the last frame: a press + release between frames still reaches the screen. */
    private static boolean pressLatch;
    private static boolean secondaryLatch;
    private static float wheel;
    private static PdaApp app;
    /** Feed on the whole screen; 0 = none. */
    private static int fullscreenFeed;
    private static Runnable pendingRaise;
    private static int pendingTicks;

    private PdaClient() {
    }

    /** Local player has a PDA anywhere in the inventory (TV keys, alerts). */
    public static boolean carried() {
        return carried;
    }

    /** Presented in the main-hand view (physical main hand or raised carried PDA). */
    public static boolean held() {
        return held;
    }

    /** Whether the two-handed view temporarily replaces the physical held items. */
    public static boolean virtualHand() {
        return virtualHand;
    }

    /** Raise a carried PDA without moving it; don't interrupt an item already being used. */
    public static boolean raiseCarried() {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        if (player == null || !player.isAlive() || mc.screen != null || UiKeyboard.focused() != null
                || player.isUsingItem() || !Pda.carries(player)) {
            return false;
        }
        carried = true;
        virtualHand = !Pda.is(player.getMainHandItem());
        held = true;
        focusPlayer = player;
        focusSlot = player.getInventory().selected;
        focused = true;
        return true;
    }

    public static void toggleCarried() {
        if (UiKeyboard.focused() != null) {
            return;
        }
        if (focused) {
            lower();
        } else {
            raiseCarried();
        }
    }

    /** Missing/hidden optional apps leave the current state untouched. */
    public static boolean openCarried(ResourceLocation id) {
        Minecraft mc = Minecraft.getInstance();
        PdaApp next = PdaApps.get(id);
        if (next == null || mc.player == null || !Pda.carries(mc.player)) {
            return false;
        }
        // Built-in apps require held(), so evaluate visibility with the prospective presentation.
        boolean wasHeld = held;
        held = true;
        boolean visible;
        try {
            visible = next.visible(mc.player);
        } finally {
            held = wasHeld;
        }
        if (!visible || !raiseCarried()) {
            return false;
        }
        open(next);
        return true;
    }

    public static boolean focused() {
        return focused;
    }

    /** 0 = rest, 1 = raised. */
    public static float raise(float partialTick) {
        return raiseO + (raise - raiseO) * partialTick;
    }

    public static float cursorX() {
        return cursorX;
    }

    public static float cursorY() {
        return cursorY;
    }

    public static int fullscreenFeed() {
        return fullscreenFeed;
    }

    /** Feed {@code feedId} (linked by the caller through {@code CameraLink}) over the whole screen while focused. */
    public static void fullscreen(int feedId) {
        if (focused && feedId != 0) {
            UiKeyboard.blur();
            fullscreenFeed = feedId;
        }
    }

    /** Raise and watch {@code feedId} full screen (e.g. on releasing a remote body); PDA must be in hand. */
    public static void watch(int feedId) {
        if (!held || feedId == 0) {
            return;
        }
        focused = true;
        CameraLink.open(feedId);
        fullscreen(feedId);
    }

    /** Raise, then run {@code then}, as soon as the PDA is in the main hand (waits up to 1 s for an equip swap). */
    public static void raiseWhenHeld(Runnable then) {
        pendingRaise = then;
        pendingTicks = 20;
        if (held && Minecraft.getInstance().screen == null) {
            runPending();
        }
    }

    private static void runPending() {
        Runnable then = pendingRaise;
        pendingRaise = null;
        focused = true;
        then.run();
    }

    /** Null = home grid. */
    public static PdaApp app() {
        return app;
    }

    public static void open(PdaApp next) {
        if (app == next) {
            return;
        }
        UiKeyboard.blur();
        if (app != null) {
            app.closed();
        }
        app = next;
        if (next != null) {
            next.opened();
        }
    }

    public static void home() {
        open(null);
    }

    /** Lowers to rest; the open app stays. */
    public static void lower() {
        virtualHand = false;
        focusSlot = -1;
        focusPlayer = null;
        LocalPlayer player = Minecraft.getInstance().player;
        held = player != null && Pda.is(player.getMainHandItem());
        fullscreenFeed = 0;
        UiKeyboard.blur();
        if (focused) {
            focused = false;
            pressed = false;
            pressLatch = false;
            secondaryLatch = false;
            wheel = 0;
            UiInput input = UiSurfaces.input(PdaRenderer.SURFACE);
            if (input != null) {
                input.reset();
            }
        }
    }

    /** Mouse turn as {@code LocalPlayer.turn} takes it. @return true while focused (cursor consumed it). */
    public static boolean steer(double yaw, double pitch) {
        if (!focused) {
            return false;
        }
        if (fullscreenFeed != 0) {
            PdaFullscreen.steer(yaw, pitch);
            return true;
        }
        cursorX = Math.max(0, Math.min(PdaShell.WIDTH - 1, cursorX + (float) yaw * CURSOR_GAIN));
        cursorY = Math.max(0, Math.min(PdaShell.HEIGHT - 1, cursorY + (float) pitch * CURSOR_GAIN));
        return true;
    }

    @SubscribeEvent
    static void onTick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        raiseO = raise;
        if (player == null) {
            carried = false;
            held = false;
            lower();
            open(null);
            raise = raiseO = 0;
            pendingRaise = null;
            useLatch = false;
            return;
        }
        carried = Pda.carries(player);
        held = Pda.is(player.getMainHandItem()) || virtualHand;
        // Own prompt (marker name) keeps it raised.
        if (!carried || !held || mc.screen != null && !(mc.screen instanceof EmbeddedScreen.Standalone)
                || !player.isAlive() || focused && focusPlayer != null && (focusPlayer != player
                || focusSlot != player.getInventory().selected)) {
            lower();
        }
        if (focused && focusPlayer == null) {
            focusPlayer = player;
            focusSlot = player.getInventory().selected;
        }
        if (pendingRaise != null) {
            if (held && player.isAlive() && mc.screen == null) {
                runPending();
            } else if (--pendingTicks <= 0) {
                pendingRaise = null;
            }
        }
        if (!mc.options.keyUse.isDown()) {
            useLatch = false;
        }
        if (!carried) {
            open(null);
        }
        raise = focused ? Math.min(1, raise + RAISE_STEP) : Math.max(0, raise - RAISE_STEP);
        PdaApps.refresh(player);
        // Drain clicks even when a screen/text field owns input: never replay a typing key later.
        while (PdaKeys.TOGGLE.consumeClick()) {
            if (mc.screen == null && UiKeyboard.focused() == null) {
                toggleCarried();
            }
        }
        while (PdaKeys.TERMINAL.consumeClick()) {
            openCarried(ResourceLocation.fromNamespaceAndPath("wflib", "terminal"));
        }
        while (PdaKeys.PLUGINLESS.consumeClick()) {
            openCarried(ResourceLocation.fromNamespaceAndPath("pluginless", "pluginless"));
        }
    }

    @SubscribeEvent
    static void onInteraction(InputEvent.InteractionKeyMappingTriggered event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.screen != null || mc.player == null) {
            return;
        }
        // A use that lowers a virtual PDA must not fall through to the real offhand afterwards.
        if (useLatch && event.isUseItem()) {
            event.setCanceled(true);
            event.setSwingHand(false);
            return;
        }
        if (!held) {
            return;
        }
        if (event.isUseItem()) {
            // Sneak + use => the item's own interactions (link a target) run instead.
            if (!focused && mc.player.isShiftKeyDown()) {
                return;
            }
            event.setCanceled(true);
            event.setSwingHand(false);
            if (event.getHand() != InteractionHand.MAIN_HAND || useLatch) {
                return;
            }
            useLatch = true;
            if (fullscreenFeed != 0) {
                fullscreenFeed = 0;
            } else if (focused) {
                lower();
            } else {
                raiseCarried();
            }
        } else if (focused) {
            event.setCanceled(true);
            event.setSwingHand(false);
        }
    }

    @SubscribeEvent
    static void onKey(InputEvent.Key event) {
        Minecraft mc = Minecraft.getInstance();
        if (focused && mc.screen == null) {
            while (mc.options.keyDrop.consumeClick()) { }
            while (mc.options.keySwapOffhand.consumeClick()) { }
        }
    }

    /** Primary button = pointer; never reaches attack while focused. */
    @SubscribeEvent
    static void onMouseButton(InputEvent.MouseButton.Pre event) {
        if (fullscreenFeed != 0 && event.getButton() == GLFW.GLFW_MOUSE_BUTTON_LEFT) {
            event.setCanceled(true);
            if (event.getAction() == GLFW.GLFW_PRESS && Minecraft.getInstance().screen == null) {
                PdaFullscreen.control();
            }
            return;
        }
        if (!focused || Minecraft.getInstance().screen != null) {
            return;
        }
        if (event.getButton() == GLFW.GLFW_MOUSE_BUTTON_RIGHT && fullscreenFeed == 0 && app != null
                && app.ownsSecondary()) {
            secondaryLatch |= event.getAction() == GLFW.GLFW_PRESS;
            event.setCanceled(true);
            return;
        }
        if (event.getButton() != GLFW.GLFW_MOUSE_BUTTON_LEFT) {
            return;
        }
        pressed = event.getAction() != GLFW.GLFW_RELEASE;
        pressLatch |= pressed;
        event.setCanceled(true);
    }

    @SubscribeEvent
    static void onScroll(InputEvent.MouseScrollingEvent event) {
        if (focused && Minecraft.getInstance().screen == null) {
            if (fullscreenFeed != 0) {
                com.wf.wflib.client.cam.CameraLink.zoomBy(event.getScrollDeltaY());
                event.setCanceled(true);
                return;
            }
            wheel += (float) event.getScrollDeltaY();
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    static void onGuiLayer(RenderGuiLayerEvent.Pre event) {
        if (focused && event.getName().equals(VanillaGuiLayers.CROSSHAIR)) {
            event.setCanceled(true);
        }
    }

    /** Esc lowers instead of pausing. */
    @SubscribeEvent
    static void onScreen(ScreenEvent.Opening event) {
        if (focused && event.getNewScreen() instanceof PauseScreen) {
            event.setCanceled(true);
            if (fullscreenFeed != 0) {
                fullscreenFeed = 0;
            } else {
                lower();
            }
        }
    }

    /** Before {@link UiSurfaces} redraws the screen at default priority. */
    @SubscribeEvent(priority = EventPriority.HIGH)
    static void onFrame(RenderFrameEvent.Pre event) {
        if (fullscreenFeed != 0 && !com.wf.wflib.client.cam.CameraLink.active()) {
            fullscreenFeed = 0;
        }
        if (!focused || fullscreenFeed != 0) {
            return;
        }
        UiInput input = UiSurfaces.input(PdaRenderer.SURFACE);
        if (input == null) {
            return;
        }
        input.pointer(cursorX, cursorY, (pressed || pressLatch ? UiInput.PRIMARY : 0)
                | (secondaryLatch ? UiInput.SECONDARY : 0));
        pressLatch = false;
        secondaryLatch = false;
        if (wheel != 0) {
            input.wheel(wheel);
            wheel = 0;
        }
    }
}
