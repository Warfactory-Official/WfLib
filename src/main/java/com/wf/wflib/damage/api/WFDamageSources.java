package com.wf.wflib.damage.api;

import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

/** Builds {@link DamageSource}s for the mod's {@link DamageClass}es. */
public final class WFDamageSources {

    private WFDamageSources() {
    }

    /**
     * @param attacker the entity ultimately responsible (the shooter / the placer of the charge), or null
     * @param direct the immediate cause (the projectile), or null to reuse {@code attacker}
     */
    public static DamageSource create(Level level, DamageClass clazz, @Nullable Entity attacker, @Nullable Entity direct) {
        Holder<DamageType> holder = level.registryAccess()
                .registryOrThrow(Registries.DAMAGE_TYPE)
                .getHolderOrThrow(clazz.type);
        return new DamageSource(holder, direct, attacker);
    }

    /** {@code faction} null => plain source; else readable through {@link Attribution#faction}. */
    public static DamageSource create(Level level, DamageClass clazz, @Nullable Entity attacker,
                                      @Nullable Entity direct, @Nullable UUID faction) {
        if (faction == null) {
            return create(level, clazz, attacker, direct);
        }
        Holder<DamageType> holder = level.registryAccess()
                .registryOrThrow(Registries.DAMAGE_TYPE)
                .getHolderOrThrow(clazz.type);
        return new AttributedDamageSource(holder, direct, attacker, faction);
    }

    /** {@code type} as vanilla's constructor takes it; {@code faction} null => plain source. */
    public static DamageSource of(Holder<DamageType> type, @Nullable Entity direct, @Nullable Entity attacker,
                                  @Nullable UUID faction) {
        return faction == null ? new DamageSource(type, direct, attacker)
                : new AttributedDamageSource(type, direct, attacker, faction);
    }

    public static DamageSource create(Level level, DamageClass clazz, @Nullable Entity attacker) {
        return create(level, clazz, attacker, attacker);
    }
}
