package com.wf.wflib.orbital;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

import java.util.UUID;
import com.wf.wflib.warhead.api.WarheadCarrier;
import com.wf.wflib.warhead.api.Warheads;

public final class OrbitalWarheads {

    public static final ResourceLocation KINETIC_ROD = Warheads.rl("kinetic_rod");
    public static final ResourceLocation CARGO_LANDING = Warheads.rl("cargo_landing");
    public static final ResourceLocation CARGO_LANDING_SOFT = Warheads.rl("cargo_landing_soft");

    private OrbitalWarheads() {
    }

    public static Warheads.Detonation detonation(ResourceLocation effect) {
        return (source, pos) -> impact(source, pos, effect);
    }

    private static void impact(WarheadCarrier source, Vec3 pos, ResourceLocation effect) {
        Level level = source.level();
        if (!(level instanceof ServerLevel server)) {
            return;
        }
        UUID id = source instanceof Entity entity ? entity.getUUID() : null;
        OrbitalImpact.apply(server, pos.x, pos.z, effect, source.igniterFactionId(), id);
    }
}
