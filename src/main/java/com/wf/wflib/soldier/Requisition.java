package com.wf.wflib.soldier;

import com.wf.wflib.soldier.api.SoldierOrder;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * A soldier told to get gear (its {@link Want}) walks to a container in the force's storage that holds it, takes what it
 * lacks, puts it on, and leaves what it wore in the same container. Nothing is conjured: if storage has no such item the
 * soldier keeps what it has, tells its owner once, and looks again later.
 */
final class Requisition {
    /** A look in storage this often (ticks) while something is missing and storage has it; and when storage has none. */
    static final long CHECK = 100, RETRY = 600;
    static final double RANGE = 160;

    private Requisition() {}

    /** One thing a soldier lacks: {@code slot} is "gun", "grenade" or an armour slot. */
    record Job(String slot, String key, ItemStack proto) {}

    /** What the soldier lacks of its want. A key that is no longer an installed choice is skipped. */
    static List<Job> missing(MinecraftServer server, ServerPlayer p, SoldierAi.Bot b) {
        List<Job> out = new ArrayList<>();
        var catalogue = SoldierEditor.catalogue(server);
        Want w = b.want;
        if (!w.gun().isEmpty()) {
            var c = catalogue.get(w.gun());
            if (c != null && (b.gun == null || !b.gun.toString().equals(w.gun()))) out.add(new Job("gun", w.gun(), c.stack()));
        }
        Map<String, Integer> have = new LinkedHashMap<>();
        for (ResourceLocation g : b.grenades) have.merge(g.toString(), 1, Integer::sum);
        for (String g : w.grenades()) {
            int left = have.getOrDefault(g, 0);
            if (left > 0) {
                have.put(g, left - 1);
                continue;
            }
            var c = catalogue.get(g);
            if (c != null) out.add(new Job("grenade", g, c.stack()));
        }
        for (var e : w.armour().entrySet()) {
            var c = catalogue.get(e.getValue());
            if (c == null) continue;
            if (!Stockpile.same(p.getItemBySlot(SoldierCatalogue.slot(e.getKey())), c.stack())) out.add(new Job(e.getKey(), e.getValue(), c.stack()));
        }
        return out;
    }

    /** Guns and grenades are matched by what they are; worn pieces by what they are made of. */
    private static boolean matches(Job job, ItemStack stored) {
        return job.slot().equals("gun") || job.slot().equals("grenade") ? stored.is(job.proto().getItem()) : Stockpile.same(stored, job.proto());
    }

    /** Every few seconds, when calm: find a container that holds something it lacks and go there. */
    static void tick(MinecraftServer server, ServerPlayer p, SoldierAi.Bot b, long now) {
        if (b.want.isEmpty() || b.companion == null || b.ownerId == null || now < b.nextRequisition
                || !b.threats.isEmpty() || b.hunting) return;
        if (switch (b.order.kind()) { case FOLLOW, HOLD, PATROL, GUARD -> false; default -> true; }) return;
        b.nextRequisition = now + CHECK;
        List<Job> todo = missing(server, p, b);
        if (todo.isEmpty()) return;
        Stockpile stock = Stockpile.of(server, b.ownerId, b.team);
        String dim = p.level().dimension().location().toString();
        BlockPos best = null;
        double bestD = RANGE * RANGE;
        for (Job job : todo) {
            var entries = stock.all().values();
            for (var e : entries) {
                if (!matches(job, e.proto())) continue;
                for (var spot : e.spots()) {
                    if (!spot.dimension().equals(dim)) continue;
                    double d = Vec3.atCenterOf(spot.pos()).distanceToSqr(p.position());
                    if (d < bestD) {
                        bestD = d;
                        best = spot.pos();
                    }
                }
            }
        }
        if (best == null) {
            b.nextRequisition = now + RETRY;
            String what = describe(todo);
            if (!what.equals(b.reqMissing)) {
                b.reqMissing = what;
                ServerPlayer owner = server.getPlayerList().getPlayer(b.ownerId);
                if (owner != null) owner.sendSystemMessage(Component.literal(b.name.replace('_', ' ') + " cannot find " + what + " in your storage"));
            }
            return;
        }
        b.reqMissing = "";
        b.reqPos = best;
        b.resume = b.order;
        Orders.apply(b, p.position(), SoldierOrder.of(SoldierOrder.Kind.RESUPPLY));
        b.resupplyStart = now;
    }

