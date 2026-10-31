package com.wf.wflib.grenade.api;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.entity.EntityType;
import net.neoforged.neoforge.registries.DeferredHolder;
import com.wf.wflib.core.api.WFLib;

/** Hand-charge entity type + sounds; registered by {@code GrenadeSystem}. */
public final class Grenades {

    public static final DeferredHolder<EntityType<?>, EntityType<ThrownCharge>> CHARGE = DeferredHolder.create(
            Registries.ENTITY_TYPE, ResourceLocation.fromNamespaceAndPath(WFLib.MODID, "thrown_charge"));

    public static final DeferredHolder<SoundEvent, SoundEvent> BOUNCE = sound("grenade.bounce");
    public static final DeferredHolder<SoundEvent, SoundEvent> PIN = sound("grenade.pin");
    public static final DeferredHolder<SoundEvent, SoundEvent> SPOON = sound("grenade.spoon");
    public static final DeferredHolder<SoundEvent, SoundEvent> FLASHBANG = sound("grenade.flashbang");
    public static final DeferredHolder<SoundEvent, SoundEvent> FLASHBANG_FAR = sound("grenade.flashbang_far");
    public static final DeferredHolder<SoundEvent, SoundEvent> THERMITE_IGNITE = sound("grenade.thermite_ignite");
    public static final DeferredHolder<SoundEvent, SoundEvent> THERMITE_LOOP = sound("grenade.thermite_loop");
    public static final DeferredHolder<SoundEvent, SoundEvent> SMOKE_HISS = sound("grenade.smoke_hiss");
    public static final DeferredHolder<SoundEvent, SoundEvent> TINNITUS = sound("grenade.tinnitus");

    private Grenades() {
    }

    private static DeferredHolder<SoundEvent, SoundEvent> sound(String path) {
        return DeferredHolder.create(Registries.SOUND_EVENT, ResourceLocation.fromNamespaceAndPath(WFLib.MODID, path));
    }
}
