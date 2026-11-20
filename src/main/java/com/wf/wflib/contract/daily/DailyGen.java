package com.wf.wflib.contract.daily;

import com.wf.wflib.contract.ContractDef;
import com.wf.wflib.contract.difficulty.Tier;
import com.wf.wflib.contract.profile.Rank;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Makes one randomised contract: a kind (assassination, raid, laptop or drive recovery, clearance, a narcotics run), with a codename, a
 * distance, a force and a price all drawn from {@code rnd} and scaled by the tier. Pure, so the same seed always gives the same board.
 */
public final class DailyGen {
    /** Which soldiers a squad of a grade is made of; the server fills it from the world's variants. */
    @FunctionalInterface
    public interface Forces {
        List<String> units(String faction, int grade);
    }

    public enum Kind {
        ASSASSINATION, RAID, LAPTOP, DRIVE, CLEARANCE, NARCOTICS
    }

    private static final String[] ADJ = {"Iron", "Silent", "Crimson", "Broken", "Pale", "Rusted", "Hollow", "Black", "Amber", "Cold", "Savage", "Gilded", "Burnt", "Ashen", "Wild"};
    private static final String[] NOUN = {"Falcon", "Anvil", "Harbor", "Lantern", "Viper", "Cinder", "Kestrel", "Bastion", "Tide", "Thorn", "Mirage", "Garrison", "Ledger", "Fang", "Gambit"};
    private static final String[] RANK = {"Colonel", "Broker", "Chemist", "Captain", "Warlord", "Quartermaster", "Fixer", "Major"};
    private static final String[] SURNAME = {"Drako", "Voss", "Karimov", "Ashby", "Brandt", "Okafor", "Lund", "Petrenko", "Moreau", "Kessler", "Halvorsen", "Rashid"};

    private DailyGen() {
    }

    public static Kind pick(Random rnd, List<Kind> already) {
        Kind[] all = Kind.values();
        for (int tries = 0; tries < 8; tries++) {
            Kind k = all[rnd.nextInt(all.length)];
            if (already.stream().filter(k::equals).count() < 2) return k;
        }
        return all[rnd.nextInt(all.length)];
    }

