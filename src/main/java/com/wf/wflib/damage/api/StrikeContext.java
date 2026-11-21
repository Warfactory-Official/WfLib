package com.wf.wflib.damage.api;

import com.norwood.ahf.part.HitboxPart;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import com.wf.wflib.missile.api.ThreatSource;

/**
 * What struck: the one view armour and hit-location code reads, whether the striker is a projectile entity or an
 * entity-less round ({@link #of} on the damage source). {@link #key} identifies the strike across
 * {@link ProjectileStrikeEvent} and the damage call that follows it.
 */
public interface StrikeContext {

    Threat threat();

    /** Striker position at the start of its travel this tick. */
    Vec3 position();

    /** This tick's travel. */
    Vec3 velocity();

    /** Identity for per-strike ledgers: the projectile entity, or a round's key. */
    Object key();

    @Nullable
    Entity shooter();

    /** Parts struck, entry order (AHF); null = no part model for the target (vehicles, wide mobs, projectiles). */
    @Nullable
    default List<HitboxPart> parts() {
        return null;
    }

    /**
     * Strikers another mod made and WFLib cannot touch: each answers a damage source it recognises with a context, null for the rest. Asked in the order they
     * were added, after the cases {@link #of} knows itself, so a foreign bullet carries a {@link Threat} to armour and hulls like WFLib's own.
     */
    List<java.util.function.Function<DamageSource, StrikeContext>> ADAPTERS = new java.util.concurrent.CopyOnWriteArrayList<>();

    /** Round damage sources are contexts; a {@link ThreatSource} projectile is adapted; a source an {@link #ADAPTERS adapter} recognises is; else null. */
    @Nullable
    static StrikeContext of(DamageSource source) {
        if (source instanceof StrikeContext context) {
            return context;
        }
        if (source.getDirectEntity() instanceof Projectile projectile && projectile instanceof ThreatSource threat) {
            return new Of(projectile, threat);
        }
        for (var adapter : ADAPTERS) {
            StrikeContext adapted = adapter.apply(source);
            if (adapted != null) {
                return adapted;
            }
        }
        return null;
    }

    record Of(Projectile projectile, ThreatSource source) implements StrikeContext {
        @Override
        public Threat threat() {
            return this.source.threat();
        }

        @Override
        public Vec3 position() {
            return this.projectile.position();
        }

        @Override
        public Vec3 velocity() {
            return this.projectile.getDeltaMovement();
        }

        @Override
        public Object key() {
            return this.projectile;
        }

        @Nullable
        @Override
        public Entity shooter() {
            return this.projectile.getOwner();
        }
    }
}
