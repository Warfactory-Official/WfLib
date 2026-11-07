package com.wf.wflib.soldier.client;

import com.wf.wflib.soldier.VoicePackets;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.CycleButton;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.ArrayList;
import java.util.List;

/** Pick the voice of allies and enemies: a pack and, optionally, one speaker of it; changes apply at once. */
public final class VoiceScreen extends Screen {
    private static final String NONE = "";

    private final VoicePackets.Menu menu;

    private VoiceScreen(VoicePackets.Menu menu) {
        super(Component.translatable("wflib.voices.title"));
        this.menu = menu;
    }

    /** Opens the screen, or refreshes it when the server re-sends the state. */
    public static void show(VoicePackets.Menu menu) {
        Minecraft.getInstance().setScreen(new VoiceScreen(menu));
    }

    @Override
    protected void init() {
        int top = height / 2 - 48;
        row(top, true, menu.allies());
        row(top + 52, false, menu.enemies());
        addRenderableWidget(Button.builder(CommonComponents.GUI_DONE, b -> onClose())
                .bounds(width / 2 - 100, top + 112, 200, 20).build());
    }

    private void row(int y, boolean allies, String binding) {
        int slash = binding.indexOf('/');
        String named = slash < 0 ? binding : binding.substring(0, slash);
        List<String> packs = new ArrayList<>(List.of(NONE));
        menu.packs().forEach(p -> packs.add(p.id()));
        String pack = packs.contains(named) ? named : NONE;
        List<String> speakers = new ArrayList<>(List.of(NONE));
        menu.packs().stream().filter(p -> p.id().equals(pack)).findFirst().ifPresent(p -> speakers.addAll(p.speakers()));
        String pinned = slash < 0 ? NONE : binding.substring(slash + 1);
        String speaker = speakers.contains(pinned) ? pinned : NONE;

        int left = width / 2 - 155;
        addRenderableWidget(CycleButton.<String>builder(id -> id.isEmpty() ? Component.translatable("wflib.voices.silent") : Component.literal(id))
                .displayOnlyValue().withValues(packs).withInitialValue(pack)
                .create(left, y + 14, 150, 20, Component.empty(), (button, id) -> choose(allies, id, NONE)));
        CycleButton<String> speakerButton = addRenderableWidget(CycleButton.<String>builder(
                        id -> id.isEmpty() ? Component.translatable("wflib.voices.auto") : Component.literal(id))
                .displayOnlyValue().withValues(speakers).withInitialValue(speaker)
                .create(left + 154, y + 14, 106, 20, Component.empty(),
                        (button, id) -> choose(allies, pack, id)));
        speakerButton.active = !pack.isEmpty();
        addRenderableWidget(Button.builder(Component.translatable("wflib.voices.sample"), b ->
                        PacketDistributor.sendToServer(new VoicePackets.Sample(current(allies))))
                .bounds(left + 264, y + 14, 46, 20).build()).active = !pack.isEmpty();
    }

    private String current(boolean forAllies) {
        return forAllies ? menu.allies() : menu.enemies();
    }

    /** Tells the server, and redraws from the same state: the server only answers if it refuses. */
    private void choose(boolean forAllies, String pack, String speaker) {
        String binding = speaker.isEmpty() ? pack : pack + "/" + speaker;
        PacketDistributor.sendToServer(new VoicePackets.Set(forAllies, binding));
        VoicePackets.Menu next = new VoicePackets.Menu(menu.packs(), forAllies ? binding : menu.allies(), forAllies ? menu.enemies() : binding);
        Minecraft.getInstance().setScreen(new VoiceScreen(next));
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        graphics.drawCenteredString(font, title, width / 2, height / 2 - 76, 0xFFFFFF);
        int left = width / 2 - 155, top = height / 2 - 48;
        graphics.drawString(font, Component.translatable("wflib.voices.allies"), left, top + 2, 0xA0A0A0);
        graphics.drawString(font, Component.translatable("wflib.voices.enemies"), left, top + 54, 0xA0A0A0);
        if (menu.packs().isEmpty()) {
            graphics.drawCenteredString(font, Component.translatable("wflib.voices.none_installed"), width / 2, top + 90, 0xFF8080);
        }
    }
}
