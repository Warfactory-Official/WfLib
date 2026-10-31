package com.wf.wflib.ui;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.wf.wflib.ui.api.widget.Align;
import com.wf.wflib.ui.api.widget.Bar;
import com.wf.wflib.ui.api.widget.Box;
import com.wf.wflib.ui.api.widget.Button;
import com.wf.wflib.ui.api.widget.Item;
import com.wf.wflib.ui.api.widget.SkinPart;
import com.wf.wflib.ui.api.widget.Tooltip;
import com.wf.wflib.ui.api.UiScreen;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import java.util.List;
import com.wf.wflib.ui.api.widget.Image;
import com.wf.wflib.ui.api.widget.Ladder;
import com.wf.wflib.ui.api.widget.Point;
import com.wf.wflib.ui.api.widget.Sized;
import com.wf.wflib.ui.api.widget.Tape;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

import java.util.Map;
import com.wf.wflib.ui.api.Binder;
import com.wf.wflib.ui.api.UiContext;
import com.wf.wflib.ui.api.UiEval;
import com.wf.wflib.ui.api.UiHud;
import com.wf.wflib.ui.api.UiInstance;
import com.wf.wflib.ui.api.UiLayout;
import com.wf.wflib.ui.api.UiSurfaces;
import com.wf.wflib.ui.api.Widget;
import com.wf.wflib.ui.api.WidgetDef;
import com.wf.wflib.core.api.WFLib;

import static com.wf.wflib.ui.api.Ui.*;

/**
 * {@code /wfui demo}: every built-in widget on the HUD; {@code /wfui screen}: screen-only widgets (tooltip, item,
 * skin part, hover border) in a {@link UiScreen}; {@code /wfui stats}: {@link UiStats}.
 */
public final class UiDebug {
    private static UiInstance demo;
    private static UiInstance surfaceView;
    private static boolean surface;
    private static boolean shown;
    private static int screenClicks;

    private UiDebug() {
    }

    public static void register() {
        UiHud.addSource(UiHud.Layer.MAIN, ResourceLocation.fromNamespaceAndPath(WFLib.MODID, "ui_debug"), 0,
                UiDebug::collect);
    }

    public static LiteralArgumentBuilder<CommandSourceStack> command() {
        return Commands.literal("wfui")
                .then(Commands.literal("demo").executes(ctx -> {
                    shown = !shown;
                    ctx.getSource().sendSuccess(() -> Component.literal("wfui demo " + (shown ? "on" : "off")), false);
                    return 1;
                }))
                .then(Commands.literal("surface").executes(ctx -> {
                    surface = !surface;
                    ctx.getSource().sendSuccess(() -> Component.literal("wfui surface " + (surface ? "on" : "off")), false);
                    return 1;
                }))
                .then(Commands.literal("screen").executes(ctx -> {
                    openScreen();
                    return 1;
                }))
                .then(Commands.literal("stats").executes(ctx -> stats(ctx.getSource(), false))
                        .then(Commands.literal("detail").executes(ctx -> stats(ctx.getSource(), true))));
    }

    private static int stats(CommandSourceStack source, boolean detail) {
        boolean on = UiStats.toggle(detail);
        source.sendSuccess(() -> Component.literal("wfui stats " + (on ? "on" : "off")), false);
        return 1;
    }

    private static void collect(UiHud.Collector out) {
        if (shown) {
            if (demo == null) {
                demo = demoLayout().instantiate();
            }
            out.add(demo);
        }
        if (surface) {
            if (surfaceView == null) {
                UiLayout demoLayout = demoLayout();
                surfaceView = layout(ResourceLocation.fromNamespaceAndPath(WFLib.MODID, "demo_surface"),
                        new SurfaceImage(demoLayout)).instantiate();
            }
            out.add(surfaceView);
        }
    }

    /** The demo drawn into a 640 x 360 surface (320 x 180 units) and shown as a texture, top-left. */
    private record SurfaceImage(UiLayout source) implements WidgetDef {
        @Override
        public Widget create(Binder binder) {
            return new Widget() {
                @Override
                public void measure(UiEval ev) {
                }

                @Override
                public void draw(UiEval ev, float x, float y, float w, float h) {
                    ResourceLocation texture = UiSurfaces.request(SurfaceImage.this, source, UiContext.EMPTY, 640, 360,
                            320, 180, 30, 0x60000000);
                    ev.canvas.frame(7, 39, 169, 131, 1, 0xFFFFFFFF);
                    ev.canvas.sprite(texture, 8, 40, 168, 130, 0xFFFFFFFF);
                }
            };
        }
    }

