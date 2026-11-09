package com.wf.wflib.soldier;

import com.mojang.brigadier.arguments.*;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.wf.wflib.soldier.api.SoldierRole;
import net.minecraft.commands.*;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import java.util.*;

/** Creative-only management, sharing the exact roster and validation used by the GUI. Console may manage definitions. */
final class SoldierEditorCommands {
    private SoldierEditorCommands() {}
    static void add(LiteralArgumentBuilder<CommandSourceStack> root) {
        root.then(Commands.literal("editor").requires(SoldierEditor::allowed).executes(c->run(c.getSource(),()->{
            SoldierEditor.open(c.getSource().getPlayerOrException()); return "Soldier editor opened";
        })));
        root.then(Commands.literal("faction").requires(SoldierEditor::allowed)
            .then(Commands.literal("list").executes(c->run(c.getSource(),()->SoldierRoster.get(c.getSource().getServer()).factions().values().toString())))
            .then(Commands.literal("add").then(Commands.argument("id",StringArgumentType.word())
                .then(Commands.argument("hostile_to_players",BoolArgumentType.bool()).executes(c->run(c.getSource(),()->{
                    String id=StringArgumentType.getString(c,"id"); SoldierEditor.faction(c.getSource().getServer(),new SoldierRoster.Faction(id,id,BoolArgumentType.getBool(c,"hostile_to_players"))); return "Saved faction "+id;
                })))))
            .then(Commands.literal("remove").then(Commands.argument("faction",StringArgumentType.word()).suggests((c,b)->SharedSuggestionProvider.suggest(SoldierRoster.get(c.getSource().getServer()).factions().keySet(),b))
                .executes(c->run(c.getSource(),()->{
                    String id=StringArgumentType.getString(c,"faction"); SoldierRoster.get(c.getSource().getServer()).removeFaction(id);
                    return "Deleted faction, variants and "+SoldierEditor.clear(c.getSource().getServer(),id)+" deployed soldiers";
                }))))
            .then(Commands.literal("spawn").then(Commands.argument("faction",StringArgumentType.word()).suggests((c,b)->SharedSuggestionProvider.suggest(SoldierRoster.get(c.getSource().getServer()).factions().keySet(),b))
                .then(Commands.argument("count",IntegerArgumentType.integer(1,40)).executes(c->run(c.getSource(),()->"Deployed "+SoldierEditor.spawnFaction(c.getSource().getPlayerOrException(),StringArgumentType.getString(c,"faction"),IntegerArgumentType.getInteger(c,"count"))+" faction soldiers")))))
            .then(Commands.literal("clear").then(Commands.argument("faction",StringArgumentType.word()).suggests((c,b)->SharedSuggestionProvider.suggest(SoldierRoster.get(c.getSource().getServer()).factions().keySet(),b))
                .executes(c->run(c.getSource(),()->"Removed "+SoldierEditor.clear(c.getSource().getServer(),StringArgumentType.getString(c,"faction"))+" deployed soldiers")))));
        var unit=Commands.literal("unit").requires(SoldierEditor::allowed);
        unit.then(Commands.literal("list").executes(c->run(c.getSource(),()->SoldierRoster.get(c.getSource().getServer()).units().keySet().toString())));
        unit.then(Commands.literal("create").then(Commands.argument("id",StringArgumentType.word())
            .then(Commands.argument("faction",StringArgumentType.word()).suggests((c,b)->SharedSuggestionProvider.suggest(SoldierRoster.get(c.getSource().getServer()).factions().keySet(),b))
            .then(Commands.argument("role",StringArgumentType.word()).suggests((c,b)->SharedSuggestionProvider.suggest(Arrays.stream(SoldierRole.values()).map(r->r.id),b))
            .then(Commands.argument("gun",StringArgumentType.greedyString()).suggests((c,b)->SharedSuggestionProvider.suggest(SoldierEditor.catalogue(c.getSource().getServer()).all().stream().filter(v->v.slot().equals("gun")).map(SoldierCatalogue.Choice::key),b))
            .executes(c->run(c.getSource(),()->{
                var role=SoldierRole.of(StringArgumentType.getString(c,"role")); String id=StringArgumentType.getString(c,"id");
                if(SoldierRoster.get(c.getSource().getServer()).units().containsKey(id))throw new IllegalArgumentException("Variant already exists: "+id);
                // Heavy variants are created by equipping an exoskeleton in the editor first.
                var armor=new LinkedHashMap<String,String>();
                if (role==SoldierRole.HEAVY) SoldierEditor.catalogue(c.getSource().getServer()).all().stream().filter(v->v.exo() && v.slot().equals("chest")).findFirst().ifPresent(v->armor.put(v.slot(),v.key()));
                SoldierEditor.save(c.getSource().getServer(),new SoldierTemplate(id,id,StringArgumentType.getString(c,"faction"),role,StringArgumentType.getString(c,"gun"),List.of(),armor,4,role.range,role.spread)); return "Created "+id;
            })))))));
        for (String action:List.of("remove","egg","spawn","rename","equip")) {
            var id=Commands.argument("variant",StringArgumentType.word()).suggests((c,b)->SharedSuggestionProvider.suggest(SoldierRoster.get(c.getSource().getServer()).units().keySet(),b));
            switch(action) {
                case "remove" -> id.executes(c->run(c.getSource(),()->{ SoldierRoster.get(c.getSource().getServer()).removeUnit(StringArgumentType.getString(c,"variant")); return "Variant deleted; its eggs are disabled"; }));
                case "egg" -> id.executes(c->run(c.getSource(),()->{
                    var u=unit(c.getSource(),StringArgumentType.getString(c,"variant")); var p=c.getSource().getPlayerOrException(); SoldierEditor.require(p);
                    var stack=SoldierEditorEgg.egg(u); if (!p.getInventory().add(stack)) p.drop(stack,false); return "Spawn egg added";
                }));
                case "spawn" -> { id.executes(c->run(c.getSource(),()->"Deployed "+SoldierEditor.spawnNear(c.getSource().getPlayerOrException(),StringArgumentType.getString(c,"variant"),1)+" soldiers"));
                    id.then(Commands.argument("count",IntegerArgumentType.integer(1,40)).executes(c->run(c.getSource(),()->"Deployed "+SoldierEditor.spawnNear(c.getSource().getPlayerOrException(),StringArgumentType.getString(c,"variant"),IntegerArgumentType.getInteger(c,"count"))+" soldiers"))); }
                case "rename" -> id.then(Commands.argument("name",StringArgumentType.greedyString()).executes(c->run(c.getSource(),()->{
                    var u=unit(c.getSource(),StringArgumentType.getString(c,"variant")); SoldierEditor.save(c.getSource().getServer(),new SoldierTemplate(u.id(),StringArgumentType.getString(c,"name"),u.faction(),u.role(),u.gun(),u.grenades(),u.armour(),u.spares(),u.range(),u.spread())); return "Variant renamed";
                })));
                case "equip" -> id.then(Commands.argument("slot",StringArgumentType.word()).suggests((c,b)->SharedSuggestionProvider.suggest(List.of("gun","head","chest","legs","feet"),b))
                    .then(Commands.argument("equipment",StringArgumentType.greedyString()).suggests((c,b)->SharedSuggestionProvider.suggest(SoldierEditor.catalogue(c.getSource().getServer()).all().stream().filter(v->v.slot().equals(StringArgumentType.getString(c,"slot"))).map(SoldierCatalogue.Choice::key),b))
                    .executes(c->run(c.getSource(),()->{
                        var u=unit(c.getSource(),StringArgumentType.getString(c,"variant")); String slot=StringArgumentType.getString(c,"slot"), gear=StringArgumentType.getString(c,"equipment");
                        var armor=new LinkedHashMap<>(u.armour()); if (!slot.equals("gun")) { if (gear.equals("none")) armor.remove(slot); else armor.put(slot,gear); }
                        SoldierEditor.save(c.getSource().getServer(),new SoldierTemplate(u.id(),u.name(),u.faction(),u.role(),slot.equals("gun")?gear:u.gun(),u.grenades(),armor,u.spares(),u.range(),u.spread())); return "Equipment updated";
                    }))));
            }
            unit.then(Commands.literal(action).then(id));
        }
        root.then(unit);
    }
    private static SoldierTemplate unit(CommandSourceStack s,String id) { var u=SoldierRoster.get(s.getServer()).units().get(id); if (u==null) throw new IllegalArgumentException("Unknown variant: "+id); return u; }
    @FunctionalInterface private interface Action { String run() throws Exception; }
    private static int run(CommandSourceStack s,Action action) {
        if (!SoldierEditor.allowed(s)) { s.sendFailure(Component.literal("Creative mode required")); return 0; }
        try { String result=action.run(); s.sendSuccess(()->Component.literal(result),false); return 1; }
        catch (Exception e) { s.sendFailure(Component.literal(e.getMessage()==null ? "Editor action failed" : e.getMessage())); return 0; }
    }
}
