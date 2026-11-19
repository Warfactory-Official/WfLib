package com.wf.wflib.soldier.api;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.neoforged.bus.api.Event;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

/**
 * A soldier died. Posted once per death on {@code NeoForge.EVENT_BUS}, server thread, after the soldier's own death
 * handling. Not cancellable: it reports. Bounties, heat, kill statistics and weapon-signature checks (a False Flag
 * contract wants to know what killed whom) read it instead of each decoding the {@link DamageSource}.
 *
 * <p>Who is responsible: {@link #killer()} is the player or soldier that dealt the blow (null for the environment, or for
 * an unmanned killer such as a drone or vehicle). For those, {@link #attributedFaction()} names the side responsible, when
 * the damage source carries one.
 */
public final class SoldierKillEvent extends Event {
    private final ServerPlayer victim;
    private final String victimTeam;
    @Nullable
    private final ServerPlayer killer;
    @Nullable
    private final String killerTeam;
    @Nullable
    private final UUID attributedFaction;
    @Nullable
    private final ResourceLocation weapon;
    private final double distance;
    private final DamageSource source;

    public SoldierKillEvent(ServerPlayer victim, String victimTeam, @Nullable ServerPlayer killer,
                            @Nullable String killerTeam, @Nullable UUID attributedFaction,
                            @Nullable ResourceLocation weapon, double distance, DamageSource source) {
        this.victim = victim;
        this.victimTeam = victimTeam;
        this.killer = killer;
        this.killerTeam = killerTeam;
        this.attributedFaction = attributedFaction;
        this.weapon = weapon;
        this.distance = distance;
        this.source = source;
    }

    /** The soldier that died. */
    public ServerPlayer victim() {
        return victim;
    }

    /** The victim's {@link SoldierSpec#team()}. */
    public String victimTeam() {
        return victimTeam;
    }

    /** Who dealt the blow: a real player or another soldier; null for the environment or an unmanned killer. */
    @Nullable
    public ServerPlayer killer() {
        return killer;
    }

    /** True when the killer is a real player (not null and not a soldier). */
    public boolean killedByPlayer() {
        return killer != null && !(killer instanceof SoldierPlayer);
    }

    /** The killer's team when the killer is a soldier; null for a real player or no killer. */
    @Nullable
    public String killerTeam() {
        return killerTeam;
    }

    /** The side responsible when the damage source names one ({@code Attribution.faction}): drones, vehicles, mines. */
    @Nullable
    public UUID attributedFaction() {
        return attributedFaction;
    }

    /** The item the killer was holding (the gun), or the direct cause's entity type for a projectile; null when neither is known. */
    @Nullable
    public ResourceLocation weapon() {
        return weapon;
    }

    /** Blocks between killer and victim at the moment of death; -1 when there is no killer. */
    public double distance() {
        return distance;
    }

    public DamageSource source() {
        return source;
    }
}
