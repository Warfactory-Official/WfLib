package com.wf.wflib.armor;

import com.wf.wflib.armor.api.ArmorExposure;
import com.wf.wflib.armor.api.ArmorMaterials;
import com.wf.wflib.armor.api.ArmorPieceItem;
import com.wf.wflib.armor.api.ArmorPreset;
import com.wf.wflib.armor.api.ArmorPresets;
import com.wf.wflib.armor.api.ArmorProfiles;
import com.wf.wflib.armor.api.ProtectionType;
import com.wf.wflib.armor.api.client.ArmorPieceClient;
import com.wf.wflib.armor.api.exo.client.ExoClient;
import com.wf.wflib.armor.api.exo.client.ExoPopupClient;
import com.wf.wflib.armor.api.exo.EnergyCellItem;
import com.wf.wflib.armor.api.exo.ExoConfig;
import com.wf.wflib.armor.api.exo.ExoEvents;
import com.wf.wflib.armor.api.exo.LifeSupport;
import com.wf.wflib.armor.api.exo.ExoPackets;
import com.wf.wflib.armor.api.exo.ExoProfiles;
import com.wf.wflib.core.api.WFLibContent;
import com.wf.wflib.core.api.WFSystem;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ArmorMaterial;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.crafting.Ingredient;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.client.extensions.common.IClientItemExtensions;
import net.neoforged.neoforge.client.extensions.common.RegisterClientExtensionsEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.registries.RegisterEvent;

import java.util.List;
import java.util.Map;
import com.wf.wflib.core.api.WFLib;

/** One DT/DR resolver for every hit, per protection type; wear and exposure on worn pieces. Catalogue items request-gated ({@code WFLibContent.armor}). */
public final class ArmorSystem implements WFSystem {
    private static volatile boolean active;

    /** Iron's defense spread; vanilla reads it only for unmanaged pieces. */
    private static final Map<ArmorItem.Type, Integer> FALLBACK_DEFENSE = Map.of(
            ArmorItem.Type.HELMET, 2,
            ArmorItem.Type.CHESTPLATE, 6,
            ArmorItem.Type.LEGGINGS, 5,
            ArmorItem.Type.BOOTS, 2,
            ArmorItem.Type.BODY, 6);

    /** Mixin guard: true once this system registered. */
    public static boolean active() {
        return active;
    }

    @Override
    public String id() {
        return "armor";
    }

    @Override
    public void register(IEventBus modBus, ModContainer container) {
        com.wf.wflib.core.api.CreativeTabs.contribute("armor", 240, out -> {
            com.wf.wflib.armor.api.ArmorContent.items().forEach(item -> out.accept(item.get()));
            for (var cell : java.util.List.of(EnergyCellItem.CELL, EnergyCellItem.CELL_LARGE)) {
                out.accept(cell.get());
                out.accept(cell.get().full());
            }
        });
        EnergyCellItem.ITEMS.register(modBus);
        modBus.addListener(RegisterCapabilitiesEvent.class, EnergyCellItem::registerCapabilities);
        active = true;
        container.registerConfig(ModConfig.Type.COMMON, ArmorConfig.SPEC, "wflib-armor.toml");
        modBus.addListener(ArmorConfig::onLoad);
        container.registerConfig(ModConfig.Type.COMMON, ExoConfig.SPEC, "wflib-exo.toml");
        modBus.addListener(ExoConfig::onLoad);
        modBus.addListener(RegisterPayloadHandlersEvent.class, ExoPackets::register);
        NeoForge.EVENT_BUS.register(ExoEvents.class);
        LifeSupport.registerDefaults();

        com.wf.wflib.armor.api.exo.ExoPopup.register();
        ArmorComponents.register(modBus);
        modBus.addListener(ArmorSystem::onRegister);
        modBus.addListener(FMLCommonSetupEvent.class, event -> event.enqueueWork(() -> ArmorPresets.live()
                .forEach(preset -> {
            ArmorProfiles.author(preset.id(), preset.spec());
            ExoProfiles.author(preset.id(), preset.exo());
        })));
        modBus.addListener(FMLCommonSetupEvent.class, event -> event.enqueueWork(() -> {
            if (com.wf.wflib.core.api.WFSystems.enabled("carry")) {
                com.wf.wflib.carry.api.Shoulder.PERMIT.set(WFLib.MODID,
                        p -> com.wf.wflib.armor.api.exo.Exo.effect(p).shoulder());
            }
        }));
        ArmorExposure.SOURCES.register(ArmorPreset.rl("fire_lava"), 0, (entity, type) -> {
            if (type != ProtectionType.THERMAL) {
                return 0.0D;
            }
            if (entity.isInLava()) {
                return ArmorConfig.get().lavaTier();
            }
            return entity.isOnFire() ? ArmorConfig.get().fireTier() : 0.0D;
        });
        if (com.wf.wflib.core.api.WFSystems.enabled("probe")) {
            DressProbe.registerServer();
        }
        NeoForge.EVENT_BUS.register(ArmorEvents.class);
    }

