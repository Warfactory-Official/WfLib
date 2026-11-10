package com.wf.wflib.soldier;

import com.wf.wflib.soldier.api.SoldierOrder;
import com.wf.wflib.soldier.api.SoldierRole;
import com.wf.wflib.soldier.api.SoldierSpec;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.BuiltInRegistries;
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
import java.util.UUID;

/**
 * A soldier that belongs to a player. Soldiers are not saved with the world, so this is the record that brings one back:
 * who it is, what it wears and carries, which fire team it is in and what it was last told. Worn pieces are kept as full
 * stacks, because modular armour is its components, not its item id.
 */
public final class Companion {
    public static final String[] TEAMS = {"Alpha", "Bravo", "Charlie", "Delta"};

    public final UUID id;
    public String callsign, side, voice = "";
    public int fireTeam;
    public SoldierRole role;
    public ResourceLocation gun;
    public List<ResourceLocation> grenades = List.of();
    public int spares, range = 80;
    public float spread = 2f;
    public final Map<EquipmentSlot, ItemStack> armour = new EnumMap<>(EquipmentSlot.class);
    public SoldierOrder order = SoldierOrder.FOLLOW;

    public Companion(UUID id, String callsign, String side, SoldierRole role, ResourceLocation gun) {
        this.id = id;
        this.callsign = clean(callsign);
        this.side = side;
        this.role = role;
        this.gun = gun;
    }

    /** A profile-name-safe callsign: a soldier's name is {@code <callsign>_<n>} and a player name has 16 characters. */
    static String clean(String name) {
        String safe = name == null ? "" : name.replaceAll("[^A-Za-z0-9]", "");
        return safe.isEmpty() ? "Soldier" : safe.substring(0, Math.min(safe.length(), 10));
    }

    public SoldierSpec spec() {
        return new SoldierSpec(side, callsign, false, gun, grenades, Map.of(), spares, role);
    }

    CompoundTag save(HolderLookup.Provider registries) {
        var ops = RegistryOps.create(NbtOps.INSTANCE, registries);
        CompoundTag t = new CompoundTag();
        t.putUUID("id", id);
        t.putString("callsign", callsign);
        t.putString("side", side);
        t.putString("voice", voice);
        t.putInt("fireTeam", fireTeam);
        t.putString("role", role.id);
        if (gun != null) t.putString("gun", gun.toString());
        ListTag g = new ListTag();
        grenades.forEach(r -> g.add(StringTag.valueOf(r.toString())));
        t.put("grenades", g);
        t.putInt("spares", spares);
        t.putInt("range", range);
        t.putFloat("spread", spread);
        CompoundTag worn = new CompoundTag();
        armour.forEach((slot, stack) -> {
            if (!stack.isEmpty()) ItemStack.CODEC.encodeStart(ops, stack).result().ifPresent(tag -> worn.put(slot.getName(), tag));
        });
        t.put("armour", worn);
        CompoundTag o = new CompoundTag();
        o.putString("kind", order.kind().id);
        o.putString("region", order.region());
        if (order.pos() != null) {
            o.putDouble("x", order.pos().x);
            o.putDouble("y", order.pos().y);
            o.putDouble("z", order.pos().z);
        }
        t.put("order", o);
        return t;
    }

    static Companion read(CompoundTag t, HolderLookup.Provider registries) {
        var ops = RegistryOps.create(NbtOps.INSTANCE, registries);
        Companion c = new Companion(t.getUUID("id"), t.getString("callsign"), t.getString("side"),
                SoldierRole.tactics(t.getString("role")), t.contains("gun") ? ResourceLocation.parse(t.getString("gun")) : null);
        c.voice = t.getString("voice");
        c.fireTeam = Math.floorMod(t.getInt("fireTeam"), TEAMS.length);
        List<ResourceLocation> grenades = new ArrayList<>();
        for (Tag e : t.getList("grenades", Tag.TAG_STRING)) grenades.add(ResourceLocation.parse(e.getAsString()));
        c.grenades = List.copyOf(grenades);
        c.spares = t.getInt("spares");
        c.range = t.getInt("range");
        c.spread = t.getFloat("spread");
        CompoundTag worn = t.getCompound("armour");
        for (String key : worn.getAllKeys()) {
            ItemStack.CODEC.parse(ops, worn.get(key)).result().ifPresent(stack -> c.armour.put(SoldierCatalogue.slot(key), stack));
        }
        CompoundTag o = t.getCompound("order");
        var kind = SoldierOrder.Kind.of(o.getString("kind"));
        try {
            if (kind != null) c.order = new SoldierOrder(kind, o.contains("x") ? new Vec3(o.getDouble("x"), o.getDouble("y"), o.getDouble("z")) : null,
                    o.getString("region"));
        } catch (IllegalArgumentException ignored) {
            // a saved order that no longer makes sense (a territory-less patrol): follow instead
        }
        return c;
    }

    /** The registry id of an item, for logs and commands. */
    static String name(ItemStack s) {
        return BuiltInRegistries.ITEM.getKey(s.getItem()).toString();
    }
}
