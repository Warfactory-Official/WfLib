package com.wf.wflib.ui.json;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.wf.wflib.ui.api.UiLayout;
import com.wf.wflib.ui.api.WidgetDef;
import com.wf.wflib.ui.api.input.UiAction;
import com.wf.wflib.ui.api.input.UiActions;
import com.wf.wflib.ui.api.widget.Button;
import com.wf.wflib.ui.api.widget.Item;
import com.wf.wflib.ui.api.widget.SkinPart;
import com.wf.wflib.ui.api.widget.Tooltip;
import com.wf.wflib.ui.api.input.UiStack;
import com.wf.wflib.ui.api.input.UiTooltip;
import com.wf.wflib.ui.api.widget.Flow;
import com.wf.wflib.ui.api.widget.Place;
import com.wf.wflib.ui.api.widget.Stack;
import com.wf.wflib.ui.api.widget.Text;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import com.wf.wflib.ui.api.json.UiJsonException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class UiExpanderTest {
    private final Map<ResourceLocation, UiExpander.Component> components = new HashMap<>();
    private final Map<ResourceLocation, JsonObject> palettes = new HashMap<>();

    private static JsonObject json(String text) {
        return JsonParser.parseString(text).getAsJsonObject();
    }

    private static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath("test", path);
    }

    private void component(String path, String text) {
        components.put(id(path), UiExpander.component(id(path), path, json(text)));
    }

    private UiLayout layout(String text) {
        return new UiExpander(components, palettes).layout(id("layout"), "layout", json(text));
    }

    private static WidgetDef root(UiLayout layout) {
        return unwrap(layout.root());
    }

    private static WidgetDef unwrap(WidgetDef def) {
        return def instanceof UiExpander.Located located ? unwrap(located.def()) : def;
    }

    @Test
    void parameterStaysOneOperand() {
        component("shift", """
                {"params": {"y": "num"}, "root": {"type": "place", "y": "$y + 8", "child": {"type": "rect", "w": 1, "h": 1}}}""");
        Place spliced = (Place) root(layout("""
                {"root": {"type": "test:shift", "y": "on ? 1 : 2"}}"""));
        assertEquals("(on ? 1 : 2) + 8", spliced.y());
        Place atom = (Place) root(layout("""
                {"root": {"type": "test:shift", "y": 12}}"""));
        assertEquals("12 + 8", atom.y());
    }

    @Test
    void argumentsResolveInTheCallersScope() {
        component("label", """
                {"constants": {"c": "#ff0000"}, "params": {"text": "text", "color": "color"},
                 "root": {"type": "text", "text": "[$text]", "color": "$color"}}""");
        palettes.put(id("p"), json("""
                {"c": "#00ff00"}"""));
        Text text = (Text) root(layout("""
                {"use": ["test:p"], "root": {"type": "test:label", "text": "{a.b} km", "color": "$c"}}"""));
        assertEquals("[{a.b} km]", text.text());
        assertEquals("#00ff00", text.color());
    }

    @Test
    void widgetParametersSplice() {
        component("pair", """
                {"params": {"items": "widgets", "head": "widget"},
                 "root": {"type": "column", "gap": "$gap * 2", "children": ["$head", "$items"]},
                 "constants": {"gap": 3}}""");
        Flow column = (Flow) root(layout("""
                {"root": {"type": "test:pair", "head": {"type": "stack"},
                  "items": [{"type": "rect", "w": 1, "h": 1}, {"type": "rect", "w": 2, "h": 2}]}}"""));
        assertEquals(6, column.gap());
        assertEquals(3, column.children().size());
        assertInstanceOf(Stack.class, unwrap(column.children().get(0)));
    }

    @Test
    void scalingDefaultsRelative() {
        assertEquals(UiLayout.DEFAULT_REFERENCE, layout("""
                {"root": {"type": "stack"}}""").reference());
        assertEquals(0, layout("""
                {"scaling": "absolute", "root": {"type": "stack"}}""").reference());
        assertEquals(480, layout("""
                {"reference": 480, "root": {"type": "stack"}}""").reference());
    }

    @Test
    void errorsNameTheLocation() {
        component("loop", """
                {"root": {"type": "test:loop"}}""");
        UiJsonException loop = assertThrows(UiJsonException.class, () -> layout("""
                {"root": {"type": "stack", "children": [{"type": "test:loop"}]}}"""));
        assertTrue(loop.getMessage().contains("includes itself"), loop.getMessage());
        UiJsonException typo = assertThrows(UiJsonException.class, () -> layout("""
                {"root": {"type": "rect", "w": 1, "h": 1, "colour": "#fff"}}"""));
        assertTrue(typo.getMessage().startsWith("layout/root/colour: unknown property"), typo.getMessage());
        UiJsonException unknown = assertThrows(UiJsonException.class, () -> layout("""
                {"root": {"type": "text", "text": "$nope"}}"""));
        assertTrue(unknown.getMessage().contains("unknown `$nope`"), unknown.getMessage());
        UiJsonException missing = assertThrows(UiJsonException.class, () -> layout("""
                {"root": {"type": "test:loop2"}}"""));
        assertTrue(missing.getMessage().contains("unknown widget type or component"), missing.getMessage());
    }

    @Test
    void dollarEscapes() {
        Text text = (Text) root(layout("""
                {"root": {"type": "text", "text": "$$5"}}"""));
        assertEquals("$5", text.text());
        assertEquals(List.of(), ((Stack) root(layout("""
                {"root": {"type": "stack"}}"""))).children());
    }

    @Test
    void buttonResolvesNamedAction() {
        UiAction action = item -> { };
        UiActions.register(id("press"), action);
        Button button = (Button) root(layout("""
                {"root": {"type": "button", "action": "test:press", "background": "#ff000000", "pad": 2,
                          "child": {"type": "text", "text": "GO"}}}"""));
        assertEquals(action, button.action());
        assertEquals("#ff000000", button.hover());
        assertEquals(2, button.padX());
        UiJsonException missing = assertThrows(UiJsonException.class, () -> layout("""
                {"root": {"type": "button", "action": "test:absent"}}"""));
        assertTrue(missing.getMessage().startsWith("layout/root/action: no UI handler"), missing.getMessage());
    }

    @Test
    void buttonHoverBorderDefaultsToItsBorder() {
        UiActions.register(id("hb"), (UiAction) item -> { });
        Button plain = (Button) root(layout("""
                {"root": {"type": "button", "action": "test:hb", "border": "#ff606060", "border_width": 1.5}}"""));
        assertEquals("#ff606060", plain.hoverBorder());
        assertEquals(1.5F, plain.hoverBorderWidth());
        Button swapped = (Button) root(layout("""
                {"root": {"type": "button", "action": "test:hb", "border": "#ff606060",
                          "hover_border": "#ffffdd55", "hover_border_width": 2}}"""));
        assertEquals("#ffffdd55", swapped.hoverBorder());
        assertEquals(2, swapped.hoverBorderWidth());
    }

    @Test
    void tooltipItemAndSkinPartResolveHandlers() {
        UiTooltip lines = item -> List.of();
        UiStack stack = item -> null;
        UiActions.register(id("tip"), lines);
        UiActions.register(id("stack"), stack);
        Tooltip tip = (Tooltip) root(layout("""
                {"root": {"type": "tooltip", "lines": "test:tip",
                          "child": {"type": "item", "stack": "test:stack", "size": 24, "decorations": true}}}"""));
        assertEquals(lines, tip.lines());
        Item item = (Item) unwrap(tip.child());
        assertEquals(stack, item.stack());
        assertEquals(24, item.size());
        assertTrue(item.decorations());
        SkinPart part = (SkinPart) root(layout("""
                {"root": {"type": "skin_part", "part": "left_arm", "health": "0.5", "w": 8, "h": 24}}"""));
        assertEquals(SkinPart.Part.LEFT_ARM, part.part());
        assertEquals("-1", part.entity());
    }
}
