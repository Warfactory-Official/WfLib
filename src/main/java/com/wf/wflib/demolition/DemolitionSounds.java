package com.wf.wflib.demolition;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import com.wf.wflib.audio.api.SoundSpec;
import net.minecraft.sounds.SoundEvent;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.List;
import com.wf.wflib.core.api.WFLib;

/** Mining charge and detonator sounds. */
public final class DemolitionSounds {
    private static final DeferredRegister<SoundEvent> SOUND_EVENTS =
            DeferredRegister.create(Registries.SOUND_EVENT, WFLib.MODID);

    public static final DeferredHolder<SoundEvent, SoundEvent> MINING_CHARGE_BLAST = register("mining_charge_blast");
    public static final DeferredHolder<SoundEvent, SoundEvent> MINING_CHARGE_BLAST_DISTANT = register("mining_charge_blast_distant");
    public static final DeferredHolder<SoundEvent, SoundEvent> DETONATOR_ARM = register("detonator_arm");
    public static final DeferredHolder<SoundEvent, SoundEvent> DETONATOR_DETONATE = register("detonator_detonate");

    /** Sharp report close, recorded rumble past 24 blocks. */
    public static List<SoundSpec> blast() {
        return List.of(
                new SoundSpec(MINING_CHARGE_BLAST.getId(), 1, 1, 0, 48, 0),
                new SoundSpec(MINING_CHARGE_BLAST_DISTANT.getId(), 1, 1, 0, 200, 0, 24));
    }

    private DemolitionSounds() {
    }

    private static DeferredHolder<SoundEvent, SoundEvent> register(String name) {
        return SOUND_EVENTS.register(name,
                () -> SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath(WFLib.MODID, name)));
    }

    public static void register(IEventBus modBus) {
        SOUND_EVENTS.register(modBus);
    }
}
