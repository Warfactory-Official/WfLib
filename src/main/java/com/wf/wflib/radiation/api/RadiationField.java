package com.wf.wflib.radiation.api;

import com.wf.wflib.radiation.RadiationSystemNT;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;

/** The chunk radiation field, read and fed from outside the radiation system. */
public final class RadiationField {
    private RadiationField() {
    }

    /** Field at {@code pos}, RAD. */
    public static double at(ServerLevel level, BlockPos pos) {
        return RadiationSystemNT.getRadForCoord(level, pos);
    }

    /** Dose rate an entity at {@code pos} receives, RAD/s, before protection. */
    public static double dose(ServerLevel level, BlockPos pos) {
        return RadiationSystemNT.doseAt(level, pos);
    }

    /** Adds a source of {@code emission} RAD/s that saturates at {@code saturation} RAD. */
    public static void source(ServerLevel level, BlockPos pos, double emission, double saturation) {
        RadiationSystemNT.incrementRad(level, pos, emission, saturation);
    }
}
