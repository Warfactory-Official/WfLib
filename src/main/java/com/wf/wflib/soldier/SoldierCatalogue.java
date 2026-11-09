package com.wf.wflib.soldier;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.wf.wflib.armor.api.ArmorPieceItem;
import com.wf.wflib.armor.api.ArmorSetups;
import com.wf.wflib.armor.api.exo.ExoProfiles;
import com.wf.wflib.soldier.api.SoldierRole;
import com.wf.wflib.soldier.api.SoldierServices;
import com.wf.wflib.soldier.api.SoldierThrowables;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ItemStack;
import java.util.*;

/** Built once per editor session, not per frame or per search. Reuses the armor system's modular insert setups. */
public final class SoldierCatalogue {
    public record Choice(String key, String slot, ItemStack stack, String pack, boolean exo, Set<EquipmentSlot> covers) {
        public String label() {
            String extra=key.contains("+") ? " + " + key.substring(key.indexOf('+')+1) : "";
            return stack.getHoverName().getString()+extra;
        }
    }
    private final Map<String,Choice> choices=new LinkedHashMap<>();
    public Collection<Choice> all() { return Collections.unmodifiableCollection(choices.values()); }
    public Choice get(String id) { return choices.get(id); }
    private void add(String key,String slot,ItemStack stack,Set<EquipmentSlot> covers) {
        String base=key.split("\\+",2)[0]; int slash=base.indexOf('/');
        String pack=slash>=0 ? base.substring(0,slash) : base.substring(0,base.indexOf(':'));
        choices.put(key,new Choice(key,slot,stack,pack,isExoskeleton(base,stack),Set.copyOf(covers)));
    }
    private static boolean isExoskeleton(String id,ItemStack stack) {
        if (ExoProfiles.of(stack)!=null) return true;
        // Legacy converted packs identify exoskeleton garments by item ID but have no powered ExoSpec.
        String path=id.toLowerCase(Locale.ROOT);
        return path.contains("exosuit") || path.contains("exoskeleton") || path.contains("exokarkas")
                || java.util.regex.Pattern.compile("(^|[./_])exo([./_]|$)").matcher(path).find();
    }
    public static SoldierCatalogue create() {
        long start=System.nanoTime();
        var c=new SoldierCatalogue();
        if (SoldierServices.hasWeapon()) for (var id:SoldierServices.weapon().guns())
            if (SoldierServices.weapon().usable(id)) c.add(id.toString(),"gun",new ItemStack(BuiltInRegistries.ITEM.get(id)),Set.of());
        for (var setup:ArmorSetups.all()) if (!setup.dev()) c.add(setup.label(),setup.slot().getName(),setup.stack(),Set.copyOf(setup.covers()));
        for (var item:BuiltInRegistries.ITEM) {
            var id=BuiltInRegistries.ITEM.getKey(item); String key=id.toString();
            if (com.wf.wflib.armor.ArmorCatalogue.isDev(id)) continue;
            if (item instanceof ArmorItem a && a.getEquipmentSlot()!=EquipmentSlot.BODY && !c.choices.containsKey(key))
                c.add(key,a.getEquipmentSlot().getName(),new ItemStack(item),Set.of(a.getEquipmentSlot()));
            if (SoldierServices.hasThrowables() && SoldierServices.throwables().kind(id)!=SoldierThrowables.Kind.OTHER)
                c.add(key,"grenade",new ItemStack(item),Set.of());
        }
        com.mojang.logging.LogUtils.getLogger().info("Soldier editor catalogue: {} choices in {} ms",c.choices.size(),(System.nanoTime()-start)/1_000_000);
        return c;
    }
    public void validate(SoldierTemplate u) {
        require(u.gun(),"gun");
        for (String g:u.grenades()) require(g,"grenade");
        var covered=EnumSet.noneOf(EquipmentSlot.class); boolean exo=false;
        for (var e:u.armour().entrySet()) {
            Choice c=require(e.getValue(),e.getKey());
            if (!Set.of("head","chest","legs","feet").contains(c.slot)) throw new IllegalArgumentException("Not wearable armor");
            if (!Collections.disjoint(covered,c.covers)) throw new IllegalArgumentException("These uniform pieces occupy the same body slots");
            covered.addAll(c.covers); exo|=c.exo && c.slot.equals("chest");
        }
        if (u.role()==SoldierRole.HEAVY && !exo) throw new IllegalArgumentException("Equip an installed exoskeleton for the heavy role");
    }
    public Choice require(String key,String slot) {
        var c=choices.get(key);
        if (c==null || !c.slot.equals(slot)) throw new IllegalArgumentException("Equipment is unavailable for "+slot+": "+key);
        return c;
    }
    public JsonArray keys() { var a=new JsonArray(); choices.values().forEach(c->a.add(c.key)); return a; }
    public static EquipmentSlot slot(String name) {
        return switch(name) { case "head"->EquipmentSlot.HEAD; case "chest"->EquipmentSlot.CHEST; case "legs"->EquipmentSlot.LEGS; case "feet"->EquipmentSlot.FEET;
            default->throw new IllegalArgumentException("Choose head, chest, legs or feet"); };
    }
}
