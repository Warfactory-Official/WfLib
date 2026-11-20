package com.wf.wflib.radiation.api;

import com.wf.wflib.radiation.RadiationSystemNT;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;

/**
 * The chunk radiation field, read and fed from outside the radiation system. Everything outside it (item hazards, contamination, the Geiger
 * HUD, dose on entities) goes through here, so another mod that owns a radiation field of its own can be the one these read and feed
 * ({@link #provider}): WFLib's field then idles and what the rest of WFLib does about radiation (armour wear and shielding, artefact soak,
 * the HUD, hazard rows) runs on the other field.
 */
public final class RadiationField {
    /** Where the field lives. The default is WFLib's own. */
    public interface Provider {
        /** Field at {@code pos}, RAD. */
        double at(ServerLevel level, BlockPos pos);

        /** Dose rate an entity at {@code pos} receives, RAD/s, before protection. */
        double dose(ServerLevel level, BlockPos pos);

        /** Adds a source of {@code emission} RAD/s that saturates at {@code saturation} RAD. */
        void source(ServerLevel level, BlockPos pos, double emission, double saturation);
    }

    /** WFLib's own field. */
    public static final Provider OWN = new Provider() {
        @Override
        public double at(ServerLevel level, BlockPos pos) {
            return RadiationSystemNT.getRadForCoord(level, pos);
        }

        @Override
        public double dose(ServerLevel level, BlockPos pos) {
            return RadiationSystemNT.doseAt(level, pos);
        }

        @Override
        public void source(ServerLevel level, BlockPos pos, double emission, double saturation) {
            RadiationSystemNT.incrementRad(level, pos, emission, saturation);
        }
    };

    private static volatile Provider provider = OWN;

    private RadiationField() {
    }

    /** Makes {@code p} the field everything outside the radiation system reads and feeds. Call once, at setup. */
    public static void provider(Provider p) {
        provider = p == null ? OWN : p;
    }

    /** Field at {@code pos}, RAD. */
    public static double at(ServerLevel level, BlockPos pos) {
        return provider.at(level, pos);
    }

    /** Dose rate an entity at {@code pos} receives, RAD/s, before protection. */
    public static double dose(ServerLevel level, BlockPos pos) {
        return provider.dose(level, pos);
    }

    /** Adds a source of {@code emission} RAD/s that saturates at {@code saturation} RAD. */
    public static void source(ServerLevel level, BlockPos pos, double emission, double saturation) {
        provider.source(level, pos, emission, saturation);
    }
}
