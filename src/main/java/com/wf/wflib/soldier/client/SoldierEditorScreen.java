package com.wf.wflib.soldier.client;

import com.google.gson.*;
import com.mojang.authlib.GameProfile;
import com.wf.wflib.soldier.*;
import com.wf.wflib.soldier.api.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.*;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.ConfirmScreen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.player.RemotePlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.lwjgl.glfw.GLFW;
import java.util.*;
import java.util.function.DoubleConsumer;

/** Search/scroll work touches only cached choices. No registry walk, network request, or model rebuild per frame. */
public final class SoldierEditorScreen extends Screen {
    private static final int BG=0xff10161f, PANEL=0xff192330, INK=0xffe4edf7, MUTED=0xff9eafc1, ACCENT=0xff6ed5bd;
    private JsonObject roster;
    private final SoldierCatalogue catalogue;
    private final Set<String> available;
    private final Map<String,String> searchable=new HashMap<>();
    private final Map<String,String> armour=new LinkedHashMap<>();
    private SoldierRole role=SoldierRole.RIFLEMAN;
    private String faction="", gun="", frag="", smoke="", unitId="", unitName="";
    private int spares=4, range=80, fragCount=1, smokeCount=1, count=1;
    private float spread=2, rotation=-20;
    private int left,top,w,h,lw,mx,mw,px,pw,unitOffset,choiceOffset;
    private String page="loadout", modal="", query="", pack="All packs", status="", unitQuery="";
    private boolean pending, dirty, existing, previewDirty=true;
    private RemotePlayer preview;
    private String previewFor="", pendingAction="";
    private int detailOffset;
    private final Map<AbstractWidget,Integer> details=new LinkedHashMap<>();
    private EditBox nameField,idField,focusNext;
    private List<SoldierTemplate> filteredUnits=List.of();
    private List<SoldierCatalogue.Choice> filteredChoices=List.of();
    private final List<Button> unitRows=new ArrayList<>(), choiceRows=new ArrayList<>();
    private String factionEditId="",factionEditName="";
    private boolean factionHostile;

