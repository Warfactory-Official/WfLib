package com.wf.wflib.hazard.api;

import com.mojang.serialization.Codec;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.tags.TagKey;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.Item;
import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import com.wf.wflib.core.api.WFLib;

/** Registry objects of the hazard port. NTM armour sets / reacher / gas masks -> item tags here. */
public final class HazardContent {

    /** Per-item RAD/s from neutron activation; never alongside a registry radiation entry. */
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<Float>> NEUTRON_ACTIVATION =
            DeferredHolder.create(Registries.DATA_COMPONENT_TYPE, id("neutron_activation"));
    /** Per-stack radiation override read by {@code HazardTransformerRadiationNBT}. */
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<Float>> HAZ_RADIATION =
            DeferredHolder.create(Registries.DATA_COMPONENT_TYPE, id("haz_radiation"));

    public static final DeferredHolder<AttachmentType<?>, AttachmentType<LivingHazardProps>> LIVING_PROPS =
            DeferredHolder.create(NeoForgeRegistries.Keys.ATTACHMENT_TYPES, id("hazard_props"));

    public static final DeferredHolder<MobEffect, MobEffect> RADIATION = effect("radiation");
    public static final DeferredHolder<MobEffect, MobEffect> RADAWAY = effect("radaway");
    public static final DeferredHolder<MobEffect, MobEffect> RADX = effect("radx");
    public static final DeferredHolder<MobEffect, MobEffect> STABILITY = effect("stability");

    public static final DeferredHolder<SoundEvent, SoundEvent> COUGH = sound("hazard.cough");
    public static final DeferredHolder<SoundEvent, SoundEvent> VOMIT = sound("hazard.vomit");
    public static final List<DeferredHolder<SoundEvent, SoundEvent>> GEIGER = List.of(
            sound("hazard.geiger1"), sound("hazard.geiger2"), sound("hazard.geiger3"),
            sound("hazard.geiger4"), sound("hazard.geiger5"), sound("hazard.geiger6"));
    public static final DeferredHolder<SoundEvent, SoundEvent> TECH_BOOP = sound("hazard.tech_boop");

    public static final DeferredHolder<Item, Item> GEIGER_COUNTER_ITEM = DeferredHolder.create(Registries.ITEM, id("geiger_counter"));

    public static final ResourceKey<DamageType> DAMAGE_RADIATION = com.wf.wflib.damage.api.WFDamageTypes.RADIATION;
    public static final ResourceKey<DamageType> DAMAGE_DIGAMMA = damage("digamma");
    public static final ResourceKey<DamageType> DAMAGE_ASBESTOS = damage("asbestos");
    public static final ResourceKey<DamageType> DAMAGE_BLACKLUNG = damage("blacklung");

    /** Full set (all four armour slots) => contamination type gate passes. */
    public static final TagKey<Item> HAZMAT = itemTag("hazmat");
    public static final TagKey<Item> HAZMAT_ADVANCED = itemTag("hazmat_advanced");
    public static final TagKey<Item> FARADAY = itemTag("faraday");
    public static final TagKey<Item> DIGAMMA_PROTECTION = itemTag("digamma_protection");
    /** Anywhere in the main 36 slots => held hazards attenuated (NTM reacher). */
    public static final TagKey<Item> REACHER = itemTag("reacher");
    /** Container contents' radiation -> sqrt (NTM containment box). */
    public static final TagKey<Item> RAD_CONTAINMENT = itemTag("rad_containment");
    /** Anywhere in inventory => LCD HUD. */
    public static final TagKey<Item> GEIGER_COUNTER = itemTag("geiger_counter");
    /** Head item => blocks {@link HazardClass}; NTM gas mask + filter. */
    public static final Map<HazardClass, TagKey<Item>> HEAD_PROTECTION;

    static {
        Map<HazardClass, TagKey<Item>> head = new EnumMap<>(HazardClass.class);
        for (HazardClass c : HazardClass.values()) {
            head.put(c, itemTag("hazard_protection/" + c.name().toLowerCase(Locale.ROOT)));
        }
        HEAD_PROTECTION = Map.copyOf(head);
    }

    private HazardContent() {
    }

    public static Holder<MobEffect> radx() {
        return RADX;
    }

    public static Holder<MobEffect> stability() {
        return STABILITY;
    }

    static void hurt(LivingEntity entity, ResourceKey<DamageType> type, float amount) {
        if (entity.level() instanceof ServerLevel server) entity.hurt(server.damageSources().source(type), amount);
    }

    private static DeferredHolder<SoundEvent, SoundEvent> sound(String name) {
        return DeferredHolder.create(Registries.SOUND_EVENT, id(name));
    }

    private static DeferredHolder<MobEffect, MobEffect> effect(String name) {
        return DeferredHolder.create(Registries.MOB_EFFECT, id(name));
    }

    private static ResourceLocation id(String name) {
        return ResourceLocation.fromNamespaceAndPath(WFLib.MODID, name);
    }

    private static ResourceKey<DamageType> damage(String name) {
        return ResourceKey.create(Registries.DAMAGE_TYPE, ResourceLocation.fromNamespaceAndPath(WFLib.MODID, name));
    }

    private static TagKey<Item> itemTag(String name) {
        return TagKey.create(Registries.ITEM, ResourceLocation.fromNamespaceAndPath(WFLib.MODID, name));
    }
}
