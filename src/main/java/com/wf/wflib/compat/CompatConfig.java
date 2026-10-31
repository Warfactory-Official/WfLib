package com.wf.wflib.compat;

import net.neoforged.fml.event.config.ModConfigEvent;
import net.neoforged.neoforge.common.ModConfigSpec;

/** Integration switches; immutable snapshot swapped on load. */
public final class CompatConfig {

    public static final ModConfigSpec SPEC;

    private static final ModConfigSpec.BooleanValue FACTION_FRIEND_OR_FOE;
    private static final ModConfigSpec.BooleanValue EXPLOSIONS_RESPECT_CLAIMS;

    private record Values(boolean factionFriendOrFoe, boolean explosionsRespectClaims) {
    }

    private static volatile Values values = new Values(true, true);

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

    public static void onLoad(ModConfigEvent event) {
        if (event.getConfig().getSpec() == SPEC) {
            values = new Values(FACTION_FRIEND_OR_FOE.get(), EXPLOSIONS_RESPECT_CLAIMS.get());
        }
    }
}