    @Override
    public void registerClient(IEventBus modBus) {
        NeoForge.EVENT_BUS.register(ArmorPieceClient.class);
        if (com.wf.wflib.core.api.WFSystems.enabled("probe")) {
            DressProbe.registerClient();
        }
        NeoForge.EVENT_BUS.register(ExoClient.class);
        NeoForge.EVENT_BUS.register(ExoPopupClient.class);
        modBus.addListener(RegisterKeyMappingsEvent.class, ExoPopupClient::keys);
        modBus.addListener(RegisterClientExtensionsEvent.class, event -> {
            for (Item item : BuiltInRegistries.ITEM) {
                if (item instanceof ArmorPieceItem armor && armor.getClass() == ArmorPieceItem.class
                        && !event.isItemRegistered(item)) {
                    IClientItemExtensions extensions = ArmorPieceClient.of(armor.preset());
                    event.registerItem(extensions, item);
                }
            }
        });
    }

    private static void onRegister(RegisterEvent event) {
        if (event.getRegistryKey().equals(Registries.ARMOR_MATERIAL)) {
            WFLibContent.freeze();
            event.register(Registries.ARMOR_MATERIAL, helper -> {
                helper.register(ArmorMaterials.NVG.getId(), new ArmorMaterial(
                        Map.of(ArmorItem.Type.HELMET, 0, ArmorItem.Type.CHESTPLATE, 0, ArmorItem.Type.LEGGINGS, 0,
                                ArmorItem.Type.BOOTS, 0, ArmorItem.Type.BODY, 0), 0,
                        SoundEvents.ARMOR_EQUIP_GENERIC, () -> Ingredient.EMPTY,
                        List.of(new ArmorMaterial.Layer(ResourceLocation.fromNamespaceAndPath(WFLib.MODID, "nvg"))),
                        0.0F, 0.0F));
                for (ArmorPreset preset : ArmorPresets.live()) {
                    if (!preset.isInsert()) {
                        // Texture textures/models/armor/<preset>_layer_1.png; GemRender-drawn presets still need a material to be an ArmorItem at all.
                        helper.register(preset.id(), new ArmorMaterial(FALLBACK_DEFENSE, 9,
                                SoundEvents.ARMOR_EQUIP_IRON, () -> Ingredient.EMPTY,
                                List.of(new ArmorMaterial.Layer(preset.id())), 0.0F, 0.0F));
                    }
                }
            });
        } else if (event.getRegistryKey().equals(Registries.ITEM)) {
            WFLibContent.freeze();
            event.register(Registries.ITEM, helper -> {
                for (ArmorPreset preset : ArmorPresets.live()) {
                    helper.register(preset.id(), preset.isInsert()
                            ? new ArmorInsertItem(preset, new Item.Properties().stacksTo(1).durability(preset.durability()))
                            : new ArmorPieceItem(preset, ArmorMaterials.of(preset),
                                    new Item.Properties().durability(preset.durability())));
                }
            });
        }
    }
}
