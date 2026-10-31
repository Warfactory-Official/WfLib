package com.wf.wflib.hazard;

import com.wf.wflib.armor.ArmorSystem;
import com.wf.wflib.armor.api.ArmorExposure;
import com.wf.wflib.armor.api.ArmorResult;
import com.wf.wflib.armor.api.ArmorResolution;
import com.wf.wflib.armor.api.ProtectionType;
import com.wf.wflib.radiation.api.RadiationConfig;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import com.wf.wflib.hazard.api.HazardClass;
import com.wf.wflib.hazard.api.HazardContent;

/**
 * Divergence: NTM ArmorUtil/ArmorRegistry/HazmatRegistry -> item tags + wflib armour.
 * Dose mult = RADIATION pass-through of worn armour x 10^-0.2 under Rad-X (NTM: 10^-sum(resistance)).
 */
public final class HazardProtection {

    private static final EquipmentSlot[] ARMOR_SLOTS =
            {EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET};
    private static final double RADX_FACTOR = Math.pow(10D, -0.2D);
    private static final float PROBE = 1.0E6F;

    private HazardProtection() {
    }

    public static boolean hasProtection(LivingEntity entity, EquipmentSlot slot, HazardClass hazard) {
        return entity.getItemBySlot(slot).is(HazardContent.HEAD_PROTECTION.get(hazard));
    }

    /** NTM gas mask filter wear -> CHEMICAL exposure on the worn armour. */
    public static void damageGasMaskFilter(LivingEntity entity, int amount) {
        if (!ArmorSystem.active()) return;
        ArmorExposure.contact(entity, ProtectionType.CHEMICAL, amount);
    }

    public static boolean wearsFullSet(LivingEntity entity, TagKey<Item> tag) {
        for (EquipmentSlot slot : ARMOR_SLOTS) {
            ItemStack stack = entity.getItemBySlot(slot);
            if (stack.isEmpty() || !stack.is(tag)) return false;
        }
        return true;
    }

    public static boolean checkForHaz2(LivingEntity entity) {
        return wearsFullSet(entity, HazardContent.HAZMAT_ADVANCED) || wearsFullSet(entity, HazardContent.DIGAMMA_PROTECTION);
    }

    public static boolean checkForHazmat(LivingEntity entity) {
        return wearsFullSet(entity, HazardContent.HAZMAT) || checkForHaz2(entity);
    }

    public static boolean checkForDigamma(LivingEntity entity) {
        return wearsFullSet(entity, HazardContent.DIGAMMA_PROTECTION) || entity.hasEffect(HazardContent.stability());
    }

    public static boolean checkForFaraday(LivingEntity entity) {
        return wearsFullSet(entity, HazardContent.FARADAY);
    }

    /** NTM 1.7 InventoryPlayer.hasItem: main 36 slots only. */
    public static boolean isHoldingReacher(LivingEntity entity) {
        if (!(entity instanceof Player player)) return false;
        Inventory inventory = player.getInventory();
        for (int i = 0; i < Inventory.INVENTORY_SIZE; i++) {
            if (inventory.getItem(i).is(HazardContent.REACHER)) return true;
        }
        return false;
    }

    /** Fraction of a radiation dose reaching the body; players only (NTM). */
    public static double radiationMod(LivingEntity entity) {
        if (!(entity instanceof Player)) return 1D;
        // Large probe => DT negligible; shielding is a DR fraction, not a per-hit threshold.
        double mod = 1D;
        if (ArmorSystem.active()) {
            ArmorResult result = ArmorResolution.resolveWhole(entity, null, ProtectionType.RADIATION, PROBE, false);
            mod = result.through() / PROBE;
        }
        if (entity.hasEffect(HazardContent.radx())) mod *= RADX_FACTOR;
        return mod;
    }

    /** Armour wear for a received dose rate; RAD/s -> exposure tier. */
    public static void exposeArmor(LivingEntity entity, double radPerSecond) {
        if (!ArmorSystem.active()) return;
        double tier = radPerSecond * RadiationConfig.get().armorWearPerRad();
        if (tier > 0D) ArmorExposure.contact(entity, ProtectionType.RADIATION, tier);
    }
}
