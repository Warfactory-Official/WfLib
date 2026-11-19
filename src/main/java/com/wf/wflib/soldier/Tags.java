package com.wf.wflib.soldier;

import com.wf.wflib.soldier.api.SoldierOrder;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.RegistryOps;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/** How a soldier's order, armour and item ids are written into saved data (shared by companions and posted soldiers). */
final class Tags {
    private Tags() {}

    static CompoundTag order(SoldierOrder order) {
        CompoundTag o = new CompoundTag();
        o.putString("kind", order.kind().id);
        o.putString("region", order.region());
        if (order.pos() != null) {
            o.putDouble("x", order.pos().x);
            o.putDouble("y", order.pos().y);
            o.putDouble("z", order.pos().z);
        }
        return o;
    }

    /** A saved order that no longer makes sense (an unknown kind, a patrol with no territory) reads as "follow". */
    static SoldierOrder order(CompoundTag o) {
        var kind = SoldierOrder.Kind.of(o.getString("kind"));
        if (kind == null) return SoldierOrder.FOLLOW;
        try {
            return new SoldierOrder(kind, o.contains("x") ? new Vec3(o.getDouble("x"), o.getDouble("y"), o.getDouble("z")) : null, o.getString("region"));
        } catch (IllegalArgumentException e) {
            return SoldierOrder.FOLLOW;
        }
    }

    /** Worn pieces as full stacks: modular armour is its components, not its item id. */
    static CompoundTag armour(Map<EquipmentSlot, ItemStack> armour, HolderLookup.Provider registries) {
        var ops = RegistryOps.create(NbtOps.INSTANCE, registries);
        CompoundTag worn = new CompoundTag();
        armour.forEach((slot, stack) -> {
            if (!stack.isEmpty()) ItemStack.CODEC.encodeStart(ops, stack).result().ifPresent(tag -> worn.put(slot.getName(), tag));
        });
        return worn;
    }

    static Map<EquipmentSlot, ItemStack> armour(CompoundTag worn, HolderLookup.Provider registries) {
        var ops = RegistryOps.create(NbtOps.INSTANCE, registries);
        Map<EquipmentSlot, ItemStack> out = new EnumMap<>(EquipmentSlot.class);
        for (String key : worn.getAllKeys()) {
            ItemStack.CODEC.parse(ops, worn.get(key)).result().ifPresent(stack -> out.put(SoldierCatalogue.slot(key), stack));
        }
        return out;
    }

    static ListTag ids(List<ResourceLocation> ids) {
        ListTag l = new ListTag();
        ids.forEach(r -> l.add(StringTag.valueOf(r.toString())));
        return l;
    }

    static List<ResourceLocation> ids(ListTag l) {
        List<ResourceLocation> out = new ArrayList<>();
        for (Tag e : l) out.add(ResourceLocation.parse(e.getAsString()));
        return List.copyOf(out);
    }
}
