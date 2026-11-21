package com.wf.wflib.core.client;

import com.wf.wflib.core.WFLibMod;
import com.wf.wflib.core.api.Features;
import com.wf.wflib.core.api.WFSystems;
import com.wf.wflib.core.features.FeatureCatalogue;
import com.wf.wflib.core.features.FeatureCatalogue.Feature;
import com.wf.wflib.core.features.FeatureCatalogue.Group;
import com.wf.wflib.core.features.FeatureConfig;
import com.wf.wflib.core.features.FeatureGraph;
import net.minecraft.ChatFormatting;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.ContainerObjectSelectionList;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.narration.NarratableEntry;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.neoforged.fml.loading.FMLPaths;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * The master switchboard: a big checkmark per mainline feature and smaller ones under it for the parts. Ticking a box ticks what
 * it needs; unticking one unticks what needs it. Systems are read once at startup, so what is shown here is what the next launch
 * will do; the header says so when it differs from what is running. Reached from Mods > WFLib > Config and {@code /wflibfeatures}.
 */
public final class FeaturesScreen extends Screen {
    private static final int GREEN = 0xFF55FF55, WHITE = 0xFFFFFFFF, GREY = 0xFF8A8A8A, DIM = 0xFF555555, AMBER = 0xFFFFC857;

    private final Screen parent;
    /** wflib's groups, then each registered source's, shown as groups of the same shape. */
    private final List<Group> groups = new ArrayList<>(FeatureCatalogue.GROUPS);
    private final Map<String, com.wf.wflib.core.api.FeatureSource> sourceOf = new HashMap<>();
    private final FeatureGraph graph = FeatureGraph.of(WFLibMod.SYSTEMS);
    /** The edited state: {@code <group>.enabled} and feature ids. */
    private final Map<String, Boolean> state = new HashMap<>(FeatureConfig.read());
    private FeatureList list;

    public FeaturesScreen(Screen parent) {
        super(Component.literal("WFLib features"));
        this.parent = parent;
        for (var source : Features.sources()) {
            List<Feature> boxes = new ArrayList<>();
            for (var box : source.boxes()) {
                boxes.add(new Feature(box.id(), box.title(), box.blurb(), true, null, null, List.of()));
                state.put(box.id(), source.get(box.id()));
            }
            groups.add(new Group(source.id(), source.title(), source.blurb(), boxes));
            state.put(source.id() + ".enabled", source.master());
            sourceOf.put(source.id(), source);
        }
    }

    // ---------------------------------------------------------------- model

    private boolean master(Group g) {
        return state.getOrDefault(g.id() + ".enabled", true);
    }

    private boolean ticked(Feature f) {
        return state.getOrDefault(f.id(), f.defaultOn());
    }

    /** On whatever the file says, because an installed mod (or another feature) needs it: the game turned it on at launch. */
    private boolean locked(Feature f) {
        if (f.system() == null || Features.startup().isEmpty() || Features.configuredOn(Features.startup(), f)) return false;
        try {
            return WFSystems.enabled(f.system());
        } catch (RuntimeException e) {
            return false;
        }
    }

    private boolean effective(Group g, Feature f) {
        return locked(f) || master(g) && ticked(f);
    }

    private void toggle(Group g, Feature f) {
        if (locked(f)) return;
        if (ticked(f) && master(g)) {
            state.put(f.id(), false);
            for (String dep : graph.dependents(f.id())) state.put(dep, false);
        } else {
            state.put(f.id(), true);
            state.put(g.id() + ".enabled", true);
            for (String need : graph.needs(f.id())) {
                state.put(need, true);
                state.put(FeatureCatalogue.groupOf(need).id() + ".enabled", true);
            }
        }
    }

    private void toggle(Group g) {
        boolean now = !master(g);
        state.put(g.id() + ".enabled", now);
        if (!now) {
            for (Feature f : g.features()) for (String dep : graph.dependents(f.id())) state.put(dep, false);
        }
    }

