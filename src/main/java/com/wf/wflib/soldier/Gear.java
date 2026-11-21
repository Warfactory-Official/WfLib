package com.wf.wflib.soldier;

import com.wf.wflib.soldier.api.SoldierRole;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import java.util.EnumSet;
import java.util.List;
import java.util.UUID;

/** Telling soldiers what gear to get: checking a loadout against what is installed, and handing it to a roster. */
public final class Gear {
    private Gear() {}

    /** Throws if a key is not an installed choice for its slot, two pieces overlap, or a heavy has no exoskeleton. */
    public static void validate(SoldierCatalogue catalogue, Want want, String role) {
        if (!want.gun().isEmpty()) catalogue.require(want.gun(), "gun");
        for (String g : want.grenades()) catalogue.require(g, "grenade");
        var covered = EnumSet.noneOf(net.minecraft.world.entity.EquipmentSlot.class);
        boolean exo = false;
        for (var e : want.armour().entrySet()) {
            var c = catalogue.require(e.getValue(), e.getKey());
            if (!java.util.Collections.disjoint(covered, c.covers())) throw new IllegalArgumentException("These uniform pieces occupy the same body slots");
            covered.addAll(c.covers());
            exo |= c.exo() && c.slot().equals("chest");
        }
        catalogue.requireWholeSets(want.armour());
        if (!role.isEmpty()) {
            if (SoldierRole.of(role) == SoldierRole.HEAVY && !exo) throw new IllegalArgumentException("Equip an installed exoskeleton for the heavy role");
        }
    }

    /** The deployed soldier of {@code owner} with this body UUID (what the squad screen lists), or null. */
    static SoldierAi.Bot botOf(ServerPlayer owner, UUID body) {
        ServerPlayer p = owner.getServer().getPlayerList().getPlayer(body);
        SoldierAi.Bot b = p == null ? null : SoldierAi.botOf(p.getGameProfile().getName());
        return b != null && b.companion != null && owner.getUUID().equals(b.ownerId) ? b : null;
    }

    /**
     * Gives the owner's soldiers (by body UUID, as the squad screen lists them) the wanted gear. They go and find it in
     * storage; role and spare magazines, which are not items, change at once.
     *
     * @return how many soldiers took it
     */
    public static int assign(ServerPlayer owner, List<UUID> bodies, Want want, String role, int spares) {
        MinecraftServer server = owner.getServer();
        validate(SoldierEditor.catalogue(server), want, role);
        var book = SquadBook.get(server);
        int n = 0;
        for (UUID body : bodies) {
            SoldierAi.Bot b = botOf(owner, body);
            Companion c = b == null ? null : book.find(owner.getUUID(), b.companion);
            if (c == null) continue;
            c.want = want;
            b.want = want;
            b.nextRequisition = 0;
            b.reqMissing = "";
            if (!role.isEmpty()) {
                c.role = SoldierRole.of(role);
                b.role = c.role.id;
                b.burst = c.role.burst;
                b.pause = c.role.pause;
            }
            if (spares >= 0) {
                c.spares = spares;
                b.mags = spares;
                b.arm = true;                              // re-armed with the new spare count on its next tick
                b.kit = null;
            }
            n++;
        }
        if (n > 0) book.setDirty();
        return n;
    }
}
