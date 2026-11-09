package com.wf.wflib.corpse;

import com.mojang.authlib.GameProfile;
import com.wf.wflib.corpse.api.CorpseEntity;
import com.wf.wflib.corpse.api.CorpseSlot;
import com.wf.wflib.corpse.api.Corpses;
import com.wf.wflib.ragdoll.api.Ragdolls;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.player.PlayerModelPart;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.living.LivingDropsEvent;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Death (LOWEST, uncancelled => death proceeds): slot snapshot. Drops (LOWEST, after every mod added its own): each
 * drop matched to the first unclaimed snapshot stack ({@code ItemStack.matches}; mods drop copies), drops cancelled.
 */
public final class CorpseDeaths {
    private static final Map<UUID, List<CorpseEntity.Entry>> WORN = new HashMap<>();

    private CorpseDeaths() {
    }

    static void onDeath(LivingDeathEvent e) {
        if (e.getEntity() instanceof ServerPlayer p) {
            List<CorpseEntity.Entry> worn = new ArrayList<>();
            for (CorpseSlot kind : Corpses.slots()) {
                kind.each(p, (key, stack) -> worn.add(CorpseEntity.entry(kind.id(), key, stack.copy())));
            }
            WORN.put(p.getUUID(), worn);
        }
    }

    static void onDrops(LivingDropsEvent e) {
        if (!(e.getEntity() instanceof ServerPlayer p)) {
            return;
        }
        List<CorpseEntity.Entry> worn = WORN.remove(p.getUUID());
        if (worn == null) {
            worn = List.of();
        }
        boolean[] used = new boolean[worn.size()];
        List<CorpseEntity.Entry> taken = new ArrayList<>();
        for (ItemEntity drop : e.getDrops()) {
            ItemStack stack = drop.getItem();
            if (stack.isEmpty()) {
                continue;
            }
            CorpseEntity.Entry from = null;
            for (int i = 0; i < used.length && from == null; i++) {
                if (!used[i] && ItemStack.matches(worn.get(i).stack(), stack)) {
                    used[i] = true;
                    from = worn.get(i);
                }
            }
            taken.add(CorpseEntity.entry(from == null ? null : from.kind, from == null ? "" : from.key, stack.copy()));
        }
        e.setCanceled(true);
        spawn(p.serverLevel(), p, p.getGameProfile(), parts(p), taken, p.getInventory().selected);
    }

    /** Offline stand-in: every slot of {@code owner} emptied into the corpse. */
    public static void claim(ServerPlayer owner, GameProfile profile, LivingEntity body) {
        List<CorpseEntity.Entry> taken = new ArrayList<>();
        CorpseSlots.settle(owner);
        for (CorpseSlot kind : Corpses.slots()) {
            List<String> keys = new ArrayList<>();
            kind.each(owner, (key, stack) -> keys.add(key));
            for (String key : keys) {
                ItemStack stack = kind.take(owner, key);
                if (!stack.isEmpty()) {
                    taken.add(CorpseEntity.entry(kind.id(), key, stack));
                }
            }
        }
        spawn((ServerLevel) body.level(), body, profile, (byte) 0x7F, taken, owner.getInventory().selected);
    }

    private static void spawn(ServerLevel level, LivingEntity body, GameProfile profile, byte parts,
                              List<CorpseEntity.Entry> taken, int selected) {
        CorpseEntity corpse = new CorpseEntity(Corpses.ENTITY.get(), level);
        corpse.setup(profile, parts, taken, selected);
        corpse.moveTo(body.getX(), body.getY(), body.getZ(), body.getYRot(), 0);
        corpse.yBodyRot = body.yBodyRot;
        corpse.yBodyRotO = body.yBodyRot;
        corpse.yHeadRot = body.yBodyRot;
        Ragdolls.handOff(body, corpse);
        level.addFreshEntity(corpse);
    }

    private static byte parts(Player p) {
        byte mask = 0;
        for (PlayerModelPart part : PlayerModelPart.values()) {
            if (p.isModelPartShown(part)) {
                mask |= (byte) part.getMask();
            }
        }
        return mask;
    }
}
