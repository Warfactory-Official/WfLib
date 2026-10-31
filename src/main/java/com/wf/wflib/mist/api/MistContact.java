package com.wf.wflib.mist.api;

import com.wf.wflib.armor.ArmorSystem;
import com.wf.wflib.armor.api.ArmorExposure;
import com.wf.wflib.armor.api.ProtectionType;
import net.minecraft.world.entity.LivingEntity;

/** Chemical exposure of worn armour; no-op while armor is off. */
public final class MistContact {
    private MistContact() {
    }

    public static void chemical(LivingEntity living, double tier) {
        if (ArmorSystem.active()) {
            ArmorExposure.contact(living, ProtectionType.CHEMICAL, tier);
        }
    }
}
