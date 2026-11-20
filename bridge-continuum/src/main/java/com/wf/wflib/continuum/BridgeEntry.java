package com.wf.wflib.continuum;

import com.hbm.api.data.ArmorDataMaps;
import dev.golem.core.platform.ModEntry;

/** The three Golem phases: nothing to substitute or declare yet; {@link #registered} checks that Continuum's reduction really stood down. */
final class BridgeEntry implements ModEntry {
    @Override
    public void substitute() {
        if (BridgeConfig.glyphids()) {
            // No hive world feature of Continuum's own: WFLib's colonies seed the world. Needs Continuum's config override (the bridge's upstream seam).
            try {
                com.hbm.config.CommonConfig.override(com.hbm.config.CommonConfig.CATEGORY_MOBS, "12.G00_enableHives", "false");
            } catch (NoSuchMethodError old) {
                ContinuumBridge.LOG.warn("This Continuum has no CommonConfig.override: its glyphid hives stay on until it is switched off in its own config (12.G00_enableHives)");
            }
        }
        // Doors, the MK5 ray and spawner blocks come here as their levels are built (docs/ntm-continuum-compat.md, 3.2 and 5.2).
    }

    @Override
    public void declare() {
    }

    @Override
    public void registered() {
        if (BridgeConfig.glyphids()) Glyphids.install();
        if (!BridgeConfig.armour()) return;
        // After every datapack load: if any Continuum set still carries a resistance, the patch did not apply and a hit is reduced twice.
        ArmorDataMaps.ARMOR_SETS.changed.register(() -> {
            long left = ArmorDataMaps.ARMOR_SETS.byId().values().stream().filter(s -> s.resistance().isPresent()).count();
            if (left > 0) ContinuumBridge.LOG.error("{} Continuum armour set(s) still carry a damage resistance after the bridge's patch: hits on them are reduced twice", left);
            else ContinuumBridge.LOG.info("Continuum's armour-set resistances are stood down ({} sets); WFLib armour profiles apply", ArmorDataMaps.ARMOR_SETS.byId().size());
        });
        ArmorDataMaps.DAMAGE_RESISTANCE.changed.register(() -> {
            if (!ArmorDataMaps.DAMAGE_RESISTANCE.byId().isEmpty() || !ArmorDataMaps.DAMAGE_RESISTANCE.byTag().isEmpty()) {
                ContinuumBridge.LOG.error("Continuum's per-item damage resistance map is not empty after the bridge's patch: hits are reduced twice");
            }
        });
    }
}
