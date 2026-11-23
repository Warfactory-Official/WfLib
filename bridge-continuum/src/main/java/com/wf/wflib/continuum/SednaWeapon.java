package com.wf.wflib.continuum;

import com.hbm.items.weapon.sedna.GunConfig;
import com.hbm.items.weapon.sedna.ItemGunBaseNT;
import com.hbm.items.weapon.sedna.ItemGunBaseNT.GunState;
import com.hbm.items.weapon.sedna.Receiver;
import com.hbm.items.weapon.sedna.mags.IMagazine;
import com.hbm.items.weapon.sedna.mags.MagazineFullReload;
import com.hbm.handler.HbmKeybinds.EnumKeybind;
import com.wf.wflib.soldier.api.SoldierServices;
import com.wf.wflib.soldier.api.SoldierWeapon;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * Soldiers with Continuum's guns (docs, 3.6): a {@link SoldierWeapon} for the Sedna guns, added beside whichever gun mod owns the main slot
 * ({@link SoldierServices#addWeapon}). A Sedna gun is a state machine driven by key edges and a per-tick call, and Continuum built it so that something with
 * no inventory tick behind it, a turret, can drive it ({@code handleKeybind} on a {@code LivingEntity}, {@code serverTick}); a soldier is that. Only guns
 * with a detachable magazine ({@link MagazineFullReload}) are usable, as the soldier's reload logic needs one.
 */
final class SednaWeapon implements SoldierWeapon {
    static void install() {
        SoldierServices.addWeapon(new SednaWeapon());
        ContinuumBridge.LOG.info("Soldiers: Continuum's magazine-fed guns can be issued and fired");
    }

    private static @Nullable ItemGunBaseNT gun(ServerPlayer p) {
        return p.getMainHandItem().getItem() instanceof ItemGunBaseNT g ? g : null;
    }

    static @Nullable IMagazine<?> magazine(ItemGunBaseNT gun, ItemStack stack) {
        GunConfig cfg = gun.getConfig(stack, 0);
        Receiver[] receivers = cfg.getReceivers(stack);
        return receivers == null || receivers.length == 0 ? null : receivers[0].getMagazine(stack);
    }

    @Override
    public @Nullable Ammo ammo(ServerPlayer p) {
        ItemGunBaseNT gun = gun(p);
        if (gun == null) return null;
        ItemStack stack = p.getMainHandItem();
        IMagazine<?> mag = magazine(gun, stack);
        if (mag == null) return null;
        Inventory inv = p.getInventory();
        // a full gun cannot be reloaded, so "could replace it" is read from what is in the pockets: the round the magazine loads, or the gun's default one
        Item round = null;
        if (mag.getType(stack, inv) instanceof com.hbm.items.weapon.sedna.BulletConfig bullet) round = bullet.ammoItem();
        if (round == null && gun.defaultAmmo != null) round = gun.defaultAmmo.get();
        Item wanted = round;
        boolean spare = wanted != null && inv.contains(s -> s.is(wanted));
        return new Ammo(mag.getAmount(stack, inv), spare ? mag.getCapacity(stack) : 0, mag.getCapacity(stack));
    }

    @Override
    public boolean busy(ServerPlayer p) {
        ItemGunBaseNT gun = gun(p);
        return gun != null && ItemGunBaseNT.getState(p.getMainHandItem(), 0) != GunState.IDLE;
    }

    @Override
    public boolean blocked(ServerPlayer p) {
        return false;
    }

    @Override
    public boolean jammed(ServerPlayer p) {
        return gun(p) != null && ItemGunBaseNT.getState(p.getMainHandItem(), 0) == GunState.JAMMED;
    }

    private static void tap(ItemGunBaseNT gun, ServerPlayer p, EnumKeybind key) {
        ItemStack stack = p.getMainHandItem();
        gun.handleKeybind(p, p.getInventory(), stack, key, false);          // a key already down would not be an edge
        gun.handleKeybind(p, p.getInventory(), stack, key, true);
        gun.handleKeybind(p, p.getInventory(), stack, key, false);
    }

    @Override
    public boolean unjam(ServerPlayer p) {
        ItemGunBaseNT gun = gun(p);
        if (gun == null || !jammed(p)) return false;
        tap(gun, p, EnumKeybind.RELOAD);
        return true;
    }

    @Override
    public boolean reload(ServerPlayer p) {
        ItemGunBaseNT gun = gun(p);
        if (gun == null || busy(p)) return false;
        tap(gun, p, EnumKeybind.RELOAD);
        return ItemGunBaseNT.getState(p.getMainHandItem(), 0) == GunState.RELOADING;
    }

    @Override
    public void cancel(ServerPlayer p) {
        if (gun(p) != null) ItemGunBaseNT.setReloadCancel(p.getMainHandItem(), true);
    }

    @Override
    public Result fire(ServerPlayer p) {
        ItemGunBaseNT gun = gun(p);
        Ammo before = ammo(p);
        if (gun == null || before == null) return new Result(Shot.REFUSED, "not a Sedna gun");
        ItemStack stack = p.getMainHandItem();
        if (ItemGunBaseNT.getState(stack, 0) == GunState.JAMMED) return new Result(Shot.REFUSED, "jammed");
        if (before.left() <= 0) return new Result(Shot.EMPTY, "empty");
        gun.handleKeybind(p, p.getInventory(), stack, EnumKeybind.GUN_PRIMARY, false);
        gun.handleKeybind(p, p.getInventory(), stack, EnumKeybind.GUN_PRIMARY, true);
        // the decider fires on the next tick of the gun; this is that tick, taken here so the answer is known, and the inventory's own tick follows as usual
        gun.serverTick(stack, p.serverLevel(), p, true);
        gun.handleKeybind(p, p.getInventory(), stack, EnumKeybind.GUN_PRIMARY, false);
        Ammo after = ammo(p);
        return after != null && after.left() < before.left() ? new Result(Shot.FIRED, "fired") : new Result(Shot.REFUSED, "state " + ItemGunBaseNT.getState(stack, 0));
    }

    @Override
    public boolean usable(ResourceLocation id) {
        Item item = BuiltInRegistries.ITEM.get(id);
        if (!(item instanceof ItemGunBaseNT gun)) return false;
        return magazine(gun, new ItemStack(gun)) instanceof MagazineFullReload;
    }

    @Override
    public List<ResourceLocation> guns() {
        List<ResourceLocation> out = new ArrayList<>();
        for (ItemGunBaseNT gun : ItemGunBaseNT.INSTANCES) {
            ResourceLocation id = BuiltInRegistries.ITEM.getKey(gun);
            if (usable(id)) out.add(id);
        }
        return out;
    }

    @Override
    @SuppressWarnings({"unchecked", "rawtypes"})
    public String arm(ServerPlayer p, ResourceLocation id, int spares, List<ResourceLocation> grenades) {
        ItemGunBaseNT gun = (ItemGunBaseNT) BuiltInRegistries.ITEM.get(id);
        ItemStack stack = new ItemStack(gun);
        Inventory inv = p.getInventory();
        int given = 0;
        if (gun.defaultAmmo != null) {
            for (int i = 0; i < Math.max(1, spares) + 1 && i < 6; i++) {
                inv.add(new ItemStack(gun.defaultAmmo.get(), Math.max(1, gun.defaultAmmoAmount)));
                given++;
            }
        }
        IMagazine mag = magazine(gun, stack);
        if (mag != null) {
            mag.initNewType(stack, inv);
            mag.setAmount(stack, mag.getCapacity(stack));
        }
        inv.setItem(inv.selected, stack);
        int slot = 8;
        for (ResourceLocation g : grenades) {
            Item item = BuiltInRegistries.ITEM.get(g);
            if (item != net.minecraft.world.item.Items.AIR && slot >= 0) inv.setItem(slot--, new ItemStack(item));
        }
        return "Continuum " + id + " loaded, " + given + " ammunition stack(s)";
    }
}