    private static String describe(List<Job> todo) {
        List<String> names = new ArrayList<>();
        for (Job j : todo) {
            String n = j.proto().getHoverName().getString();
            if (!names.contains(n)) names.add(n);
        }
        return String.join(", ", names);
    }

    /** The walk to the container, then the exchange. */
    static void trip(MinecraftServer server, ServerPlayer p, SoldierAi.Bot b, long now) {
        if (b.resupplyStart == 0) b.resupplyStart = now;
        b.anchor = Vec3.atBottomCenterOf(b.reqPos);
        if (now - b.resupplyStart > Resupply.GIVE_UP) {
            b.nextRequisition = now + RETRY;
            Resupply.finish(b, p, false);
            return;
        }
        if (p.getEyePosition().distanceToSqr(Vec3.atCenterOf(b.reqPos)) > Resupply.REACH * Resupply.REACH) return;
        Container box = Resupply.containerAt(p.serverLevel(), b.reqPos);
        boolean got = box != null && exchange(server, p, b, box);
        b.nextRequisition = now + (got ? CHECK : RETRY);
        Resupply.finish(b, p, got);
    }

    /** Takes from {@code box} what the soldier lacks and holds there, wears it, and returns what it replaced. */
    static boolean exchange(MinecraftServer server, ServerPlayer p, SoldierAi.Bot b, Container box) {
        Companion c = b.ownerId == null ? null : SquadBook.get(server).find(b.ownerId, b.companion);
        boolean any = false;
        List<ResourceLocation> grenades = new ArrayList<>(b.grenades);
        boolean grenadesChanged = false;
        for (Job job : missing(server, p, b)) {
            ItemStack found = take(box, job);
            if (found.isEmpty()) continue;
            any = true;
            switch (job.slot()) {
                case "gun" -> {
                    if (b.gun != null && BuiltInRegistries.ITEM.containsKey(b.gun)) deposit(p, box, new ItemStack(BuiltInRegistries.ITEM.get(b.gun)));
                    b.gun = BuiltInRegistries.ITEM.getKey(found.getItem());
                    b.arm = true;                                     // the AI arms it on its next tick with the new gun
                    b.kit = null;
                    if (c != null) c.gun = b.gun;
                }
                case "grenade" -> {
                    if (!grenadesChanged) {                           // the first one in: what is not wanted goes
                        grenades.removeIf(g -> !b.want.grenades().contains(g.toString()));
                        grenadesChanged = true;
                    }
                    grenades.add(BuiltInRegistries.ITEM.getKey(found.getItem()));
                    b.arm = true;
                    b.kit = null;
                }
                default -> {
                    EquipmentSlot slot = SoldierCatalogue.slot(job.slot());
                    ItemStack old = p.getItemBySlot(slot);
                    if (!old.isEmpty()) deposit(p, box, old.copy());
                    p.setItemSlot(slot, found);
                    if (c != null) c.armour.put(slot, found.copy());
                    b.topUp = 5;
                }
            }
        }
        if (grenadesChanged) {
            b.grenades = List.copyOf(grenades);
            if (c != null) c.grenades = b.grenades;
        }
        if (any) {
            box.setChanged();
            Stockpile.changed(b.ownerId);
            if (c != null) SquadBook.get(server).setDirty();
        }
        return any;
    }

    /** Takes one matching item out of the container ({@code Items} for guns and grenades, the exact piece for armour). */
    private static ItemStack take(Container box, Job job) {
        for (int i = 0; i < box.getContainerSize(); i++) {
            ItemStack s = box.getItem(i);
            if (!s.isEmpty() && matches(job, s)) return box.removeItem(i, 1);
        }
        return ItemStack.EMPTY;
    }

    /** Puts a stack into the container, stacking where it can; what does not fit is dropped at the soldier's feet. */
    static void deposit(ServerPlayer p, Container box, ItemStack stack) {
        for (int i = 0; i < box.getContainerSize() && !stack.isEmpty(); i++) {
            ItemStack in = box.getItem(i);
            if (in.isEmpty() || !ItemStack.isSameItemSameComponents(in, stack) || in.getCount() >= in.getMaxStackSize()) continue;
            int move = Math.min(stack.getCount(), in.getMaxStackSize() - in.getCount());
            in.grow(move);
            stack.shrink(move);
        }
        for (int i = 0; i < box.getContainerSize() && !stack.isEmpty(); i++) {
            if (box.getItem(i).isEmpty() && box.canPlaceItem(i, stack)) {
                box.setItem(i, stack.copy());
                stack.setCount(0);
            }
        }
        if (!stack.isEmpty()) p.drop(stack, false);
    }
}
