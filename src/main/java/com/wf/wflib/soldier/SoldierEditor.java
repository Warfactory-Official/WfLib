package com.wf.wflib.soldier;

import com.google.gson.JsonObject;
import com.wf.wflib.soldier.api.*;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.phys.Vec3;
import java.util.*;

/** All editor actions (GUI, commands, eggs) cross this server-side authorization/validation boundary. */
public final class SoldierEditor {
    private static final Map<MinecraftServer,SoldierCatalogue> CATALOGUES=new WeakHashMap<>();
    private SoldierEditor() {}
    public static boolean allowed(CommandSourceStack s) { return s.getEntity() instanceof ServerPlayer p ? p.isCreative() && !SoldierAi.scripted(p) : s.hasPermission(2); }
    public static void require(ServerPlayer p) { if (!p.isCreative() || SoldierAi.scripted(p)) throw new IllegalArgumentException("The soldier editor requires Creative mode"); }
    public static SoldierCatalogue catalogue(MinecraftServer s) { return CATALOGUES.computeIfAbsent(s, k->SoldierCatalogue.create()); }
    static void forget(MinecraftServer s) { CATALOGUES.remove(s); }
    public static void open(ServerPlayer p) { require(p); SoldierEditorPackets.send(p,"",true); }
    public static void save(MinecraftServer s,SoldierTemplate u) { catalogue(s).validate(u); SoldierRoster.get(s).put(u); }
    public static void faction(MinecraftServer s,SoldierRoster.Faction f) {
        SoldierRoster.get(s).put(f);
        for (var p:s.getPlayerList().getPlayers()) {
            var b=SoldierAi.botOf(p.getGameProfile().getName());
            if (p instanceof SoldierPlayer && b!=null && b.team.equals(f.id())) { b.huntsPlayers=f.hostile(); b.threats.clear(); b.glimpses.clear(); }
        }
    }
    public static int clear(MinecraftServer s,String faction) {
        int n=0;
        for (var p:new ArrayList<>(s.getPlayerList().getPlayers())) {
            var b=SoldierAi.botOf(p.getGameProfile().getName());
            if (p instanceof SoldierPlayer && b!=null && b.team.equals(faction)) { Soldiers.remove(p); n++; }
        }
        return n;
    }
    public static ServerPlayer spawn(ServerPlayer owner,String id,Vec3 feet,float yaw) {
        require(owner); var s=owner.getServer(); var r=SoldierRoster.get(s); var u=r.units().get(id);
        if (u==null || !r.factions().containsKey(u.faction())) throw new IllegalArgumentException("This saved variant or faction no longer exists");
        var c=catalogue(s); c.validate(u);
        var box=new net.minecraft.world.phys.AABB(feet.x-.3,feet.y,feet.z-.3,feet.x+.3,feet.y+1.8,feet.z+.3);
        if (!owner.serverLevel().noCollision(box) || !owner.serverLevel().getWorldBorder().isWithinBounds(BlockPos.containing(feet)))
            throw new IllegalArgumentException("The soldier needs two clear blocks inside the world border");
        if (!owner.serverLevel().hasChunkAt(BlockPos.containing(feet)) || feet.y<owner.level().getMinBuildHeight() || feet.y+2>=owner.level().getMaxBuildHeight())
            throw new IllegalArgumentException("Choose a loaded position within build height");
        var f=r.factions().get(u.faction()); var spec=new SoldierSpec(f.id(),u.name(),f.hostile(),ResourceLocation.parse(u.gun()),
                u.grenades().stream().map(ResourceLocation::parse).toList(),Map.of(),u.spares(),u.role());
        var p=Soldiers.spawn(owner.serverLevel(),feet,yaw,spec,owner);
        if (p==null) throw new IllegalArgumentException("Soldier limit reached ("+Soldiers.CAP+")");
        for (var e:u.armour().entrySet()) p.setItemSlot(SoldierCatalogue.slot(e.getKey()),c.get(e.getValue()).stack().copy());
        if (u.role()==SoldierRole.HEAVY) {
            p.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.MAX_HEALTH).setBaseValue(40);
            p.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.KNOCKBACK_RESISTANCE).setBaseValue(.6);
            p.setHealth(40);
        }
        p.setCustomName(Component.literal(u.name())); p.setCustomNameVisible(true);
        var bot=SoldierAi.botOf(p.getGameProfile().getName()); bot.range=u.range(); bot.spreadDeg=u.spread();
        return p;
    }
    public static int spawnFaction(ServerPlayer p,String faction,int count) {
        require(p); if(count<1 || count>Soldiers.CAP)throw new IllegalArgumentException("Count must be 1–"+Soldiers.CAP);
        var units=SoldierRoster.get(p.getServer()).units().values().stream().filter(u->u.faction().equals(faction)).toList();
        if(units.isEmpty())throw new IllegalArgumentException("Save at least one variant in this faction first");
        for(var u:units)catalogue(p.getServer()).validate(u);
        int capacity=Soldiers.CAP-SoldierAi.soldiers(); if(capacity<=0)throw new IllegalArgumentException("Soldier limit reached");
        int made=0; for(int i=0;i<Math.min(count,capacity);i++)made+=spawnNear(p,units.get(i%units.size()).id(),1);
        return made;
    }
    /** GUI/command spawning stays on the caller's storey and verifies feet support, rather than using the roof heightmap. */
    public static int spawnNear(ServerPlayer p,String id,int count) {
        require(p); if (count<1 || count>Soldiers.CAP) throw new IllegalArgumentException("Count must be 1–"+Soldiers.CAP);
        count=Math.min(count,Soldiers.CAP-SoldierAi.soldiers());
        if (count<=0) throw new IllegalArgumentException("Soldier limit reached ("+Soldiers.CAP+")");
        int made=0;
        for (int ring=1;ring<=5 && made<count;ring++) for (int dx=-ring;dx<=ring && made<count;dx++) for (int dz=-ring;dz<=ring && made<count;dz++) {
            if (Math.max(Math.abs(dx),Math.abs(dz))!=ring) continue;
            BlockPos at=p.blockPosition().offset(dx*2,0,dz*2);
            if (!p.serverLevel().hasChunkAt(at)) continue;
            for (int dy:new int[]{0,-1,1}) {
                var q=at.above(dy); if (!p.serverLevel().getBlockState(q.below()).isCollisionShapeFullBlock(p.level(),q.below())) continue;
                var feet=Vec3.atBottomCenterOf(q); if (!p.serverLevel().noCollision(new net.minecraft.world.phys.AABB(feet.x-.3,feet.y,feet.z-.3,feet.x+.3,feet.y+1.8,feet.z+.3))) continue;
                spawn(p,id,feet,p.getYRot()); made++; break;
            }
        }
        if (made==0) throw new IllegalArgumentException("No clear, supported spawn position nearby"); return made;
    }
}
