package com.wf.wflib.compat;

import net.neoforged.fml.event.config.ModConfigEvent;
import net.neoforged.neoforge.common.ModConfigSpec;

/** Integration switches; immutable snapshot swapped on load. */
public final class CompatConfig {

    public static final ModConfigSpec SPEC;

    private static final ModConfigSpec.BooleanValue FACTION_FRIEND_OR_FOE;
    private static final ModConfigSpec.BooleanValue EXPLOSIONS_RESPECT_CLAIMS;
    private static final ModConfigSpec.BooleanValue EXCAVATIONS_RESPECT_CLAIMS;
    private static final ModConfigSpec.BooleanValue RADIATION_RESPECTS_CLAIMS;
    private static final ModConfigSpec.BooleanValue EMP_RESPECTS_CLAIMS;

    private record Values(boolean factionFriendOrFoe, boolean explosionsRespectClaims,
                          boolean excavationsRespectClaims,
                          boolean radiationRespectsClaims, boolean empRespectsClaims) {
    }

    private static volatile Values values = new Values(true, true, true, false, false);

    static {
        ModConfigSpec.Builder b = new ModConfigSpec.Builder();
        b.comment("WarForge Factions integration (only has any effect when the 'warforge' mod is installed).")
                .push("warforge");
        FACTION_FRIEND_OR_FOE = b
                .comment("Treat missiles of the same / allied / truced WarForge faction as friendly (so a",
                        "faction's defenses don't engage its own missiles across different launchers).")
                .define("factionFriendOrFoe", true);
        EXPLOSIONS_RESPECT_CLAIMS = b
                .comment("Missile explosions and round block damage respect WarForge land claims (protected",
                        "blocks survive, except in active siege zones). Individual blasts can still opt out (e.g.",
                        "strategic nukes).")
                .define("explosionsRespectClaims", true);
        EXCAVATIONS_RESPECT_CLAIMS = b
                .comment("Tunnel boring, track laying and drone builds obey the claim's block rules (break /",
                        "place), not just zone and alliance.")
                .define("excavationsRespectClaims", true);
        RADIATION_RESPECTS_CLAIMS = b
                .comment("Fallout block decay obeys claim rules. Off: radiation ignores claims.")
                .define("radiationRespectsClaims", false);
        EMP_RESPECTS_CLAIMS = b
                .comment("EMP pulses obey claim rules. Off: EMP ignores claims.")
                .define("empRespectsClaims", false);
        b.pop();
        SPEC = b.build();
    }

    private CompatConfig() {
    }

    public static boolean factionFriendOrFoe() {
        return values.factionFriendOrFoe;
    }

    public static boolean explosionsRespectClaims() {
        return values.explosionsRespectClaims;
    }

    public static boolean excavationsRespectClaims() {
        return values.excavationsRespectClaims;
    }

    public static boolean radiationRespectsClaims() {
        return values.radiationRespectsClaims;
    }

    public static boolean empRespectsClaims() {
        return values.empRespectsClaims;
    }

    public static void onLoad(ModConfigEvent event) {
        if (event.getConfig().getSpec() == SPEC) {
            values = new Values(FACTION_FRIEND_OR_FOE.get(), EXPLOSIONS_RESPECT_CLAIMS.get(),
                    EXCAVATIONS_RESPECT_CLAIMS.get(),
                    RADIATION_RESPECTS_CLAIMS.get(), EMP_RESPECTS_CLAIMS.get());
        }
    }
}
