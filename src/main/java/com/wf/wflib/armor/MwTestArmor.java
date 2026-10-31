package com.wf.wflib.armor;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ArmorItem;

import java.util.Map;
import com.wf.wflib.armor.api.ArmorPreset;
import com.wf.wflib.armor.api.ProtectionType;

/**
 * Dev-only test pieces: Modular Warfare archive models ({@code modular-archive-gltf}) via GemRender.
 * Assets come from the {@code armor_frame.py} resource pack; missing => nothing drawn.
 */
final class MwTestArmor {

    private MwTestArmor() {
    }

    static void addTo(Map<ResourceLocation, ArmorPreset> presets) {
        add(presets, "mw_6b47", ArmorItem.Type.HELMET, "armed_forces_of_the_russian_federation/armor/6b47");
        add(presets, "mw_bearsuit", ArmorItem.Type.CHESTPLATE, "eftc/armor/bearsuit");
        add(presets, "mw_bearpants", ArmorItem.Type.LEGGINGS, "eftc/armor/bearpants");
        add(presets, "mw_uniform", ArmorItem.Type.CHESTPLATE,
                "armed_forces_of_the_russian_federation/armor/russianuniform");
        add(presets, "mw_exo", ArmorItem.Type.CHESTPLATE, "kanonarmor__kanonarmor/armor/exokarkas");
        add(presets, "mw_ghillie", ArmorItem.Type.CHESTPLATE, "kanonarmor__kanonarmor/armor/ghillie",
                EquipmentSlot.HEAD, EquipmentSlot.LEGS);
        ArmorPreset exo = ArmorPreset.piece("exo_test", ArmorItem.Type.CHESTPLATE, 100)
                .row(ProtectionType.IMPACT, 0.0D, 0.05D, 0.0D, 0.0D)
                .exo(testExo())
                .build();
        presets.put(exo.id(), exo);
    }

    /** Repairs with logs; every feature on one piece. */
    static com.wf.wflib.armor.api.exo.ExoSpec testExo() {
        com.wf.wflib.armor.api.exo.ExoStats on = new com.wf.wflib.armor.api.exo.ExoStats(true, 30.0D,
                Map.of("grenades", 4), 0.6D, 0.4D, true, true);
        com.wf.wflib.armor.api.exo.ExoStats off = new com.wf.wflib.armor.api.exo.ExoStats(false, 0.0D, Map.of(),
                0.0D, 0.0D, false, false);
        java.util.List<com.wf.wflib.armor.api.exo.ExoPart> parts = java.util.List.of(
                new com.wf.wflib.armor.api.exo.ExoPart("arms", 100, java.util.EnumSet.of(
                        com.wf.wflib.armor.api.exo.ExoFeature.RECOIL, com.wf.wflib.armor.api.exo.ExoFeature.SWAY,
                        com.wf.wflib.armor.api.exo.ExoFeature.TRIGGER, com.wf.wflib.armor.api.exo.ExoFeature.SHOULDER),
                        java.util.Set.of("left_arm", "right_arm")),
                new com.wf.wflib.armor.api.exo.ExoPart("legs", 100, java.util.EnumSet.of(
                        com.wf.wflib.armor.api.exo.ExoFeature.SPRINT, com.wf.wflib.armor.api.exo.ExoFeature.CARRY),
                        java.util.Set.of("left_leg", "right_leg")),
                new com.wf.wflib.armor.api.exo.ExoPart("core", 100, java.util.EnumSet.of(
                        com.wf.wflib.armor.api.exo.ExoFeature.REACTOR, com.wf.wflib.armor.api.exo.ExoFeature.LIFE_SUPPORT),
                        java.util.Set.of("torso")));
        return new com.wf.wflib.armor.api.exo.ExoSpec(on, off, 20.0D, parts, true, true,
                java.util.Set.of(ResourceLocation.fromNamespaceAndPath("minecraft", "logs")));
    }

    private static void add(Map<ResourceLocation, ArmorPreset> presets, String path, ArmorItem.Type type,
                            String model, EquipmentSlot... occupies) {
        ArmorPreset preset = ArmorPreset.piece(path, type, 100)
                .row(ProtectionType.IMPACT, 0.0D, 0.05D, 0.0D, 0.0D)
                .model(ResourceLocation.fromNamespaceAndPath("mwarchive", "models/" + model + ".gltf"))
                .occupies(occupies)
                .build();
        presets.put(preset.id(), preset);
    }
}