    /** Whether what is ticked now differs from what the game started with (a locked box is not the player's choice). */
    private boolean changed() {
        Map<String, Boolean> started = Features.startup();
        for (Group g : groups) {
            var source = sourceOf.get(g.id());
            for (Feature f : g.features()) {
                boolean will = master(g) && ticked(f);
                if (source != null) {
                    if ((source.startupMaster() && source.startup(f.id())) != will) return true;
                } else if (!started.isEmpty() && Features.configuredOn(started, f) != will) {
                    return true;
                }
            }
        }
        return false;
    }

    private void save() {
        for (Group g : groups) {
            var source = sourceOf.get(g.id());
            if (source != null) {
                source.master(master(g));
                for (Feature f : g.features()) source.set(f.id(), ticked(f));
                source.save();
                continue;
            }
            FeatureConfig.set(g, null, master(g));
            for (Feature f : g.features()) FeatureConfig.set(g, f, ticked(f));
        }
        FeatureConfig.save();
    }

    // ---------------------------------------------------------------- screen

    @Override
    protected void init() {
        int bottom = height - 28;
        list = new FeatureList(minecraft, width, bottom - 52, 34, 22);
        for (Group g : groups) {
            list.add(new GroupRow(g));
            for (Feature f : g.features()) list.add(new FeatureRow(g, f));
        }
        addRenderableWidget(list);
        int bw = Math.min(150, (width - 30) / 3);
        int x = width / 2 - (bw * 3 + 8) / 2;
        addRenderableWidget(Button.builder(CommonComponents.GUI_DONE, b -> onClose()).bounds(x, height - 24, bw, 20).build());
        addRenderableWidget(Button.builder(Component.literal("Reset to defaults"), b -> {
            state.clear();
            for (Group g : groups) {
                state.put(g.id() + ".enabled", true);
                for (Feature f : g.features()) state.put(f.id(), f.defaultOn());
            }
        }).bounds(x + bw + 4, height - 24, bw, 20).build());
        addRenderableWidget(Button.builder(Component.literal("Open config file"), b ->
                Util.getPlatform().openPath(FMLPaths.CONFIGDIR.get().resolve(FeatureConfig.FILE))).bounds(x + (bw + 4) * 2, height - 24, bw, 20).build());
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partial) {
        super.render(g, mouseX, mouseY, partial);
        g.drawCenteredString(font, title, width / 2, 8, WHITE);
        int groupsOn = (int) groups.stream().filter(this::master).count();
        long on = groups.stream().flatMap(gr -> gr.features().stream().filter(f -> effective(gr, f))).count();
        g.drawCenteredString(font, Component.literal(on + " of " + groups.stream().mapToInt(gr -> gr.features().size()).sum() + " features on, " + groupsOn + " of " + groups.size() + " groups"), width / 2, 20, GREY);
        if (changed()) {
            g.drawCenteredString(font, Component.literal("Restart the game to apply the changes you made").withStyle(ChatFormatting.GOLD), width / 2, height - 40, AMBER);
        } else {
            g.drawCenteredString(font, Component.literal("Click a big box for a whole group, a small one for a single feature"), width / 2, height - 40, DIM);
        }
    }

    @Override
    public void onClose() {
        save();
        minecraft.setScreen(parent);
    }

    // ---------------------------------------------------------------- list

    private final class FeatureList extends ContainerObjectSelectionList<Row> {
        FeatureList(Minecraft mc, int width, int height, int y, int itemHeight) {
            super(mc, width, height, y, itemHeight);
        }

        void add(Row r) {
            addEntry(r);
        }

        @Override
        public int getRowWidth() {
            return Math.min(width - 40, 520);
        }

        @Override
        protected int getScrollbarPosition() {
            return width / 2 + getRowWidth() / 2 + 8;
        }
    }

    private abstract class Row extends ContainerObjectSelectionList.Entry<Row> {
        int boxX, boxY, boxSize;

        @Override
        public List<? extends GuiEventListener> children() {
            return List.of();
        }

        @Override
        public List<? extends NarratableEntry> narratables() {
            return List.of();
        }

        abstract void click();

        @Override
        public boolean mouseClicked(double mx, double my, int button) {
            if (button != 0) return false;
            click();
            return true;
        }

