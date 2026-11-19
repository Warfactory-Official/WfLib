package com.wf.wflib.soldier;

import com.mojang.logging.LogUtils;
import com.wf.wflib.soldier.api.SoldierOrder;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Territories that raise their own garrison. Every few seconds each territory with a {@link Garrison} counts the soldiers
 * it has raised (alive, or waiting to come back after a restart), recruits the shortfall inside itself (a few at a time,
 * and not sooner than the reinforcement delay after its last loss) and sends home any it has too many of. A territory
 * whose centre is not loaded raises nothing: it fills when somebody comes near. Server thread only.
 */
final class Garrisons {
    static final int EVERY = 100, PER_PASS = 5, TRIES = 8;
    /** How a soldier is raised from a variant, and which variants exist: the editor's, unless a test stands in for a gun mod. */
    interface Raiser {
        ServerPlayer raise(ServerLevel level, String unit, Vec3 at, float yaw);
    }

    static Raiser raiser = (level, unit, at, yaw) -> SoldierEditor.place(level, null, unit, at, yaw);
    static java.util.function.Function<MinecraftServer, java.util.Collection<String>> variants = s -> SoldierRoster.get(s).units().keySet();
    private static final RandomSource RNG = RandomSource.create(23L);
    private static final Map<String, Long> LOSS = new HashMap<>();
    private static final Set<String> COMPLAINED = new HashSet<>();

    private Garrisons() {}

    /** A garrison soldier fell: the reinforcement delay starts from now. */
    static void lost(SoldierAi.Bot b, long now) {
        if (!b.garrison.isEmpty()) LOSS.put(b.garrison, now);
    }

    /** The garrison's soldiers that are in the world. */
    static List<ServerPlayer> alive(MinecraftServer server, String territory) {
        List<ServerPlayer> out = new ArrayList<>();
        for (SoldierAi.Bot b : SoldierAi.soldierBots()) {
            if (b.server != server || !b.garrison.equals(territory)) continue;
            ServerPlayer p = server.getPlayerList().getPlayerByName(b.name);
            if (p != null && p.isAlive()) out.add(p);
        }
        return out;
    }

    static void onServerTick(ServerTickEvent.Post e) {
        if (e.getServer().getTickCount() % EVERY == 7) pass(e.getServer());
    }

    /** One look at every territory: let go of what it no longer raises, then count and recruit. */
    static void pass(MinecraftServer server) {
        var book = TerritoryBook.get(server);
        for (SoldierAi.Bot b : SoldierAi.soldierBots()) {          // a territory that is gone, or no longer raises soldiers, lets them go
            if (b.server != server || b.garrison.isEmpty()) continue;
            Territory t = book.get(b.garrison);
            if (t == null || t.garrison() == null) b.garrison = "";
        }
        for (Territory t : book.all().values()) if (t.garrison() != null) fill(server, t, t.garrison());
    }

    private static void fill(MinecraftServer server, Territory t, Garrison g) {
        ServerLevel level = server.getLevel(ResourceKey.create(Registries.DIMENSION, ResourceLocation.parse(t.dimension())));
        if (level == null || !level.hasChunkAt(BlockPos.containing(t.centre()))) return;
        List<ServerPlayer> mine = alive(server, t.id());
        int have = mine.size() + Posts.pending(t.id());
        if (have > g.count()) {
            for (int i = 0; i < Math.min(have - g.count(), mine.size()); i++) com.wf.wflib.soldier.api.Soldiers.remove(mine.get(mine.size() - 1 - i));
            return;
        }
        long now = level.getGameTime();
        if (have == g.count() || now - LOSS.getOrDefault(t.id(), Long.MIN_VALUE / 2) < g.respawnSeconds() * 20L) return;
        List<String> existing = new ArrayList<>(variants.apply(server));
        for (int i = 0; i < Math.min(g.count() - have, PER_PASS); i++) {
            String unit = g.unitFor(have + i, existing);
            if (unit == null) return;                             // nothing saved to raise it from
            Vec3 at = spot(level, t);
            if (at == null) continue;
            try {
                var p = raiser.raise(level, unit, at, RNG.nextFloat() * 360f);
                var b = SoldierAi.botOf(p.getGameProfile().getName());
                b.garrison = t.id();
                Orders.apply(b, p.position(), SoldierOrder.in(g.mode(), t.id()));
            } catch (IllegalArgumentException ex) {
                if (COMPLAINED.add(t.id() + "/" + unit)) LogUtils.getLogger().warn("Garrison of {} cannot raise {}: {}", t.id(), unit, ex.getMessage());
            }
        }
    }

    /** A free place on the ground inside the territory, or null. */
    private static Vec3 spot(ServerLevel level, Territory t) {
        for (int i = 0; i < TRIES; i++) {
            Vec3 at = Patrols.onGround(level, t, t.patrolPoint(RNG.nextInt(), null, RNG, t.radius()), false);
            BlockPos feet = BlockPos.containing(at);
            if (!level.hasChunkAt(feet) || !level.getWorldBorder().isWithinBounds(feet)) continue;
            if (!level.getBlockState(feet.below()).isCollisionShapeFullBlock(level, feet.below())) continue;
            Vec3 stand = Vec3.atBottomCenterOf(feet);
            if (level.noCollision(new AABB(stand.x - .3, stand.y, stand.z - .3, stand.x + .3, stand.y + 1.8, stand.z + .3))) return stand;
        }
        return null;
    }
}
