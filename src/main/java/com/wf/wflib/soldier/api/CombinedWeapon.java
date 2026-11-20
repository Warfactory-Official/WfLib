package com.wf.wflib.soldier.api;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * Several gun mods behind one {@link SoldierWeapon}: whichever of them {@link SoldierWeapon#ammo} answers for the soldier's held weapon works it. A soldier holding
 * a gun none of them knows is treated as the first of them would (not armed). Asking what is {@link #usable} or {@link #guns} is a union, and {@link #arm} goes to
 * the first that can use the gun.
 */
final class CombinedWeapon implements SoldierWeapon {
    private final List<SoldierWeapon> all;

    CombinedWeapon(List<SoldierWeapon> all) {
        this.all = List.copyOf(all);
    }

    private SoldierWeapon of(ServerPlayer p) {
        for (SoldierWeapon w : all) if (w.ammo(p) != null) return w;
        return all.get(0);
    }

    @Override
    public @Nullable Ammo ammo(ServerPlayer p) {
        for (SoldierWeapon w : all) {
            Ammo a = w.ammo(p);
            if (a != null) return a;
        }
        return null;
    }

    @Override
    public boolean busy(ServerPlayer p) {
        return of(p).busy(p);
    }

    @Override
    public boolean blocked(ServerPlayer p) {
        return of(p).blocked(p);
    }

    @Override
    public boolean jammed(ServerPlayer p) {
        return of(p).jammed(p);
    }

    @Override
    public boolean unjam(ServerPlayer p) {
        return of(p).unjam(p);
    }

    @Override
    public boolean reload(ServerPlayer p) {
        return of(p).reload(p);
    }

    @Override
    public void cancel(ServerPlayer p) {
        of(p).cancel(p);
    }

    @Override
    public Result fire(ServerPlayer p) {
        return of(p).fire(p);
    }

    @Override
    public boolean usable(ResourceLocation gun) {
        for (SoldierWeapon w : all) if (w.usable(gun)) return true;
        return false;
    }

    @Override
    public List<ResourceLocation> guns() {
        List<ResourceLocation> out = new ArrayList<>();
        for (SoldierWeapon w : all) for (ResourceLocation id : w.guns()) if (!out.contains(id)) out.add(id);
        return out;
    }

    @Override
    public String arm(ServerPlayer p, ResourceLocation gun, int spares, List<ResourceLocation> grenades) {
        for (SoldierWeapon w : all) if (w.usable(gun)) return w.arm(p, gun, spares, grenades);
        return all.get(0).arm(p, gun, spares, grenades);
    }
}
