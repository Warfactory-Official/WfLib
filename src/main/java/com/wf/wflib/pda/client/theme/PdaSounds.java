package com.wf.wflib.pda.client.theme;

import com.wf.wflib.WFLib;
import com.wf.wflib.WFSounds;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** The PDA's two bundled UI sounds, through Minecraft's sound engine and master volume. */
@EventBusSubscriber(modid = WFLib.MODID, value = Dist.CLIENT)
public final class PdaSounds {
    public enum Event { HOVER, CLICK }
    // One-line mapping switch: false = hit.wav click / beep.wav hover; true swaps them.
    private static final boolean SWAP_CLICK_AND_HOVER = false;
    private static Event pending;
    private static long lastUi, lastHover;
    private static final List<SoundInstance> owned = new ArrayList<>();
    private static final boolean AUDIT = Boolean.getBoolean("wflib.pda.soundAudit");
    private static final List<Map<String, Object>> receipts = new ArrayList<>();
    private PdaSounds() { }

    public static void request(Event event) {
        if (!PdaThemeState.sounds() || Minecraft.getInstance().player == null) return;
        if (pending == null || event.ordinal() >= pending.ordinal()) pending = event;
    }
    /** Navigation has the same click feedback as every other control. */
    public static void tab() { request(Event.CLICK); }
    @SubscribeEvent
    public static void tick(ClientTickEvent.Post event) {
        var manager = Minecraft.getInstance().getSoundManager();
        if (!PdaThemeState.sounds() || Minecraft.getInstance().player == null) {
            pending = null;
            owned.forEach(manager::stop); owned.clear(); return;
        }
        owned.removeIf(sound -> !manager.isActive(sound));
        long now = Util.getMillis();
        if (pending != null) {
            Event next = pending; pending = null;
            if (next == Event.HOVER) {
                // Dropped entries are never replayed later while the pointer rests.
                if (now - lastHover >= 140 && now - lastUi >= 140) { lastHover = now; play(next); }
            } else if (now - lastUi >= 50) { lastUi = now; play(next); }
        }
    }
    private static void play(Event event) {
        boolean hover = (event == Event.HOVER) != SWAP_CLICK_AND_HOVER;
        var instance = SimpleSoundInstance.forUI((hover ? WFSounds.PDA_HOVER : WFSounds.PDA_CLICK).get(), 1, .35f);
        owned.add(instance);
        Minecraft.getInstance().getSoundManager().play(instance);
        if (AUDIT) {
            if (receipts.size() == 256) receipts.removeFirst();
            receipts.add(Map.of("event", event.name().toLowerCase(Locale.ROOT), "time_ms", Util.getMillis(), "route", "builtin"));
        }
    }
    public static void resetDiagnostics() { if (AUDIT) receipts.clear(); }
    public static Map<String, Object> diagnostics() { return Map.of("events", List.copyOf(receipts)); }
}
