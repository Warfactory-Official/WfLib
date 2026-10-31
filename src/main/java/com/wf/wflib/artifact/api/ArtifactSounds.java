package com.wf.wflib.artifact.api;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import com.wf.wflib.core.api.WFLib;

/** Detector and bolt sounds. */
public final class ArtifactSounds {
    private static final DeferredRegister<SoundEvent> SOUND_EVENTS =
            DeferredRegister.create(Registries.SOUND_EVENT, WFLib.MODID);

    /** Burnt fuzz blowout, drawn by the zone artefact it hatches from; the anomaly of that name shares it. */
    public static final DeferredHolder<SoundEvent, SoundEvent> ANOMALY_BFUZZ_BLOWOUT = register("anomaly.bfuzz_blowout");
    public static final DeferredHolder<SoundEvent, SoundEvent> DETECTOR_ECHO_BEEP = register("detector.echo_beep");
    public static final DeferredHolder<SoundEvent, SoundEvent> DETECTOR_BEAR_BEEP = register("detector.bear_beep");
    public static final DeferredHolder<SoundEvent, SoundEvent> DETECTOR_DRAW = register("detector.draw");
    public static final DeferredHolder<SoundEvent, SoundEvent> DETECTOR_HOLSTER = register("detector.holster");
    static final DeferredHolder<SoundEvent, SoundEvent> BOLT_IMPACT_CONCRETE = register("bolt.impact.concrete");
    static final DeferredHolder<SoundEvent, SoundEvent> BOLT_IMPACT_DEFAULT = register("bolt.impact.default");
    static final DeferredHolder<SoundEvent, SoundEvent> BOLT_IMPACT_DIRT = register("bolt.impact.dirt");
    static final DeferredHolder<SoundEvent, SoundEvent> BOLT_IMPACT_METAL = register("bolt.impact.metal");
    static final DeferredHolder<SoundEvent, SoundEvent> BOLT_IMPACT_WATER = register("bolt.impact.water");

    private ArtifactSounds() {
    }

    private static DeferredHolder<SoundEvent, SoundEvent> register(String name) {
        return SOUND_EVENTS.register(name,
                () -> SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath(WFLib.MODID, name)));
    }

    public static void register(IEventBus modBus) {
        SOUND_EVENTS.register(modBus);
    }
}
