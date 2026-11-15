package com.wf.wflib.armor;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ArmorItem;

import java.util.Map;
import com.wf.wflib.armor.api.ArmorPreset;
import com.wf.wflib.armor.api.ProtectionType;
import com.wf.wflib.armor.api.exo.ExoFeature;
import com.wf.wflib.armor.api.exo.ExoPart;
import com.wf.wflib.armor.api.exo.ExoSpec;
import com.wf.wflib.armor.api.exo.ExoStats;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Dev-only test pieces: Modular Warfare archive models ({@code modular-archive-gltf}) via GemRender.
 * Assets come from the {@code armor_frame.py} resource pack; missing => nothing drawn.
 */
final class MwTestArmor {

    private MwTestArmor() {
    }

    private static final Set<ResourceLocation> IDS = new HashSet<>();

    static boolean isDev(ResourceLocation id) {
        return IDS.contains(id);
    }

    static void addTo(Map<ResourceLocation, ArmorPreset> presets) {
        Set<ResourceLocation> before = new HashSet<>(presets.keySet());
        addAll(presets);
        IDS.addAll(presets.keySet());
        IDS.removeAll(before);
    }

    private static void addAll(Map<ResourceLocation, ArmorPreset> presets) {
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
    static ExoSpec testExo() {
        ExoStats on = new ExoStats(true, 30.0D,
                Map.of("grenades", 4), 0.6D, 0.4D, true, true);
        ExoStats off = new ExoStats(false, 0.0D, Map.of(),
                0.0D, 0.0D, false, false);
        List<ExoPart> parts = List.of(
                new ExoPart("arms", 100, EnumSet.of(
                        ExoFeature.RECOIL, ExoFeature.SWAY,
                        ExoFeature.TRIGGER, ExoFeature.SHOULDER),
                        Set.of("left_arm", "right_arm")),
                new ExoPart("legs", 100, EnumSet.of(
                        ExoFeature.SPRINT, ExoFeature.CARRY),
                        Set.of("left_leg", "right_leg")),
                new ExoPart("core", 100, EnumSet.of(
                        ExoFeature.REACTOR, ExoFeature.LIFE_SUPPORT),
                        Set.of("torso")));
        return new ExoSpec(on, off, 20.0D, parts, true, true,
                Set.of(ResourceLocation.fromNamespaceAndPath("minecraft", "logs")));
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
