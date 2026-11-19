package com.wf.wflib.soldier;

import com.wf.wflib.soldier.api.SoldierOrder;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

import java.util.LinkedHashMap;
import java.util.Map;

/** A soldier that is short of kit walks to a supply container and takes what its issued kit lacks. */
final class Resupply {
    /** How close (eye to container centre) a soldier must stand to reach in. */
    static final double REACH = 3.2;
    /** The longest a trip may take (game ticks) and how far a depot may be for a soldier to go on its own (blocks). */
    static final long GIVE_UP = 1800, RECHECK = 2400;
    static final double AUTO_RANGE = 96, ORDER_RANGE = 160;

    private Resupply() {}

    /** Moves what {@code inv} lacks against {@code kit} out of {@code source} (or out of thin air when infinite). Returns what moved. */
    static Map<Item, Integer> transfer(Container source, Container inv, Kit kit, boolean infinite) {
        Map<Item, Integer> moved = new LinkedHashMap<>();
        for (Kit.Need need : kit.needs(inv)) {
            int left = need.count();
            if (infinite) {
                ItemStack give = need.proto().copyWithCount(left);
                insert(inv, give);
                left = give.getCount();
            } else if (source != null) {
                for (int i = 0; i < source.getContainerSize() && left > 0; i++) {
                    ItemStack s = source.getItem(i);
                    if (!s.is(need.item())) continue;
                    ItemStack take = s.copyWithCount(Math.min(left, s.getCount()));
                    int before = take.getCount();
                    insert(inv, take);
                    int took = before - take.getCount();
                    if (took > 0) {
                        s.shrink(took);
                        source.setChanged();
                        left -= took;
                    }
                }
            }
            if (left < need.count()) moved.merge(need.item(), need.count() - left, Integer::sum);
        }
        return moved;
    }

    /** Puts the stack in, stacking where it can; what did not fit stays in {@code stack}. */
    static void insert(Container to, ItemStack stack) {
        if (to instanceof Inventory inv) inv.add(stack);
        else if (to instanceof net.minecraft.world.SimpleContainer simple) stack.setCount(simple.addItem(stack).getCount());
        else throw new IllegalArgumentException("Unsupported container " + to.getClass());
    }

    /** The inventory behind a block (a double chest as one), or null. */
    static Container containerAt(ServerLevel level, BlockPos pos) {
        if (!level.isLoaded(pos)) return null;
        BlockState state = level.getBlockState(pos);
        if (state.getBlock() instanceof ChestBlock chest) return ChestBlock.getContainer(chest, state, level, pos, true);
        BlockEntity entity = level.getBlockEntity(pos);
        return entity instanceof Container c ? c : null;
    }

    /** Every brain tick: an unprompted trip when the kit is low, and the trip itself while one is under way. */
    static void tick(MinecraftServer server, ServerPlayer p, SoldierAi.Bot b, long now) {
        if (b.order.kind() == SoldierOrder.Kind.RESUPPLY) {
            if (b.reqPos != null) Requisition.trip(server, p, b, now);
            else trip(server, p, b, now);
            return;
        }
        if (b.kit == null || now < b.nextResupply || !b.threats.isEmpty() || b.hunting) return;
        if (switch (b.order.kind()) { case FOLLOW, HOLD, PATROL, GUARD -> false; default -> true; }) return;
        b.nextResupply = now + RECHECK;
        if (!b.kit.low(p.getInventory())) return;
        if (nearest(server, p, b, AUTO_RANGE) == null) return;
        b.resume = b.order;
        Orders.apply(b, p.position(), SoldierOrder.of(SoldierOrder.Kind.RESUPPLY));
        b.resupplyStart = now;
    }

    private static Depot nearest(MinecraftServer server, ServerPlayer p, SoldierAi.Bot b, double range) {
        return DepotBook.get(server).nearest(b.team, p.level().dimension().location().toString(), p.blockPosition(), range, b.triedDepots);
    }

    private static void trip(MinecraftServer server, ServerPlayer p, SoldierAi.Bot b, long now) {
        if (b.resupplyStart == 0) b.resupplyStart = now;
        Depot depot = b.depotId.isEmpty() ? null : DepotBook.get(server).all().get(b.depotId);
        if (depot == null) {
            depot = nearest(server, p, b, ORDER_RANGE);
            if (depot == null) {
                finish(b, p, false);
                return;
            }
            b.depotId = depot.id();
        }
        Vec3 centre = Vec3.atCenterOf(depot.pos());
        b.anchor = Vec3.atBottomCenterOf(depot.pos());
        if (now - b.resupplyStart > GIVE_UP) {
            finish(b, p, false);
            return;
        }
        if (p.getEyePosition().distanceToSqr(centre) > REACH * REACH) return;
        ServerLevel level = p.serverLevel();
        Map<Item, Integer> moved = transfer(containerAt(level, depot.pos()), p.getInventory(), b.kit, depot.infinite());
        if (moved.isEmpty() && !b.kit.needs(p.getInventory()).isEmpty()) {     // nothing here for it: try the next depot
            b.triedDepots.add(depot.id());
            b.depotId = "";
            return;
        }
        finish(b, p, !moved.isEmpty());
    }

    static void finish(SoldierAi.Bot b, ServerPlayer p, boolean gotSomething) {
        b.reqPos = null;
        SoldierOrder back = b.resume != null ? b.resume : SoldierOrder.FOLLOW;
        b.resume = null;
        b.resupplyStart = 0;
        b.nextResupply = p.level().getGameTime() + RECHECK;
        Orders.apply(b, p.position(), back);
        SoldierVoices.say(p, b, gotSomething ? "resupplied" : "ack_refuse");
    }
}
