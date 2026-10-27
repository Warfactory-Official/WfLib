package com.wf.wflib.hazard;

import com.mojang.serialization.Codec;
import com.wf.wflib.WFLib;
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
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

import java.util.EnumMap;
import java.util.Locale;
import java.util.Map;

/** Registry objects of the hazard port. NTM armour sets / reacher / gas masks -> item tags here. */
public final class HazardContent {

    private static final DeferredRegister<DataComponentType<?>> COMPONENTS =
            DeferredRegister.create(Registries.DATA_COMPONENT_TYPE, WFLib.MODID);
    private static final DeferredRegister<MobEffect> EFFECTS = DeferredRegister.create(Registries.MOB_EFFECT, WFLib.MODID);
    private static final DeferredRegister<SoundEvent> SOUNDS = DeferredRegister.create(Registries.SOUND_EVENT, WFLib.MODID);
    private static final DeferredRegister<AttachmentType<?>> ATTACHMENTS =
            DeferredRegister.create(NeoForgeRegistries.Keys.ATTACHMENT_TYPES, WFLib.MODID);

    /** Per-item RAD/s from neutron activation; never alongside a registry radiation entry. */
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<Float>> NEUTRON_ACTIVATION =
            COMPONENTS.register("neutron_activation", () -> DataComponentType.<Float>builder()
                    .persistent(Codec.FLOAT).networkSynchronized(ByteBufCodecs.FLOAT).build());
    /** Per-stack radiation override read by {@code HazardTransformerRadiationNBT}. */
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<Float>> HAZ_RADIATION =
            COMPONENTS.register("haz_radiation", () -> DataComponentType.<Float>builder()
                    .persistent(Codec.FLOAT).networkSynchronized(ByteBufCodecs.FLOAT).build());

    public static final DeferredHolder<AttachmentType<?>, AttachmentType<LivingHazardProps>> LIVING_PROPS =
            ATTACHMENTS.register("hazard_props", () -> AttachmentType.builder(LivingHazardProps::new)
                    .serialize(LivingHazardProps.MAP_CODEC.codec(), p -> !p.isDefault()).build());

    public static final DeferredHolder<MobEffect, MobEffect> RADIATION = EFFECTS.register("radiation",
            () -> new HazardEffects.Radiation());
    public static final DeferredHolder<MobEffect, MobEffect> RADAWAY = EFFECTS.register("radaway",
            () -> new HazardEffects.Radaway());
    public static final DeferredHolder<MobEffect, MobEffect> RADX = EFFECTS.register("radx",
            () -> new HazardEffects.Plain(MobEffectCategory.BENEFICIAL, 0xBB4B00));
    public static final DeferredHolder<MobEffect, MobEffect> STABILITY = EFFECTS.register("stability",
            () -> new HazardEffects.Plain(MobEffectCategory.BENEFICIAL, 0xD0D0D0));

    public static final DeferredHolder<SoundEvent, SoundEvent> COUGH = sound("hazard.cough");
    public static final DeferredHolder<SoundEvent, SoundEvent> VOMIT = sound("hazard.vomit");
    @SuppressWarnings("unchecked")
    public static final DeferredHolder<SoundEvent, SoundEvent>[] GEIGER = new DeferredHolder[6];
    public static final DeferredHolder<SoundEvent, SoundEvent> TECH_BOOP = sound("hazard.tech_boop");

    public static final ResourceKey<DamageType> DAMAGE_RADIATION = com.wf.wflib.damage.WFDamageTypes.RADIATION;
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
    public static final Map<HazardClass, TagKey<Item>> HEAD_PROTECTION = new EnumMap<>(HazardClass.class);

    static {
        for (int i = 0; i < GEIGER.length; i++) GEIGER[i] = sound("hazard.geiger" + (i + 1));
        for (HazardClass c : HazardClass.values()) {
            HEAD_PROTECTION.put(c, itemTag("hazard_protection/" + c.name().toLowerCase(Locale.ROOT)));
        }
    }

    private HazardContent() {
    }

    public static void register(IEventBus modBus) {
        COMPONENTS.register(modBus);
        EFFECTS.register(modBus);
        ATTACHMENTS.register(modBus);
        SOUNDS.register(modBus);
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
        return SOUNDS.register(name,
                () -> SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath(WFLib.MODID, name)));
    }

    private static ResourceKey<DamageType> damage(String name) {
        return ResourceKey.create(Registries.DAMAGE_TYPE, ResourceLocation.fromNamespaceAndPath(WFLib.MODID, name));
    }

    private static TagKey<Item> itemTag(String name) {
        return TagKey.create(Registries.ITEM, ResourceLocation.fromNamespaceAndPath(WFLib.MODID, name));
    }
}
