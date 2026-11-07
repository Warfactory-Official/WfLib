package com.wf.wflib.armor.api;

import com.wf.wflib.armor.ArmorCatalogue;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/** Every garment the armour system knows, bare and with each compatible insert filling its slots. */
public final class ArmorSetups {
    private static final EquipmentSlot[] WORN = {EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS,
            EquipmentSlot.FEET};

    /** @param garment item id; @param inserts ids joined by {@code +}, empty bare; @param covers slots the piece answers for */
    public record Setup(String garment, String inserts, ItemStack stack, EquipmentSlot slot,
                        List<EquipmentSlot> covers, List<ArmorSpec> specs, boolean dev) {
        public String label() {
            return inserts.isEmpty() ? garment : garment + "+" + inserts;
        }
    }

    private ArmorSetups() {
    }

    /** Fresh stacks every call; mutate freely. */
    public static List<Setup> all() {
        List<Item> inserts = new ArrayList<>();
        List<Item> garments = new ArrayList<>();
        for (Item item : BuiltInRegistries.ITEM) {
            ArmorSpec spec = ArmorProfiles.specFor(new ItemStack(item));
            if (spec == null) {
                continue;
            }
            if (item instanceof ArmorItem a) {
                if (a.getEquipmentSlot() != EquipmentSlot.BODY) {
                    garments.add(item);
                }
            } else if (spec.insertSlots() == 0) {
                inserts.add(item);
            }
        }
        List<Setup> out = new ArrayList<>();
        for (Item g : garments) {
            ArmorItem armor = (ArmorItem) g;
            ItemStack bare = new ItemStack(g);
            ArmorSpec gs = ArmorProfiles.specFor(bare);
            ResourceLocation gid = BuiltInRegistries.ITEM.getKey(g);
            out.add(setup(gid, "", bare, armor.getEquipmentSlot(), List.of(gs), List.of()));
            if (gs.insertSlots() <= 0) {
                continue;
            }
            for (Item i : inserts) {
                ItemStack insertStack = new ItemStack(i);
                if (!ArmorStacks.canInsert(bare, insertStack)) {
                    continue;
                }
                ItemStack filled = new ItemStack(g);
                List<ItemStack> fill = new ArrayList<>();
                for (int k = 0; k < gs.insertSlots(); k++) {
                    fill.add(new ItemStack(i));
                }
                ArmorStacks.setInserts(filled, fill);
                List<ArmorSpec> specs = new ArrayList<>();
                specs.add(gs);
                for (int k = 0; k < gs.insertSlots(); k++) {
                    specs.add(ArmorProfiles.specFor(insertStack));
                }
                out.add(setup(gid, BuiltInRegistries.ITEM.getKey(i).toString() + (gs.insertSlots() > 1
                        ? "x" + gs.insertSlots() : ""), filled, armor.getEquipmentSlot(), specs, List.of()));
            }
        }
        return out;
    }

    private static Setup setup(ResourceLocation gid, String inserts, ItemStack stack, EquipmentSlot slot,
                               List<ArmorSpec> specs, List<ArmorSpec> unused) {
        List<EquipmentSlot> covers = new ArrayList<>();
        covers.add(slot);
        if (stack.getItem() instanceof ArmorPieceItem piece) {
            for (EquipmentSlot s : WORN) {
                if (piece.preset().occupies().contains(s)) {
                    covers.add(s);
                }
            }
        }
        return new Setup(gid.toString(), inserts, stack, slot, covers, specs, ArmorCatalogue.isDev(gid));
    }

    public static String zone(EquipmentSlot slot) {
        return switch (slot) {
            case HEAD -> "head";
            case CHEST -> "chest";
            case LEGS -> "legs";
            case FEET -> "feet";
            default -> slot.getName();
        };
    }

    public static Set<EquipmentSlot> worn() {
        return Set.of(WORN);
    }
}
