package com.wf.wflib.damage.api;

import net.minecraft.core.Holder;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.entity.Entity;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

public final class AttributedDamageSource extends DamageSource implements Attributed {

    private final UUID faction;

    AttributedDamageSource(Holder<DamageType> type, @Nullable Entity direct, @Nullable Entity attacker, UUID faction) {
        super(type, direct, attacker);
        this.faction = faction;
    }

    @Override
    public UUID attributedFaction() {
        return this.faction;
    }
}