    public static void receive(String json) {
        Minecraft mc=Minecraft.getInstance(); if (mc.player==null || !mc.player.isCreative()) return;
        var j=JsonParser.parseString(json).getAsJsonObject();
        if (mc.screen instanceof SoldierEditorScreen s) {
            s.roster=j; s.pending=false; s.status=j.get("message").getAsString();
            boolean missingFaction=!s.factions().containsKey(s.faction);
            if (missingFaction) s.faction=s.factions().keySet().stream().findFirst().orElse("");
            if (missingFaction || s.status.startsWith("Variant removed")) {
                var first=s.units().stream().filter(u->u.faction().equals(s.faction)).findFirst();
                if(first.isPresent())s.load(first.get());else s.fresh();
                s.rebuild();
            }
            s.filterUnits();
            if (Set.of("save","egg","spawn").contains(s.pendingAction) && (s.status.startsWith("Saved ") || s.status.startsWith("Spawn egg") || s.status.startsWith("Deployed"))) { s.dirty=false; s.existing=true; if(s.idField!=null)s.idField.setEditable(false); }
            s.pendingAction="";
            if (s.status.startsWith("Faction saved")) { s.faction=s.factionEditId; s.modal=""; s.rebuild(); }
        } else if (j.get("open").getAsBoolean()) mc.setScreen(new SoldierEditorScreen(j));
    }
    private SoldierEditorScreen(JsonObject roster) {
        super(Component.literal("Soldier workshop")); this.roster=roster; catalogue=SoldierCatalogue.create();
        available=new HashSet<>(); roster.getAsJsonArray("catalogue").forEach(e->available.add(e.getAsString()));
        for (var c:catalogue.all()) searchable.put(c.key(),(c.key()+" "+c.label()+" "+c.pack()).toLowerCase(Locale.ROOT));
        faction=factions().keySet().stream().findFirst().orElse("");
        var u=units().stream().filter(v->v.faction().equals(faction)).findFirst(); if (u.isPresent()) load(u.get()); else fresh();
    }
    private Map<String,SoldierRoster.Faction> factions() {
        var out=new LinkedHashMap<String,SoldierRoster.Faction>();
        for (var e:roster.getAsJsonArray("factions")) { var j=e.getAsJsonObject(); var f=new SoldierRoster.Faction(j.get("id").getAsString(),j.get("name").getAsString(),j.get("hostile").getAsBoolean()); out.put(f.id(),f); }
        return out;
    }
    private List<SoldierTemplate> units() { return roster.getAsJsonArray("units").asList().stream().map(e->SoldierTemplate.read(e.getAsJsonObject())).toList(); }
    private void load(SoldierTemplate u) {
        unitId=u.id(); unitName=u.name(); faction=u.faction(); role=u.role(); gun=u.gun(); armour.clear(); armour.putAll(u.armour());
        spares=u.spares(); range=u.range(); spread=u.spread(); frag=""; smoke=""; fragCount=0; smokeCount=0;
        for (var id:u.grenades()) {
            if (SoldierServices.hasThrowables() && SoldierServices.throwables().kind(net.minecraft.resources.ResourceLocation.parse(id))==SoldierThrowables.Kind.SMOKE) { smoke=id; smokeCount++; }
            else { frag=id; fragCount++; }
        }
        dirty=false; existing=true; previewDirty=true;
    }
    private void fresh() {
        Set<String> ids=new HashSet<>(); units().forEach(u->ids.add(u.id())); int n=1; while(ids.contains("soldier_"+n)) n++;
        unitId="soldier_"+n; unitName="Rifleman "+n; armour.clear(); gun=""; role=SoldierRole.RIFLEMAN;
        var side=SoldierServices.spec(faction);
        if (side!=null) side.armour().forEach((s,id)->{ if (available.contains(id.toString())) armour.put(s.getName(),id.toString()); });
        defaults(role); dirty=false; existing=false;
    }
    private void defaults(SoldierRole r) {
        role=r; range=r.range; spread=r.spread; spares=r==SoldierRole.MACHINEGUNNER?8:4;
        String[] prefer=switch(r) {
            case SNIPER->new String[]{"svd","m24","awp","mosin","m107","scar_h"};
            case MACHINEGUNNER,HEAVY->new String[]{"pkm","m249","rpk","m240","mg3","m60"};
            case ASSAULT->new String[]{"mk18","mp5","aks_74u","hk416"};
            default->new String[]{"m4a1","ak_74","akm","hk416","scar"};
        };
        var guns=catalogue.all().stream().filter(c->c.slot().equals("gun") && available.contains(c.key())).toList();
        gun=guns.stream().min(Comparator.comparingInt(c->{ for(int i=0;i<prefer.length;i++) if(c.key().contains(prefer[i])) return i; return 100; })).map(SoldierCatalogue.Choice::key).orElse("");
        var grenades=catalogue.all().stream().filter(c->c.slot().equals("grenade") && available.contains(c.key())).toList();
        frag=grenades.stream().filter(c->SoldierServices.throwables().kind(net.minecraft.resources.ResourceLocation.parse(c.key()))==SoldierThrowables.Kind.FRAG).map(SoldierCatalogue.Choice::key).findFirst().orElse("");
        smoke=grenades.stream().filter(c->SoldierServices.throwables().kind(net.minecraft.resources.ResourceLocation.parse(c.key()))==SoldierThrowables.Kind.SMOKE).map(SoldierCatalogue.Choice::key).findFirst().orElse("");
        fragCount=r==SoldierRole.GRENADIER?3:1; smokeCount=r==SoldierRole.MEDIC?2:1;
        if (r==SoldierRole.HEAVY) catalogue.all().stream().filter(c->c.exo() && c.slot().equals("chest") && available.contains(c.key())).findFirst().ifPresent(c->{
            wear(c); var legs=catalogue.get(c.key().replace("_chest","_legs"));
            if (legs!=null && legs.slot().equals("legs") && !c.covers().contains(EquipmentSlot.LEGS)) wear(legs);
        });
        touch();
    }
    private void touch() { dirty=true; previewDirty=true; }
    private void wear(SoldierCatalogue.Choice c) {
        armour.entrySet().removeIf(e->{ var old=catalogue.get(e.getValue()); return old!=null && !Collections.disjoint(old.covers(),c.covers()); });
        armour.put(c.slot(),c.key()); touch();
    }
    @Override protected void init() {
        w=Math.min(width-16,940); h=Math.min(height-16,490); left=(width-w)/2; top=(height-h)/2;
        lw=Math.max(100,(int)(w*.23)); mw=(int)(w*.43); mx=left+lw+8; px=mx+mw+8; pw=left+w-px;
        rebuild();
    }
    private Button button(String text,int x,int y,int width,Runnable run) {
        var b=Button.builder(Component.literal(text),v->run.run()).bounds(x,y,width,20).build(); addRenderableWidget(b); return b;
    }
    private EditBox field(String value,int x,int y,int width,int limit,java.util.function.Consumer<String> change) {
        var e=new EditBox(font,x,y,width,18,Component.literal("Text")); e.setMaxLength(limit); e.setValue(value); e.setResponder(change); addRenderableWidget(e); return e;
    }
    private void rebuild() {
        clearWidgets(); unitRows.clear(); choiceRows.clear(); details.clear();
        if (modal.equals("faction")) { factionForm(); return; }
        if (modal.equals("roles")) { roleForm(); return; }
        if (!modal.isEmpty()) { picker(); return; }
        var factions=factions(); var f=factions.get(faction);
        button(f==null?"Choose faction":fit(f.name(),lw-12),left,top+29,lw,()->switchDraft(()->{
            var ids=new ArrayList<>(factions().keySet()); if (ids.isEmpty()) return;
            faction=ids.get((ids.indexOf(faction)+1)%ids.size()); unitOffset=0; filterUnits();
            var first=filteredUnits.stream().findFirst(); if(first.isPresent()) load(first.get()); else fresh(); rebuild();
        })).setTooltip(Tooltip.create(Component.literal("Click to cycle factions · Same faction = allies; different factions = enemies")));
        button("+ Faction",left,top+52,lw/2-2,()->{ factionEditId=""; factionEditName=""; factionHostile=false; modal="faction"; rebuild(); });
        button("Edit",left+lw/2+2,top+52,lw/2-2,()->{ if(f==null)return; factionEditId=f.id(); factionEditName=f.name(); factionHostile=f.hostile(); modal="faction"; rebuild(); });
        field(unitQuery,left,top+78,lw,64,s->{unitQuery=s;unitOffset=0;filterUnits();}).setHint(Component.literal("Search variants…"));
        int rows=Math.max(1,(h-181)/22);
        for (int i=0;i<rows;i++) { int index=i; unitRows.add(button("",left,top+101+i*22,lw,()->{ int at=unitOffset+index; if(at<filteredUnits.size()) { var u=filteredUnits.get(at); switchDraft(()->{load(u);rebuild();}); } })); }
        button("+ New variant",left,top+h-74,lw,()->switchDraft(()->{fresh();rebuild();}));
        button("Deploy faction",left,top+h-51,lw,()->{var d=new JsonObject();d.addProperty("id",faction);d.addProperty("count",count);send("spawn_faction",d);})
            .setTooltip(Tooltip.create(Component.literal("Deploy saved variants in rotation, using the Deploy count slider")));
        button("Clear deployed",left,top+h-28,lw,()->confirm("Remove all deployed soldiers in this faction? Saved variants stay.",()->{var d=new JsonObject();d.addProperty("id",faction);send("clear_faction",d);}));
        idField=field(unitId,mx,top+30,mw,32,s->{unitId=s;touch();}); idField.setTooltip(Tooltip.create(Component.literal("Stable variant ID for commands and spawn eggs"))); idField.setEditable(!existing);
        nameField=field(unitName,mx,top+55,mw,48,s->{unitName=s;touch();}); nameField.setHint(Component.literal("Soldier display name"));
        button("Role: "+role.title,mx,top+78,mw,()->{modal="roles";rebuild();}).setTooltip(Tooltip.create(Component.literal(role.description)));
        button("Equipment",mx,top+101,mw/2-2,()->{page="loadout";detailOffset=0;rebuild();});
        button("Tactics",mx+mw/2+2,top+101,mw/2-2,()->{page="tactics";detailOffset=0;rebuild();});
        if(page.equals("loadout")) {
            String[] slots={"gun","head","chest","legs","feet","frag","smoke"};
            int row=Math.max(18,Math.min(25,(h-155)/7));
            for(int i=0;i<slots.length;i++) { String slot=slots[i]; String selected=selected(slot); var choice=catalogue.get(selected);
                var b=button(fit(title(slot)+": "+(choice==null?"None":choice.label()),mw-12),mx,top+126+i*row,mw,()->{modal=slot;query="";pack="All packs";choiceOffset=0;rebuild();});
                b.setHeight(row-2); details.put(b,b.getY()); b.setTooltip(Tooltip.create(Component.literal(choice==null?"Choose from installed packs":choice.label()+"\n"+choice.key())));
            }
        } else {
            int y=top+126;
            slider("Spare magazines",mx,y,mw,0,12,spares,true,v->{spares=(int)v;touch();});
            slider("Sight range",mx,y+24,mw,20,160,range,true,v->{range=(int)v;touch();});
            slider("Aim spread °",mx,y+48,mw,.25,6,spread,false,v->{spread=(float)v;touch();});
            slider("Frag grenades",mx,y+72,mw,0,6,fragCount,true,v->{fragCount=(int)v;touch();});
            slider("Smoke grenades",mx,y+96,mw,0,2,smokeCount,true,v->{smokeCount=(int)v;touch();});
        }
        layoutDetails();
        button("Delete variant",mx,top+h-28,mw,()->confirm("Delete this variant? Its spawn eggs will stop working.",()->{var d=new JsonObject();d.addProperty("id",unitId);send("delete_unit",d);}));
        slider("Rotate preview",px+4,top+h-127,pw-8,-180,180,rotation,true,v->rotation=(float)v);
        slider("Deploy count",px+4,top+h-104,pw-8,1,40,count,true,v->count=(int)v);
        button("Save variant",px+4,top+h-80,pw-8,()->save("save"));
        button("Get spawn egg",px+4,top+h-60,pw-8,()->save("egg"));
        button("Deploy nearby",px+4,top+h-40,pw-8,()->save("spawn"));
        button("Done",px+4,top+h-20,pw-8,this::onClose);
        for(var widget:renderables) if(widget instanceof Button b && b.getX()>=px) b.setHeight(18);
        filterUnits();
    }
    private static String title(String s) { return switch(s) {case "gun"->"Primary";case "head"->"Head";case "chest"->"Torso";case "legs"->"Legs";case "feet"->"Boots";case "frag"->"Frag";case "smoke"->"Smoke";default->s;}; }
    private String fit(String s,int pixels) { return font.width(s)<=pixels?s:font.plainSubstrByWidth(s,Math.max(1,pixels-8))+"…"; }
    private String selected(String slot) { return switch(slot) {case "gun"->gun;case "frag"->frag;case "smoke"->smoke;default->armour.getOrDefault(slot,"");}; }
    private void filterUnits() {
        String q=unitQuery.toLowerCase(Locale.ROOT);
        filteredUnits=units().stream().filter(u->u.faction().equals(faction) && (u.name()+" "+u.id()+" "+u.role().title).toLowerCase(Locale.ROOT).contains(q)).toList();
        unitOffset=Math.max(0,Math.min(unitOffset,Math.max(0,filteredUnits.size()-unitRows.size())));
        for(int i=0;i<unitRows.size();i++) { var b=unitRows.get(i); int at=unitOffset+i; b.visible=at<filteredUnits.size(); if(b.visible) {var u=filteredUnits.get(at);b.setMessage(Component.literal(fit((u.id().equals(unitId)?"› ":"")+u.name(),lw-12)));b.setTooltip(Tooltip.create(Component.literal(u.role().title+" · "+u.id())));} }
    }
    private void picker() {
        int x=left+8, y=top+31, width=px-left-16;
        button("‹ Back",x,y,74,()->{modal="";rebuild();});
        var choices=catalogue.all().stream().filter(c->available.contains(c.key()) && matchesSlot(c)).toList();
        var packs=new ArrayList<String>(); packs.add("All packs");choices.stream().map(SoldierCatalogue.Choice::pack).distinct().sorted().forEach(packs::add);
        button(fit(pack,Math.max(30,width/3-12)),x+80,y,width/3,()->{pack=packs.get((packs.indexOf(pack)+1)%packs.size());choiceOffset=0;rebuild();});
        button("Unequip",x+width-86,y,86,()->{if(modal.equals("gun"))gun="";else if(modal.equals("frag"))frag="";else if(modal.equals("smoke"))smoke="";else armour.remove(modal);touch();modal="";rebuild();});
        var search=field(query,x,y+26,width,128,s->{query=s;choiceOffset=0;filterChoices();});search.setHint(Component.literal("Search name, pack or item ID…"));
        int rows=Math.max(1,(h-102)/26);
        for(int i=0;i<rows;i++) {int index=i;choiceRows.add(button("",x,y+52+i*26,width,()->{
            int at=choiceOffset+index; if(at>=filteredChoices.size())return;var c=filteredChoices.get(at);
            switch(modal){case "gun"->gun=c.key();case "frag"->frag=c.key();case "smoke"->smoke=c.key();default->wear(c);}
            touch();modal="";rebuild();
        }));}
        slider("Rotate preview",px+4,top+h-28,pw-8,-180,180,rotation,true,v->rotation=(float)v);
        filterChoices(); focusNext=search;
    }
    private boolean matchesSlot(SoldierCatalogue.Choice c) {
        if(modal.equals("frag") || modal.equals("smoke")) return c.slot().equals("grenade") && SoldierServices.hasThrowables()
            && SoldierServices.throwables().kind(net.minecraft.resources.ResourceLocation.parse(c.key()))==(modal.equals("smoke")?SoldierThrowables.Kind.SMOKE:SoldierThrowables.Kind.FRAG);
        return c.slot().equals(modal);
    }
    private void filterChoices() {
        String q=query.toLowerCase(Locale.ROOT);
        filteredChoices=catalogue.all().stream().filter(c->available.contains(c.key()) && matchesSlot(c) && (pack.equals("All packs") || c.pack().equals(pack)) && searchable.get(c.key()).contains(q)).toList();
        choiceOffset=Math.max(0,Math.min(choiceOffset,Math.max(0,filteredChoices.size()-choiceRows.size())));
        for(int i=0;i<choiceRows.size();i++){var b=choiceRows.get(i);int at=choiceOffset+i;b.visible=at<filteredChoices.size();if(b.visible){var c=filteredChoices.get(at);b.setMessage(Component.literal(fit(c.label()+"   ["+c.pack()+"]",b.getWidth()-44)));b.setTooltip(Tooltip.create(Component.literal(c.key()+(c.exo()?"\nExoskeleton":""))));}}
    }
    private void roleForm() {
        button("‹ Back",mx,top+30,mw,()->{modal="";rebuild();});int y=top+56; int roleRow=Math.min(25,(h-70)/7);
        for(var r:SoldierRole.values()) {var b=button(r.title,mx,y,mw,()->{defaults(r);modal="";rebuild();});b.setTooltip(Tooltip.create(Component.literal(r.description+"\nApplies role weapon, ammunition and grenade defaults")));y+=roleRow;}
    }
    private void factionForm() {
        int x=left+Math.max(12,w/5), width=Math.min(360,w-32),y=top+43;
        button("‹ Back",x,y,width,()->{modal="";rebuild();});
        field(factionEditId,x,y+40,width,32,s->factionEditId=s).setHint(Component.literal("Faction ID (lowercase)"));
        field(factionEditName,x,y+67,width,48,s->factionEditName=s).setHint(Component.literal("Faction display name"));
        button("Players: "+(factionHostile?"HOSTILE":"FRIENDLY"),x,y+94,width,()->{factionHostile=!factionHostile;rebuild();});
        button("Save faction",x,y+121,width,()->{var d=new JsonObject();d.addProperty("id",factionEditId);d.addProperty("name",factionEditName);d.addProperty("hostile",factionHostile);send("save_faction",d);});
        button("Delete faction",x,y+148,width,()->confirm("Delete this faction, all its variants and deployed soldiers?",()->{var d=new JsonObject();d.addProperty("id",factionEditId);modal="";send("delete_faction",d);rebuild();}));
    }
    private void slider(String label,int x,int y,int width,double min,double max,double current,boolean integral,DoubleConsumer consumer) {
        var slider=new AbstractSliderButton(x,y,width,20,Component.empty(),(current-min)/(max-min)) {
            { updateMessage(); }
            @Override protected void updateMessage() {double v=min+value*(max-min);setMessage(Component.literal(label+": "+(integral?Integer.toString((int)Math.round(v)):String.format(Locale.ROOT,"%.2f",v))));}
            @Override protected void applyValue() {double v=min+value*(max-min);consumer.accept(integral?Math.round(v):v);}
        };
        addRenderableWidget(slider);
        if(x==mx && y>=top+126) details.put(slider,y);
    }
    private void layoutDetails() {
        int bottom=top+h-34;
        int end=details.entrySet().stream().mapToInt(e->e.getValue()+e.getKey().getHeight()).max().orElse(top+126);
        detailOffset=Math.max(0,Math.min(detailOffset,Math.max(0,end-bottom)));
        details.forEach((widget,y)->{widget.setY(y-detailOffset);widget.visible=widget.getY()>=top+126 && widget.getY()+widget.getHeight()<=bottom;});
    }
    private SoldierTemplate draft() {
        var g=new ArrayList<String>(); if(!frag.isEmpty())for(int i=0;i<fragCount;i++)g.add(frag);if(!smoke.isEmpty())for(int i=0;i<smokeCount;i++)g.add(smoke);
        var u=new SoldierTemplate(unitId,unitName,faction,role,gun,g,armour,spares,range,spread); catalogue.validate(u); return u;
    }
    private void save(String action) {
        try { var d=new JsonObject();d.add("unit",draft().json());d.addProperty("count",count);send(action,d); }
        catch(RuntimeException e){status=e.getMessage();}
    }
    private void send(String action,JsonObject data) { if(pending)return;pending=true;pendingAction=action;status="Saving…";SoldierEditorPackets.request(action,data,roster.get("revision").getAsInt()); }
    private void confirm(String message,Runnable action) {
        minecraft.setScreen(new ConfirmScreen(ok->{minecraft.setScreen(this);if(ok)action.run();},Component.literal("Soldier workshop"),Component.literal(message)));
    }
    private void switchDraft(Runnable next) { if(dirty)confirm("Discard unsaved changes to this variant?",next);else next.run(); }
    @Override public void onClose() { if(dirty)confirm("Close without saving changes?",()->{dirty=false;super.onClose();});else super.onClose(); }
    @Override public boolean isPauseScreen() { return false; }
    private void focusPending() { if(focusNext!=null) { if(children().contains(focusNext)){setFocused(focusNext);focusNext.setFocused(true);} focusNext=null; } }
    @Override public boolean mouseClicked(double x,double y,int button) { boolean handled=super.mouseClicked(x,y,button);focusPending();return handled; }
    @Override public void tick() { focusPending(); if(minecraft.player==null || !minecraft.player.isCreative()) minecraft.setScreen(null); }
    @Override public boolean keyPressed(int key,int scan,int modifiers) {
        if(key==GLFW.GLFW_KEY_S && hasControlDown() && modal.isEmpty()){save("save");return true;}
        if(key==GLFW.GLFW_KEY_ESCAPE && !modal.isEmpty()){modal="";rebuild();return true;}
        return super.keyPressed(key,scan,modifiers);
    }
    @Override public boolean mouseScrolled(double x,double y,double horizontal,double vertical) {
        if(!choiceRows.isEmpty()){choiceOffset-= (int)Math.signum(vertical)*3;filterChoices();return true;}
        if(x>=mx && x<px && y>=top+126){detailOffset-=(int)Math.signum(vertical)*22;layoutDetails();return true;}
        if(x<mx){unitOffset-=(int)Math.signum(vertical)*3;filterUnits();return true;}
        return super.mouseScrolled(x,y,horizontal,vertical);
    }
    private void updatePreview(String hovered) {
        if(preview==null) {
            var team=new net.minecraft.world.scores.PlayerTeam(new net.minecraft.world.scores.Scoreboard(),"soldier_preview");
            team.setNameTagVisibility(net.minecraft.world.scores.Team.Visibility.NEVER);
            preview=new RemotePlayer(minecraft.level,new GameProfile(UUID.nameUUIDFromBytes("wflib:soldier_preview".getBytes(java.nio.charset.StandardCharsets.UTF_8)),"SoldierPreview")) {
                @Override public net.minecraft.world.scores.PlayerTeam getTeam() { return team; }
            };
        }
        if(!previewDirty && previewFor.equals(hovered))return;previewDirty=false;previewFor=hovered;
        for(var s:new EquipmentSlot[]{EquipmentSlot.MAINHAND,EquipmentSlot.HEAD,EquipmentSlot.CHEST,EquipmentSlot.LEGS,EquipmentSlot.FEET})preview.setItemSlot(s,ItemStack.EMPTY);
        var weapon=catalogue.get(gun);if(weapon!=null)preview.setItemSlot(EquipmentSlot.MAINHAND,weapon.stack().copy());
        armour.forEach((slot,key)->{var c=catalogue.get(key);if(c!=null)preview.setItemSlot(SoldierCatalogue.slot(slot),c.stack().copy());});
        var candidate=catalogue.get(hovered);
        if(candidate!=null) {
            if(candidate.slot().equals("gun"))preview.setItemSlot(EquipmentSlot.MAINHAND,candidate.stack().copy());
            else if(!candidate.slot().equals("grenade")) {
                armour.forEach((slot,key)->{var old=catalogue.get(key);if(old!=null && !Collections.disjoint(old.covers(),candidate.covers()))preview.setItemSlot(SoldierCatalogue.slot(slot),ItemStack.EMPTY);});
                preview.setItemSlot(SoldierCatalogue.slot(candidate.slot()),candidate.stack().copy());
            }
        }
    }
    private void drawPreview(GuiGraphics g,String hovered,int bottom) {
        g.fill(px,top+25,px+pw,top+h,PANEL);
        updatePreview(hovered); int ptop=top+42;
        if(bottom>ptop+35){
            preview.yBodyRot=180+rotation;preview.setYRot(180+rotation);preview.yHeadRot=180+rotation;preview.yHeadRotO=preview.yHeadRot;preview.setXRot(0);
            g.enableScissor(px,ptop,px+pw,bottom);
            InventoryScreen.renderEntityInInventory(g,px+pw/2f,(ptop+bottom)/2f,Math.min(pw*.52f,(bottom-ptop)/2.1f),new Vector3f(0,1f,0),new Quaternionf().rotateZ((float)Math.PI),new Quaternionf(),preview);
            g.disableScissor();
        }
        g.drawString(font,fit(hovered.isEmpty()?role.title:"Preview · not selected",pw-12),px+6,top+30,INK,false);
    }
    @Override public void renderBackground(GuiGraphics g,int x,int y,float partial) { }
    @Override public void render(GuiGraphics g,int mouseX,int mouseY,float partial) {
        g.fill(0,0,width,height,0xd910141b);g.fill(left-6,top-5,left+w+6,top+h+5,BG);
        g.drawString(font,"SOLDIER WORKSHOP",left,top+5,ACCENT,false);
        if(modal.isEmpty()){
            g.fill(left-3,top+25,left+lw+3,top+h,PANEL);g.fill(mx-3,top+25,mx+mw+3,top+h,PANEL);g.fill(px,top+25,px+pw,top+h,PANEL);
            g.drawString(font,(dirty?"● Unsaved":existing?"Saved":"New variant")+" · Creative only",mx,top+7,dirty?0xfff1c778:MUTED,false);
            drawPreview(g,"",top+h-135);
            if(page.equals("tactics")) {int y=top+251;for(var line:font.split(Component.literal(role.description),mw-12)){if(y>top+h-34)break;g.drawString(font,line,mx+3,y,MUTED,false);y+=10;}}
        }else if(!choiceRows.isEmpty()) {
            String hovered="";
            for(int i=0;i<choiceRows.size();i++){var b=choiceRows.get(i);int at=choiceOffset+i;if(b.visible && b.isMouseOver(mouseX,mouseY) && at<filteredChoices.size())hovered=filteredChoices.get(at).key();}
            drawPreview(g,hovered,top+h-45);
            g.drawString(font,filteredChoices.size()+" options · hover to preview",left+10,top+h-15,MUTED,false);
        }
        else if(modal.equals("faction"))g.drawString(font,"Same faction: allies. Different factions: enemies.",left+12,top+h-20,MUTED,false);
        super.render(g,mouseX,mouseY,partial);
        if(!choiceRows.isEmpty())for(int i=0;i<choiceRows.size();i++){var b=choiceRows.get(i);int at=choiceOffset+i;if(b.visible&&at<filteredChoices.size())g.renderItem(filteredChoices.get(at).stack(),b.getX()+4,b.getY()+2);}
        if(!status.isEmpty())g.drawString(font,fit(status,width-20),10,height-10,pending?MUTED:0xfff1c778,false);
    }
}