        void box(GuiGraphics g, int x, int y, int size, boolean on, boolean partial, boolean dim) {
            boxX = x;
            boxY = y;
            boxSize = size;
            int edge = dim ? DIM : WHITE;
            g.fill(x, y, x + size, y + size, edge);
            g.fill(x + 1, y + 1, x + size - 1, y + size - 1, 0xFF101010);
            int c = dim ? DIM : GREEN;
            if (partial) {
                g.fill(x + 3, y + size / 2 - 1, x + size - 3, y + size / 2 + 1, c);
            } else if (on) {
                int n1 = Math.max(2, size / 5), n2 = size / 2 - 2, t = 2;
                int sx = x + 3, sy = y + size / 2 - 1;
                for (int i = 0; i < n1; i++) g.fill(sx + i, sy + i, sx + i + t, sy + i + t, c);
                for (int i = 0; i < n2; i++) g.fill(sx + n1 + i, sy + n1 - i, sx + n1 + i + t, sy + n1 - i + t, c);
            }
        }
    }

    private final class GroupRow extends Row {
        private final Group group;

        GroupRow(Group group) {
            this.group = group;
        }

        @Override
        void click() {
            toggle(group);
        }

        @Override
        public void render(GuiGraphics g, int index, int top, int left, int width, int height, int mx, int my, boolean hover, float partial) {
            boolean on = master(group);
            long ticked = group.features().stream().filter(f -> effective(group, f)).count();
            boolean partial2 = on && ticked < group.features().size();
            if (hover) g.fill(left - 2, top, left + width + 2, top + height, 0x30FFFFFF);
            box(g, left + 2, top + 2, 18, on && ticked > 0, partial2 || (on && ticked == 0), !on);
            g.drawString(font, Component.literal(group.title()).withStyle(ChatFormatting.BOLD), left + 28, top + 3, on ? WHITE : GREY);
            String count = on ? ticked + " / " + group.features().size() : "off";
            g.drawString(font, count, left + width - font.width(count) - 4, top + 3, on ? GREEN : DIM);
            g.drawString(font, font.plainSubstrByWidth(group.blurb(), width - 40), left + 28, top + 13, GREY);
        }
    }

    private final class FeatureRow extends Row {
        private final Group group;
        private final Feature feature;

        FeatureRow(Group group, Feature feature) {
            this.group = group;
            this.feature = feature;
        }

        @Override
        void click() {
            toggle(group, feature);
        }

        private List<String> notes() {
            List<String> out = new ArrayList<>();
            if (!feature.apps().isEmpty()) out.add("PDA: " + feature.apps().stream().map(FeaturesScreen::appName).collect(Collectors.joining(", ")));
            var needs = graph.direct(feature.id());
            if (!needs.isEmpty()) out.add("needs " + needs.stream().map(id -> FeatureCatalogue.byId(id).title()).collect(Collectors.joining(", ")));
            if (locked(feature)) out.add("needed by an installed mod");
            else if (feature.system() != null || sourceOf.containsKey(group.id())) out.add("restart");
            return out;
        }

        @Override
        public void render(GuiGraphics g, int index, int top, int left, int width, int height, int mx, int my, boolean hover, float partial) {
            boolean on = effective(group, feature);
            boolean parentOn = master(group);
            if (hover) g.fill(left - 2, top, left + width + 2, top + height, 0x20FFFFFF);
            box(g, left + 24, top + 4, 12, on, false, !parentOn);
            int color = on ? WHITE : parentOn ? GREY : DIM;
            g.drawString(font, feature.title(), left + 42, top + 3, color);
            String note = String.join("  |  ", notes());
            int nx = left + 42 + font.width(feature.title()) + 10;
            g.drawString(font, font.plainSubstrByWidth(note, Math.max(0, left + width - nx)), nx, top + 3, DIM);
            if (hover) setTooltipForNextRenderPass(Component.literal(feature.blurb()));
        }
    }

    private static String appName(String id) {
        String p = id.substring(id.indexOf(':') + 1);
        return Character.toUpperCase(p.charAt(0)) + p.substring(1);
    }
}