    public static ContractDef generate(ResourceLocation id, Kind kind, Random rnd, double tier, double rewardScale, Forces forces) {
        int grade = Tier.grade(tier);
        String code = "Operation " + ADJ[rnd.nextInt(ADJ.length)] + " " + NOUN[rnd.nextInt(NOUN.length)];
        int farMax = 500 + (int) (tier * 90) + rnd.nextInt(300);
        int lo = 200 + rnd.nextInt(150), hi = Math.min(5000, Math.max(lo + 150, farMax));
        int radius = 12 + rnd.nextInt(10);
        int minutes = 25 + rnd.nextInt(20) + (int) (hi / 100);
        Rank gate = Rank.values()[Tier.gateRank(tier)];
        String enemy = "enemy";
        List<ContractDef.Squad> squads = new ArrayList<>();
        List<ContractDef.Objective> objectives = new ArrayList<>();
        List<ContractDef.Cargo> cargo = new ArrayList<>();
        String title, description;
        int baseMoney, baseRep, buyIn = 0;
        switch (kind) {
            case ASSASSINATION -> {
                String who = RANK[rnd.nextInt(RANK.length)] + " " + SURNAME[rnd.nextInt(SURNAME.length)];
                title = "Eliminate " + who;
                description = code + ". " + who + " is holed up with a guard. Kill " + who + "; the guard is optional.";
                squads.add(squad(forces, enemy, Math.min(3, grade + 1), 1, "guard"));
                squads.add(squad(forces, enemy, grade, Tier.squadSize(3 + rnd.nextInt(3), tier), rnd.nextBoolean() ? "patrol" : "guard"));
                objectives.add(new ContractDef.KillTarget(0));
                baseMoney = 700;
                baseRep = 28;
            }
            case RAID -> {
                title = code + ": supply raid";
                description = "A cache manifest sits in a guarded depot. Take it out.";
                squads.add(squad(forces, enemy, grade, Tier.squadSize(4 + rnd.nextInt(3), tier), "guard"));
                objectives.add(new ContractDef.RetrieveItem(ResourceLocation.parse("wflib:cache_manifest"), 1));
                baseMoney = 520;
                baseRep = 20;
            }
            case LAPTOP -> {
                title = code + ": laptop recovery";
                description = "A company laptop was left at a hideout. Bring it back intact.";
                squads.add(squad(forces, enemy, grade, Tier.squadSize(4 + rnd.nextInt(3), tier), "guard"));
                squads.add(squad(forces, enemy, grade, Tier.squadSize(2 + rnd.nextInt(2), tier), "patrol"));
                objectives.add(new ContractDef.RetrieveItem(ResourceLocation.parse("wflib:laptop"), 1));
                baseMoney = 850;
                baseRep = 32;
            }
            case DRIVE -> {
                title = code + ": data recovery";
                description = "Drives with the other side's accounts are at a site. Recover " + (1 + rnd.nextInt(2)) + ".";
                squads.add(squad(forces, enemy, grade, Tier.squadSize(3 + rnd.nextInt(4), tier), rnd.nextBoolean() ? "guard" : "patrol"));
                objectives.add(new ContractDef.RetrieveItem(ResourceLocation.parse("wflib:data_drive"), 1));
                baseMoney = 580;
                baseRep = 22;
            }
            case CLEARANCE -> {
                title = code + ": clear the compound";
                description = "A fortified compound has to be cleared of every hostile.";
                squads.add(squad(forces, enemy, grade, Tier.squadSize(5 + rnd.nextInt(3), tier), "guard"));
                squads.add(squad(forces, enemy, grade, Tier.squadSize(3 + rnd.nextInt(3), tier), "patrol"));
                objectives.add(new ContractDef.ClearArea());
                baseMoney = 950;
                baseRep = 36;
                radius = Math.max(radius, 16);
            }
            default -> {
                int bricks = 4 + rnd.nextInt(9);
                title = code + ": delivery run";
                description = "Carry " + bricks + " wrapped bricks to the drop. You pay for the goods up front, and nothing is refunded.";
                squads.add(squad(forces, enemy, grade, Tier.squadSize(3 + rnd.nextInt(3), tier), "patrol"));
                ResourceLocation brick = ResourceLocation.parse("wflib:narcotics_brick");
                objectives.add(new ContractDef.DeliverItem(brick, bricks));
                cargo.add(new ContractDef.Cargo(brick, bricks));
                buyIn = bricks * 150;
                baseMoney = (int) (buyIn * (1.45 + 0.05 * tier));
                baseRep = 24 + bricks;
                lo = Math.max(lo, 400);
                hi = Math.max(hi, lo + 300);
            }
        }
        double jitter = 0.85 + rnd.nextDouble() * 0.3;
        int money = Tier.money((int) (baseMoney * jitter), kind == Kind.NARCOTICS ? 0 : tier, rewardScale);
        if (kind == Kind.NARCOTICS) money = (int) Math.round(baseMoney * jitter * rewardScale);
        int rep = Tier.rep(baseRep, tier);
        return new ContractDef(id, title.length() > 48 ? title.substring(0, 48) : title, description, gate, lo, hi, Math.min(240, minutes), radius,
                squads, objectives, money, rep, Math.max(2, rep / 5), enemy, buyIn, cargo, "board", List.of(), "");
    }

    private static ContractDef.Squad squad(Forces forces, String faction, int grade, int count, String mode) {
        List<String> units = forces.units(faction, grade);
        return new ContractDef.Squad(units.isEmpty() ? faction : "", units, Math.max(1, Math.min(ContractDef.MAX_SQUAD_SIZE, count)), mode);
    }
}
