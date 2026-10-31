package com.wf.wflib.armor;

import com.wf.wflib.armor.api.ArmorPreset;
import com.wf.wflib.armor.api.ProtectionType;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ArmorItem;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/** Catalogue armour, as worked examples for the model (not balance-tuned); registered iff requested. Dev-only {@code mw_*} when not production. */
public final class ArmorCatalogue {
    private static final Map<ResourceLocation, ArmorPreset> DEFS = new LinkedHashMap<>();

    static {
        // Vest: weave+padding -> sharp/impact; weak vs kinetic alone by design, pockets exist for plates.
        add(ArmorPreset.piece("plate_carrier", ArmorItem.Type.CHESTPLATE, 600)
                .row(ProtectionType.SHARP, 4.0D, 0.45D, 3.0D, 0.0D)
                .row(ProtectionType.IMPACT, 2.0D, 0.35D, 2.0D, 0.0D)
                .row(ProtectionType.KINETIC, 2.0D, 0.10D, 1.0D, 0.0D)
                .row(ProtectionType.BLAST, 1.0D, 0.15D, 1.0D, 0.0D)
                .inserts(2, ProtectionType.KINETIC, ProtectionType.BLAST)
                .wornFloor(0.10D)
                .build());

        // Steel: high hardness threshold (buckshot marks, doesn't grind); high wornFloor, still steel once spent.
        add(ArmorPreset.insert("steel_plate", 3000)
                .row(ProtectionType.KINETIC, 20.0D, 0.50D, 15.0D, 0.0D)
                .row(ProtectionType.BLAST, 6.0D, 0.25D, 8.0D, 0.0D)
                .wornFloor(0.60D)
                .build());

        // Ceramic: higher DT/DR than steel, wornFloor 0 (shatters).
        add(ArmorPreset.insert("ceramic_plate", 1200)
                .row(ProtectionType.KINETIC, 28.0D, 0.55D, 12.0D, 0.0D)
                .row(ProtectionType.BLAST, 4.0D, 0.20D, 6.0D, 0.0D)
                .wornFloor(0.0D)
                .build());

        // Sealed suit: value is tier, not DT/DR (near-zero vs bullets). tier=200 chemical -> shrugs a
        // full-strength gas cloud; tier=80 thermal -> fire-immune, lava still hurts.
        add(ArmorPreset.piece("hazmat_suit", ArmorItem.Type.CHESTPLATE, 400)
                .row(ProtectionType.CHEMICAL, 0.0D, 0.85D, 0.0D, 200.0D)
                .row(ProtectionType.THERMAL, 0.0D, 0.20D, 0.0D, 80.0D)
                .row(ProtectionType.IMPACT, 0.0D, 0.05D, 0.0D, 0.0D)
                .build());

        add(ArmorPreset.piece("combat_helmet", ArmorItem.Type.HELMET, 350)
                .row(ProtectionType.KINETIC, 6.0D, 0.25D, 5.0D, 0.0D)
                .row(ProtectionType.SHARP, 6.0D, 0.40D, 4.0D, 0.0D)
                .row(ProtectionType.IMPACT, 4.0D, 0.40D, 3.0D, 0.0D)
                .row(ProtectionType.BLAST, 2.0D, 0.20D, 2.0D, 0.0D)
                .wornFloor(0.25D)
                .model(ArmorPreset.rl("models/armor/combat_helmet.glb"))
                .nvgMount()
                .build());

        if (!net.neoforged.fml.loading.FMLEnvironment.production) {
            MwTestArmor.addTo(DEFS);
        }
    }

    private ArmorCatalogue() {
    }

    private static void add(ArmorPreset preset) {
        DEFS.put(preset.id(), preset);
    }

    public static Collection<ArmorPreset> all() {
        return Collections.unmodifiableCollection(DEFS.values());
    }

    public static ArmorPreset get(ResourceLocation id) {
        return DEFS.get(id);
    }
}
