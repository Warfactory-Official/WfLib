package com.wf.wflib.soldier;

import com.wf.wflib.soldier.api.SoldierMedical;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Fire-team movement leases, delayed contact reports, and one rescuer per casualty. Server thread only. */
final class BotSquad {
    record Member(String name, String role, long lastBound, boolean available) {}
    private static final class Team {
        Set<String> movers = Set.of();
        long until;
    }
    private static final Map<String, Team> TEAMS = new HashMap<>();

    private BotSquad() {}
    static void clear() { TEAMS.clear(); }

    static List<String> movers(List<Member> members, int count) {
        return members.stream().filter(Member::available)
                .sorted(Comparator.comparingLong(Member::lastBound)
                        .thenComparingInt(m -> m.role.equals("mg") ? 2 : m.role.equals("medic") ? 1 : 0)
                        .thenComparing(Member::name))
                .limit(count).map(Member::name).toList();
    }

    static Vec3 formation(Vec3 objective, Vec3 forward, String role, int index) {
        double back = role.equals("mg") ? 6 : role.equals("medic") ? 4 : 0;
        Vec3 right = new Vec3(-forward.z, 0, forward.x);
        return objective.subtract(forward.scale(back)).add(right.scale(index % 2 == 0 ? -2 : 2));
    }

    static void tick(MinecraftServer server, Map<String, SoldierAi.Bot> bots, long now) {
        if (now % 10 != 0) return;
        List<SoldierAi.Bot> living = new ArrayList<>();
        for (var b : bots.values()) {
            ServerPlayer p = server.getPlayerList().getPlayerByName(b.name);
            if (p == null || !p.isAlive() || !SoldierAi.scripted(p)) continue;
            b.health = BotMedical.condition(p);
            b.patient = "";
            b.rescuer = "";
            b.squadTask = "travel";
            b.mayAdvance = true;
            living.add(b);
        }

        // Patient priority and a claimed set prevent a whole squad abandoning its weapons for one casualty.
        Set<String> claimed = new HashSet<>();
        List<SoldierAi.Bot> patients = living.stream().filter(b -> b.health.needsHelp())
                .sorted(Comparator.comparingDouble((SoldierAi.Bot b) -> -b.health.urgency())).toList();
        for (var patient : patients) {
            ServerPlayer casualty = server.getPlayerList().getPlayerByName(patient.name);
            if (!patient.health.downed() && !patient.health.hands() && !patient.health.legs()
                    && BotMedical.plan(casualty, casualty) != null) continue;
            SoldierAi.Bot rescuer = living.stream().filter(b -> b != patient && b.team.equals(patient.team)
                            && !claimed.contains(b.name) && !b.health.downed() && !b.health.hands() && !b.health.legs()
                            && !b.health.critical() && !b.evading && now >= b.careRetry)
                    .filter(b -> {
                        ServerPlayer p = server.getPlayerList().getPlayerByName(b.name);
                        return p.level() == casualty.level() && p.distanceToSqr(casualty) <= 32 * 32
                                && (patient.health.downed() || BotMedical.plan(p, casualty) != null);
                    }).min(Comparator.comparingDouble(b -> {
                        ServerPlayer p = server.getPlayerList().getPlayerByName(b.name);
                        return p.distanceTo(casualty) - (b.role.equals("medic") ? 12 : 0)
                                - (b.carePatient.equalsIgnoreCase(patient.name) ? 8 : 0);
                    })).orElse(null);
            if (rescuer == null) continue;
            claimed.add(rescuer.name);
            rescuer.patient = patient.name;
            rescuer.squadTask = "rescue";
            patient.rescuer = rescuer.name;
            for (var guard : living) {
                if (guard != patient && guard != rescuer && guard.team.equals(patient.team)
                        && guard.patient.isEmpty() && !claimed.contains(guard.name) && !guard.health.needsHelp()
                        && server.getPlayerList().getPlayerByName(guard.name).distanceToSqr(casualty) < 24 * 24) {
                    guard.squadTask = "rescue-cover";
                    guard.mayAdvance = false;
                    break;
                }
            }
        }

        Map<String, List<SoldierAi.Bot>> squads = new HashMap<>();
        for (var b : living) squads.computeIfAbsent(b.team + ":" + b.squad, k -> new ArrayList<>()).add(b);
        for (var entry : squads.entrySet()) {
            Team team = TEAMS.computeIfAbsent(entry.getKey(), k -> new Team());
            var members = entry.getValue();
            var selfCare = members.stream().filter(b -> !b.health.downed() && !b.health.hands() && b.patient.isEmpty()
                            && BotMedical.plan(server.getPlayerList().getPlayerByName(b.name),
                                    server.getPlayerList().getPlayerByName(b.name)) != null)
                    .max(Comparator.comparingDouble(b -> b.health.urgency()
                            + (!b.careAction.isEmpty() && b.carePatient.equalsIgnoreCase(b.name) ? 1000 : 0))).orElse(null);
            for (var b : members) b.mayTreat = b == selfCare || b.health.critical();
            boolean contact = members.stream().anyMatch(b -> b.threats.values().stream().anyMatch(t -> now - t.seen < 80));
            if (!contact) continue;
            if (now >= team.until || team.movers.isEmpty()) {
                List<Member> candidates = members.stream().map(b -> new Member(b.name, b.role, b.lastBound,
                        b.mode == SoldierAi.Mode.ADVANCE && b.anchor != null && !b.health.needsHelp()
                                && !b.evading && b.patient.isEmpty() && !b.squadTask.equals("rescue-cover")
                                && BotTactics.horizontal(server.getPlayerList().getPlayerByName(b.name).position(), b.anchor) > 4))
                        .toList();
                team.movers = Set.copyOf(movers(candidates, Math.max(1, members.size() / 2)));
                team.until = now + 80;
                for (var b : members) if (team.movers.contains(b.name)) b.lastBound = now;
            }
            for (var b : members) {
                if (!b.patient.isEmpty() || b.squadTask.equals("rescue-cover")) continue;
                b.mayAdvance = team.movers.contains(b.name);
                b.squadTask = b.mayAdvance ? "bound" : "overwatch";
            }
        }
    }

    static void report(MinecraftServer server, Map<String, SoldierAi.Bot> bots, SoldierAi.Bot observer,
                       String enemy, SoldierAi.Threat contact, long now) {
        if (contact.visible < contact.reaction || contact.visible < 8 || now % 10 != 0) return;
        ServerPlayer source = server.getPlayerList().getPlayerByName(observer.name);
        for (var ally : bots.values()) {
            if (ally == observer || !ally.team.equals(observer.team)) continue;
            ServerPlayer p = server.getPlayerList().getPlayerByName(ally.name);
            if (p == null || p.level() != source.level() || p.distanceToSqr(source) > 48 * 48) continue;
            var remembered = ally.threats.computeIfAbsent(enemy, k -> SoldierAi.Threat.told());
            if (remembered.visible > 0 || remembered.seen >= contact.seen) continue;
            remembered.eye = contact.eye;
            remembered.seen = contact.seen;
            remembered.reaction = 6;
            remembered.confirmed |= contact.confirmed;
            ally.reports++;
        }
    }
}
