package com.wf.wflib.soldier;

import com.wf.wflib.core.api.CreativeTabs;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.*;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.*;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;
import java.util.List;

/** Empty egg opens the editor; named eggs resolve their world-owned variant on every placement. */
public final class SoldierEditorEgg extends Item {
    private static final DeferredRegister.Items ITEMS=DeferredRegister.createItems("wflib");
    public static final DeferredItem<SoldierEditorEgg> ITEM=ITEMS.register("soldier_spawn_egg",()->new SoldierEditorEgg(new Properties()));
    private SoldierEditorEgg(Properties p) { super(p); }
    static void register(IEventBus bus) { ITEMS.register(bus); CreativeTabs.contribute("soldier_editor",461,out->out.accept(ITEM.get())); }
    public static ItemStack egg(SoldierTemplate u) {
        var stack=new ItemStack(ITEM.get()); var tag=new CompoundTag(); tag.putString("soldier_variant",u.id());
        stack.set(DataComponents.CUSTOM_DATA,CustomData.of(tag));
        stack.set(DataComponents.CUSTOM_NAME,Component.literal(u.name()+" · "+u.role().title)); return stack;
    }
    public static String variant(ItemStack s) { return s.getOrDefault(DataComponents.CUSTOM_DATA,CustomData.EMPTY).copyTag().getString("soldier_variant"); }
    @Override public InteractionResultHolder<ItemStack> use(Level level,Player player,InteractionHand hand) {
        if (!player.isCreative()) return InteractionResultHolder.fail(player.getItemInHand(hand));
        if (player instanceof ServerPlayer p) SoldierEditor.open(p);
        return InteractionResultHolder.sidedSuccess(player.getItemInHand(hand),level.isClientSide);
    }
    @Override public InteractionResult useOn(UseOnContext c) {
        if (!(c.getPlayer() instanceof Player player) || !player.isCreative()) return InteractionResult.FAIL;
        if (!(player instanceof ServerPlayer p)) return InteractionResult.SUCCESS;
        try {
            String id=variant(c.getItemInHand());
            if (id.isEmpty() || p.isShiftKeyDown()) { SoldierEditor.open(p); return InteractionResult.CONSUME; }
            var pos=c.getClickedPos(); if (!c.getLevel().getBlockState(pos).getCollisionShape(c.getLevel(),pos).isEmpty()) pos=pos.relative(c.getClickedFace());
            SoldierEditor.spawn(p,id,Vec3.atBottomCenterOf(pos),p.getYRot()); return InteractionResult.CONSUME;
        } catch (IllegalArgumentException e) { p.displayClientMessage(Component.literal(e.getMessage()),true); return InteractionResult.FAIL; }
    }
    @Override public void appendHoverText(ItemStack s,TooltipContext c,List<Component> lines,TooltipFlag f) {
        lines.add(Component.literal("Creative only · Use in air / sneak to open editor"));
        if (!variant(s).isEmpty()) { lines.add(Component.literal("Place on a block to deploy · "+variant(s))); lines.add(Component.literal("Uses this world's saved variant; deleting it disables the egg")); }
    }
}