    private static final ItemStack SWORD = new ItemStack(Items.DIAMOND_SWORD);
    private static final ItemStack APPLES = new ItemStack(Items.APPLE, 12);
    private static final List<Component> TIP = List.of(Component.literal("Button"),
            Component.literal("hover border #ffdd55, 2").withStyle(ChatFormatting.GRAY));

    public static void openScreen() {
        Minecraft.getInstance().tell(() -> UiScreen.open(screenLayout(), UiContext.EMPTY,
                Component.literal("wfui screen"), UiScreen.Options.DEFAULT));
    }

    static UiLayout screenLayout() {
        String w = "#ffffff";
        return layout(ResourceLocation.fromNamespaceAndPath(WFLib.MODID, "demo_screen"), stack(
                rect(240, 120, "#e0101014"),
                at("8", "6", text("wfui screen", w)),
                at("8", "22", row(8, Align.CENTER,
                        new Tooltip(item -> TIP, new Button(item -> screenClicks++, "true", "#ff303030", "#ff404040",
                                "#ff202020", "#ff606060", 1, 6, 6, 4, 0, 0, Point.CENTER, text("Button", w),
                                "#ffffdd55", 2)),
                        new Item(item -> SWORD, 16, false),
                        new Item(item -> APPLES, 24, true))),
                at("8", "56", row(6, Align.END,
                        new SkinPart(SkinPart.Part.HEAD, "-1", "1", 24, 24),
                        new SkinPart(SkinPart.Part.TORSO, "-1", "0.5", 16, 24),
                        new SkinPart(SkinPart.Part.RIGHT_ARM, "-1", "0.1", 8, 24),
                        new SkinPart(SkinPart.Part.LEFT_LEG, "-1", "0", 8, 24))))).absolute().withDesignSize(240, 120);
    }

    static UiLayout demoLayout() {
        String green = "#33ff33";
        return layout(ResourceLocation.fromNamespaceAndPath(WFLib.MODID, "demo"), stack(
                place("ui.width / 2", "12", Point.TOP, new Tape("camera.yaw", false, false, false, 4, 5, 3, "120", 6, 8, 1,
                        true, 12, 0, -180, 180, Map.of(0f, "S", 90f, "W", -180f, "N", -90f, "E"), green, green)),
                place("ui.width / 2", "41", Point.TOP, poly(green, 0, 0, -3, 6, 3, 6)),
                place("ui.width / 2", "ui.height / 2", Point.CENTER, new Sized("120", "70", Point.CENTER, true,
                        rotate("sin(ui.time) * 20", Point.CENTER, new Sized("0", "0", Point.CENTER, false,
                                new Ladder("camera.pitch * -1", 2, 5, 20, 3, 4, 1, 3, 1, 32, green))))),
                anchor(Point.BOTTOM_RIGHT, 6, new Box("#c0101014", "#60ffffff", 1, 3, 6, 5, 0, 0, Point.TOP_LEFT,
                        column(3,
                                shadowed("{tr:menu.game}  t={ui.time|.1}s", "#ffffff"),
                                text("HP {player.health|.1} / {player.max_health}", "player.health < 6 ? #ff4040 : " + green),
                                new Bar(120, 5, "player.health / player.max_health", "mixc(#ff3030, #30ff30, player.health / player.max_health)",
                                        "#40000000", "#80ffffff", Bar.Direction.RIGHT, false, "0.5", "#ffffff"),
                                new Bar(120, 5, "sin(ui.time)", green, "#40000000", "0", Bar.Direction.RIGHT, true, "0/0", "0"),
                                row(4, Align.CENTER,
                                        new Image(ResourceLocation.withDefaultNamespace("textures/item/diamond.png"), 16, 16,
                                                0, 0, 1, 1, "#ffffffff", "ui.time * 90"),
                                        text("yaw {camera.yaw|+.1}  pitch {camera.pitch|03}", "#ffffff"),
                                        when("blink(1)", text("BLINK", "#ffcc00"))),
                                row(6,
                                        sized(24, 24, stack(
                                                arc("12", "12", "10", "2", "0", "360", "#40ffffff"),
                                                arc("12", "12", "10", "2", "-90", "wrap(ui.time * 120, 0, 360)", green))),
                                        sized(24, 24, arc("12", "12", "11", "0", "-90", "smooth(abs(sin(ui.time)) * 360, 0.3)", "#8040a0ff")),
                                        sized(40, 24, stack(
                                                line("0", "12", "40", "12 + sin(ui.time * 3) * 10", "1", "#ffffff"),
                                                line("0", "20", "40", "4", "0.5", "#ff8080"))),
                                        new Box("#00000000", "#ffff00", 1.5f, 6, 4, 4, 30, 24, Point.CENTER,
                                                text("{ui.dt * 1000|.1}", "#ffff00"))))))));
    }
}
