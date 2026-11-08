package com.wf.wflib.soldier;

import com.wf.wflib.soldier.api.SoldierRole;
import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class SoldierEditorTest {
    private SoldierTemplate variant(String id,String faction,SoldierRole role) {
        return new SoldierTemplate(id,"Monolith "+role.title,faction,role,"ma_rifles:akm",List.of("ma_grenades:m67"),Map.of("head","mwarchive:monolith/helmet"),4,role.range,role.spread);
    }
    @Test void savedRosterSurvivesReloadAndFactionDeletionInvalidatesItsVariants() {
        var r=new SoldierRoster(); r.put(new SoldierRoster.Faction("monolith","Monolith",true));
        r.put(variant("monolith_rifle","monolith",SoldierRole.RIFLEMAN));
        r.put(variant("friendly_medic","friendly",SoldierRole.MEDIC));
        var tag=r.save(new CompoundTag(),null);var loaded=SoldierRoster.load(tag);
        assertEquals(r.json(),loaded.json());assertFalse(loaded.isDirty());
        loaded.removeFaction("monolith");assertFalse(loaded.units().containsKey("monolith_rifle"));
        assertTrue(loaded.units().containsKey("friendly_medic"));assertTrue(loaded.isDirty());
        assertThrows(IllegalArgumentException.class,()->loaded.put(variant("bad","deleted",SoldierRole.RIFLEMAN)));
    }
    @Test void malformedAndExcessiveDefinitionsAreRefusedBeforeSaving() {
        assertThrows(IllegalArgumentException.class,()->SoldierTemplate.key("../../world"));
        assertThrows(IllegalArgumentException.class,()->SoldierTemplate.key("A faction"));
        assertThrows(IllegalArgumentException.class,()->new SoldierRoster.Faction("ok","\n",true));
        assertThrows(IllegalArgumentException.class,()->new SoldierTemplate("ok","Test","friendly",SoldierRole.SNIPER,"ma:a",List.of(),Map.of(),4,100,Float.NaN));
        assertThrows(IllegalArgumentException.class,()->new SoldierTemplate("ok","Test","friendly",SoldierRole.SNIPER,"ma:a",Collections.nCopies(9,"ma:grenade"),Map.of(),4,100,1));
        var u=variant("one","friendly",SoldierRole.MEDIC); assertEquals(u,SoldierTemplate.read(u.json()));
        assertThrows(UnsupportedOperationException.class,()->u.armour().put("feet","test"));
    }
    @Test void soldiersStandAtEaseWithoutAThreatEvenAfterOccupyingCover() {
        assertFalse(BotPolicy.crouch(true,false,false,false,false,true));
        assertFalse(BotPolicy.crouch(true,false,false,false,true,true));
        assertTrue(BotPolicy.crouch(true,true,false,false,true,true));
        assertFalse(BotPolicy.crouch(true,true,true,false,true,true),"exposing a peek stands up");
        assertFalse(BotPolicy.crouch(true,true,false,true,false,true),"running between cover is upright");
        assertTrue(BotPolicy.crouch(false,false,false,false,false,true),"explicit filming posture remains available");
    }
    @Test void largePackCataloguesRoundTripInsideThePayloadLimit() {
        var j=new com.google.gson.JsonArray();
        for(int i=0;i<15000;i++)j.add("mwarchive:large_equipment_pack/armor/modular_uniform_variant_"+i);
        String json=j.toString();assertTrue(json.length()>262144);
        var buf=new net.minecraft.network.FriendlyByteBuf(io.netty.buffer.Unpooled.buffer());
        try {
            SoldierEditorPackets.State.CODEC.encode(buf,new SoldierEditorPackets.State(json));
            assertTrue(buf.readableBytes()<524288);
            assertEquals(json,SoldierEditorPackets.State.CODEC.decode(buf).json());
        } finally {buf.release();}
        var tooLarge=new net.minecraft.network.FriendlyByteBuf(io.netty.buffer.Unpooled.buffer());
        try {assertThrows(IllegalArgumentException.class,()->SoldierEditorPackets.State.CODEC.encode(tooLarge,new SoldierEditorPackets.State("x".repeat(2097153))));}
        finally {tooLarge.release();}
    }
    @Test void resourceIdsAndModularEquipmentAreAcceptedByTheActualCommandNodes() throws Exception {
        var root=net.minecraft.commands.Commands.literal("soldiers");SoldierEditorCommands.add(root);var node=root.build().getChild("unit");
        var gun=(com.mojang.brigadier.tree.ArgumentCommandNode<?,?>)node.getChild("create").getChild("id").getChild("faction").getChild("role").getChild("gun");
        assertEquals("ma_rifles:m4a1",gun.getType().parse(new com.mojang.brigadier.StringReader("ma_rifles:m4a1")));
        var armor=(com.mojang.brigadier.tree.ArgumentCommandNode<?,?>)node.getChild("equip").getChild("variant").getChild("slot").getChild("equipment");
        assertEquals("mwarchive:pack/vest+wflib:steel_platex2",armor.getType().parse(new com.mojang.brigadier.StringReader("mwarchive:pack/vest+wflib:steel_platex2")));
    }
    @Test void rolesHaveDistinctFireAndSquadDoctrineWithoutDisplacingTheRifleDefault() {
        assertTrue(SoldierRole.MACHINEGUNNER.burst>SoldierRole.RIFLEMAN.burst);
        assertEquals(1,SoldierRole.SNIPER.burst);assertTrue(SoldierRole.SNIPER.spread<SoldierRole.RIFLEMAN.spread);
        assertTrue(SoldierRole.SNIPER.standOff>SoldierRole.MEDIC.standOff);
        assertTrue(SoldierRole.HEAVY.movePriority()<SoldierRole.MACHINEGUNNER.movePriority());
        var members=List.of(new BotSquad.Member("sniper","sniper",0,true),new BotSquad.Member("rifle","rifle",0,true),new BotSquad.Member("assault","assault",0,true));
        assertEquals(List.of("assault","rifle"),BotSquad.movers(members,2));
        for(var role:SoldierRole.values())assertEquals(role,SoldierRole.of(role.id));
        assertEquals(SoldierRole.RIFLEMAN,SoldierRole.tactics("custom filming role"));
    }
}
