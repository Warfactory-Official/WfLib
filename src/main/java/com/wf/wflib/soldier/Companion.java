package com.wf.wflib.soldier;

import com.wf.wflib.soldier.api.SoldierOrder;
import com.wf.wflib.soldier.api.SoldierRole;
import com.wf.wflib.soldier.api.SoldierSpec;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;

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
    /** Gear it has been told to get from storage; see {@link Requisition}. */
    public Want want = Want.NONE;

    public Companion(UUID id, String callsign, String side, SoldierRole role, ResourceLocation gun) {
        this.id = id;
        this.callsign = clean(callsign);
        this.side = side;
        this.role = role;
        this.gun = gun;
    }

    /** A profile-name-safe callsign: a soldier's name is {@code <callsign>_<n>} and a player name has 16 characters. */
    static String clean(String name) {
        String safe = name == null ? "" : name.replaceAll("[^A-Za-z0-9_]", "");
        return safe.isEmpty() ? "Soldier" : safe.substring(0, Math.min(safe.length(), 13));
    }

    public SoldierSpec spec() {
        return new SoldierSpec(side, callsign, false, gun, grenades, Map.of(), spares, role);
    }

    CompoundTag save(HolderLookup.Provider registries) {
        CompoundTag t = new CompoundTag();
        t.putUUID("id", id);
        t.putString("callsign", callsign);
        t.putString("side", side);
        t.putString("voice", voice);
        t.putInt("fireTeam", fireTeam);
        t.putString("role", role.id);
        if (gun != null) t.putString("gun", gun.toString());
        t.put("grenades", Tags.ids(grenades));
        t.putInt("spares", spares);
        t.putInt("range", range);
        t.putFloat("spread", spread);
        t.put("armour", Tags.armour(armour, registries));
        t.put("order", Tags.order(order));
        if (!want.isEmpty()) t.putString("want", want.json().toString());
        return t;
    }

    static Companion read(CompoundTag t, HolderLookup.Provider registries) {
        Companion c = new Companion(t.getUUID("id"), t.getString("callsign"), t.getString("side"),
                SoldierRole.tactics(t.getString("role")), t.contains("gun") ? ResourceLocation.parse(t.getString("gun")) : null);
        c.voice = t.getString("voice");
        c.fireTeam = Math.floorMod(t.getInt("fireTeam"), TEAMS.length);
        c.grenades = Tags.ids(t.getList("grenades", Tag.TAG_STRING));
        c.spares = t.getInt("spares");
        c.range = t.getInt("range");
        c.spread = t.getFloat("spread");
        c.armour.putAll(Tags.armour(t.getCompound("armour"), registries));
        c.order = Tags.order(t.getCompound("order"));
        if (t.contains("want")) {
            try {
                c.want = Want.read(com.google.gson.JsonParser.parseString(t.getString("want")).getAsJsonObject());
            } catch (RuntimeException ignored) {
                // an unreadable want is dropped: the soldier keeps what it wears
            }
        }
        return c;
    }

    /** The registry id of an item, for logs and commands. */
    static String name(ItemStack s) {
        return BuiltInRegistries.ITEM.getKey(s.getItem()).toString();
    }
}
