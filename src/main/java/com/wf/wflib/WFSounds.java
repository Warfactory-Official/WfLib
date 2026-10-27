package com.wf.wflib;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class WFSounds {

    public static final DeferredRegister<SoundEvent> SOUND_EVENTS =
            DeferredRegister.create(Registries.SOUND_EVENT, WFLib.MODID);

    public static final DeferredHolder<SoundEvent, SoundEvent> NUCLEAR_EXPLOSION = register("weapon.nuclear_explosion");
    public static final DeferredHolder<SoundEvent, SoundEvent> PDA_CLICK = register("pda.click");
    public static final DeferredHolder<SoundEvent, SoundEvent> PDA_HOVER = register("pda.hover");
    public static final DeferredHolder<SoundEvent, SoundEvent> FIRE_DISINTEGRATION = register("weapon.fire.disintegration");
    public static final DeferredHolder<SoundEvent, SoundEvent> EXPLOSION_SMALL_NEAR = register("weapon.explosion_small_near");
    public static final DeferredHolder<SoundEvent, SoundEvent> EXPLOSION_SMALL_FAR = register("weapon.explosion_small_far");
    public static final DeferredHolder<SoundEvent, SoundEvent> EXPLOSION_LARGE_NEAR = register("weapon.explosion_large_near");
    public static final DeferredHolder<SoundEvent, SoundEvent> EXPLOSION_LARGE_FAR = register("weapon.explosion_large_far");
    public static final DeferredHolder<SoundEvent, SoundEvent> SONIC_BOOM = register("weapon.sonic_boom");
    public static final DeferredHolder<SoundEvent, SoundEvent> MISSILE_FLIGHT = register("weapon.missile_flight");
    /** A quadcopter holding an unladen hover, looped. */
    public static final DeferredHolder<SoundEvent, SoundEvent> DRONE_ROTOR_LOOP = register("drone.rotor_loop");

    /** The demolition set, moved across with the mining charges themselves. */
    public static final DeferredHolder<SoundEvent, SoundEvent> MINING_CHARGE_BLAST =
            register("mining_charge_blast");
    public static final DeferredHolder<SoundEvent, SoundEvent> MINING_CHARGE_BLAST_DISTANT =
            register("mining_charge_blast_distant");
    public static final DeferredHolder<SoundEvent, SoundEvent> DETONATOR_ARM = register("detonator_arm");
    public static final DeferredHolder<SoundEvent, SoundEvent> DETONATOR_DETONATE =
            register("detonator_detonate");

    public static final DeferredHolder<SoundEvent, SoundEvent> SMOKE_POP = register("countermeasure.smoke_pop");
    public static final DeferredHolder<SoundEvent, SoundEvent> FLARE_BURN = register("countermeasure.flare_burn");

    /** STALKER's anomaly sounds, as their zone configs name them (tools/xray-assets.py; third-party, internal only). */
    public static final DeferredHolder<SoundEvent, SoundEvent> ANOMALY_GRAVI_IDLE01 = register("anomaly.gravi_idle01");
    public static final DeferredHolder<SoundEvent, SoundEvent> ANOMALY_ANOMALY_MINCER_BLOWOUT = register("anomaly.anomaly_mincer_blowout");
    public static final DeferredHolder<SoundEvent, SoundEvent> ANOMALY_ANOMALY_GRAVY_HIT1 = register("anomaly.anomaly_gravy_hit1");
    public static final DeferredHolder<SoundEvent, SoundEvent> ANOMALY_ANOMALY_BODY_TEAR_1 = register("anomaly.anomaly_body_tear_1");
    public static final DeferredHolder<SoundEvent, SoundEvent> ANOMALY_GRAVI_IDLE00 = register("anomaly.gravi_idle00");
    public static final DeferredHolder<SoundEvent, SoundEvent> ANOMALY_GRAVI_BLOWOUT6 = register("anomaly.gravi_blowout6");
    public static final DeferredHolder<SoundEvent, SoundEvent> ANOMALY_GRAVI_RUMBLE1 = register("anomaly.gravi_rumble1");
    public static final DeferredHolder<SoundEvent, SoundEvent> ANOMALY_ANOMALY_GRAVY_BLAST1 = register("anomaly.anomaly_gravy_blast1");
    public static final DeferredHolder<SoundEvent, SoundEvent> ANOMALY_ELECTRA_IDLE1 = register("anomaly.electra_idle1");
    public static final DeferredHolder<SoundEvent, SoundEvent> ANOMALY_ELECTRA_BLAST1 = register("anomaly.electra_blast1");
    public static final DeferredHolder<SoundEvent, SoundEvent> ANOMALY_ELECTRA_HIT = register("anomaly.electra_hit");
    public static final DeferredHolder<SoundEvent, SoundEvent> ANOMALY_FIRE_IDLE = register("anomaly.fire_idle");
    public static final DeferredHolder<SoundEvent, SoundEvent> ANOMALY_ZHAR_BLOW = register("anomaly.zhar_blow");
    public static final DeferredHolder<SoundEvent, SoundEvent> ANOMALY_ZHAR = register("anomaly.zhar");
    public static final DeferredHolder<SoundEvent, SoundEvent> ANOMALY_BUZZ_IDLE = register("anomaly.buzz_idle");
    public static final DeferredHolder<SoundEvent, SoundEvent> ANOMALY_BUZZ_HIT = register("anomaly.buzz_hit");
    public static final DeferredHolder<SoundEvent, SoundEvent> ANOMALY_STEAM = register("anomaly.steam");
    public static final DeferredHolder<SoundEvent, SoundEvent> ANOMALY_STEAM_BLOWOUT = register("anomaly.steam_blowout");
    public static final DeferredHolder<SoundEvent, SoundEvent> ANOMALY_FLASH_IDLE = register("anomaly.flash_idle");
    public static final DeferredHolder<SoundEvent, SoundEvent> ANOMALY_FLASH_SHIELD = register("anomaly.flash_shield");
    public static final DeferredHolder<SoundEvent, SoundEvent> ANOMALY_GHOST_IDLE = register("anomaly.ghost_idle");
    public static final DeferredHolder<SoundEvent, SoundEvent> ANOMALY_GHOST_BLOWOUT = register("anomaly.ghost_blowout");
    public static final DeferredHolder<SoundEvent, SoundEvent> ANOMALY_NET_IDLE = register("anomaly.net_idle");
    public static final DeferredHolder<SoundEvent, SoundEvent> ANOMALY_NET_BLOWOUT = register("anomaly.net_blowout");
    public static final DeferredHolder<SoundEvent, SoundEvent> ANOMALY_NET_SHIELD = register("anomaly.net_shield");
    public static final DeferredHolder<SoundEvent, SoundEvent> ANOMALY_POINT_IDLE = register("anomaly.point_idle");
    public static final DeferredHolder<SoundEvent, SoundEvent> ANOMALY_POINT_BLOWOUT = register("anomaly.point_blowout");
    public static final DeferredHolder<SoundEvent, SoundEvent> ANOMALY_GOLD_IDLE = register("anomaly.gold_idle");
    public static final DeferredHolder<SoundEvent, SoundEvent> ANOMALY_GOLD_BLOWOUT = register("anomaly.gold_blowout");
    public static final DeferredHolder<SoundEvent, SoundEvent> ANOMALY_GREEN_DRAGON_IDLE = register("anomaly.green_dragon_idle");
    public static final DeferredHolder<SoundEvent, SoundEvent> ANOMALY_GREEN_DRAGON_BLOWOUT = register("anomaly.green_dragon_blowout");
    public static final DeferredHolder<SoundEvent, SoundEvent> ANOMALY_SPHERE_IDLE = register("anomaly.sphere_idle");
    public static final DeferredHolder<SoundEvent, SoundEvent> ANOMALY_SPHERE_BLOWOUT = register("anomaly.sphere_blowout");
    public static final DeferredHolder<SoundEvent, SoundEvent> ANOMALY_MEFISTOTEL_IDLE = register("anomaly.mefistotel_idle");
    public static final DeferredHolder<SoundEvent, SoundEvent> ANOMALY_MEFISTOTEL_BLOWOUT = register("anomaly.mefistotel_blowout");
    public static final DeferredHolder<SoundEvent, SoundEvent> ANOMALY_MEFISTOTEL_SHIELD = register("anomaly.mefistotel_shield");
    public static final DeferredHolder<SoundEvent, SoundEvent> ANOMALY_BFUZZ_BLOWOUT = register("anomaly.bfuzz_blowout");
    public static final DeferredHolder<SoundEvent, SoundEvent> ANOMALY_BFUZZ_HIT = register("anomaly.bfuzz_hit");
    public static final DeferredHolder<SoundEvent, SoundEvent> ANOMALY_FIREBALL_IDLE = register("anomaly.fireball_idle");
    public static final DeferredHolder<SoundEvent, SoundEvent> ANOMALY_FIREBALL_BLOW = register("anomaly.fireball_blow");
    public static final DeferredHolder<SoundEvent, SoundEvent> ANOMALY_FIRE2 = register("anomaly.fire2");
    public static final DeferredHolder<SoundEvent, SoundEvent> ANOMALY_RND_FLY1 = register("anomaly.rnd_fly1");
    public static final DeferredHolder<SoundEvent, SoundEvent> ANOMALY_RND_FLY2 = register("anomaly.rnd_fly2");

    private WFSounds() {
    }

    private static DeferredHolder<SoundEvent, SoundEvent> register(String name) {
        return SOUND_EVENTS.register(name,
                () -> SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath(WFLib.MODID, name)));
    }

    public static void register(IEventBus modBus) {
        SOUND_EVENTS.register(modBus);
    }
}
