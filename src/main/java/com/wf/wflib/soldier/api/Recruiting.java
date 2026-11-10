package com.wf.wflib.soldier.api;

import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.block.state.BlockState;

import java.util.Collections;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Predicate;

/**
 * Where mods plug into getting soldiers: {@link RecruitSource}s, {@link BarracksType}s (blocks and multiblocks the player hires at)
 * and a {@link Wallet}. WFLib's own sources are {@code rescue} (sneak-use an unowned friendly soldier), {@code garrison} (the same,
 * on a territory's garrison, at a price), {@code pda} (call reinforcements from the PDA, they walk to you) and {@code barracks}.
 */
public final class Recruiting {
    private static final List<RecruitSource> SOURCES = new CopyOnWriteArrayList<>();
    private static final List<BarracksType> BARRACKS = new CopyOnWriteArrayList<>();
    private static volatile Wallet wallet;
    private static volatile Pricing pricing;

    private Recruiting() {}

    public static void source(RecruitSource source) {
        SOURCES.removeIf(s -> s.id().equals(source.id()));
        SOURCES.add(source);
    }

    public static List<RecruitSource> sources() {
        return Collections.unmodifiableList(SOURCES);
    }

    public static RecruitSource source(String id) {
        for (RecruitSource s : SOURCES) if (s.id().equals(id)) return s;
        return null;
    }

    /** A barracks: a block or multiblock of yours that opens the hire menu when used. */
    public static void barracks(BarracksType type) {
        BARRACKS.removeIf(b -> b.id().equals(type.id()));
        BARRACKS.add(type);
    }

    /** The one-liner: every block {@code block} accepts is a barracks offering each friendly saved variant at {@code cost}, arriving after {@code delaySeconds}. */
    public static void barracks(String id, Predicate<BlockState> block, Cost cost, int delaySeconds) {
        barracks(new BarracksType() {
            @Override public String id() { return id; }

            @Override public boolean matches(net.minecraft.server.level.ServerLevel level, net.minecraft.core.BlockPos pos) {
                return block.test(level.getBlockState(pos));
            }

            @Override public List<Offer> offers(net.minecraft.server.level.ServerPlayer player, net.minecraft.server.level.ServerLevel level, net.minecraft.core.BlockPos pos) {
                return variants(player.getServer(), cost, delaySeconds);
            }
        });
    }

    public static List<BarracksType> barracks() {
        return Collections.unmodifiableList(BARRACKS);
    }

    /** Whether the server's config has the source switched on. */
    public static boolean enabled(String sourceId) {
        return com.wf.wflib.soldier.RecruitConfig.enabled(sourceId);
    }

    /** The economy's price for hiring this variant (free without a {@link Pricing}). */
    public static Cost hireCost(String variant, SoldierRole role) {
        var p = pricing;
        return p == null ? Cost.FREE : Cost.currency(p.hire(variant, role));
    }

    /** An offer for each saved variant that fights for a friendly faction. */
    public static List<Offer> variants(MinecraftServer server, Cost cost, int delaySeconds) {
        return com.wf.wflib.soldier.Recruits.friendlyVariants(server).stream()
                .map(v -> new Offer(v.id(), v.name(), v.id(), Cost.all(List.of(cost, hireCost(v.id(), v.role()))), delaySeconds)).toList();
    }

    /** The currency hiring and wages can be priced in (null: none installed). */
    public static void wallet(Wallet impl) {
        wallet = impl;
    }

    public static Wallet wallet() {
        return wallet;
    }

    /** What an economy mod charges (null: nothing beyond the config's own prices). */
    public static void pricing(Pricing impl) {
        pricing = impl;
    }

    public static Pricing pricing() {
        return pricing;
    }
}
