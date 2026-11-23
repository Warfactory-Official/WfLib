package com.wf.wflib.continuum;

import com.hbm.config.RadiationConfig;
import com.hbm.util.ContaminationUtil;
import com.wf.wflib.core.api.WFSystems;
import com.wf.wflib.radiation.api.RadiationField;
import dev.golem.core.hook.Verdict;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;

/**
 * Radiation (docs, 5.1): Continuum's chunk field stays the owner. WFLib reads and feeds it through {@link RadiationField.Provider}, so Continuum's reactors,
 * bombs and hazard blocks fill the field WFLib's HUD, shielding, armour wear and hazard rows read. A player's dose is WFLib's alone: Continuum is told, through
 * its replaceable {@code RAD_IMMUNE} hook, that players are immune to <i>its</i> contamination, which is the gate every Continuum radiation path passes
 * ({@code ContaminationUtil.contaminate}, the per-tick handler). Other creatures stay Continuum's.
 */
final class RadiationBridge {
    private RadiationBridge() {
    }

    static boolean possible() {
        return WFSystems.enabled("hazard") && WFSystems.enabled("radiation");
    }

    static void install() {
        RadiationField.provider(new RadiationField.Provider() {
            @Override
            public double at(ServerLevel level, BlockPos pos) {
                return Math.max(com.hbm.handler.radiation.RadiationSystemNT.getRadForCoord(level, pos), RadiationField.OWN.at(level, pos));
            }

            @Override
            public double dose(ServerLevel level, BlockPos pos) {
                double field = com.hbm.handler.radiation.RadiationSystemNT.getRadForCoord(level, pos);
                if (level.dimension() == Level.NETHER && RadiationConfig.hellRad > 0 && field < RadiationConfig.hellRad) field = RadiationConfig.hellRad;
                return Math.max(field, RadiationField.OWN.dose(level, pos));
            }

            @Override
            public void source(ServerLevel level, BlockPos pos, double emission, double saturation) {
                com.hbm.handler.radiation.RadiationSystemNT.incrementRad(level, pos, emission, saturation);
            }
        });
        ContaminationUtil.RAD_IMMUNE.register(entity -> entity instanceof Player ? Verdict.ALLOW : Verdict.PASS);
        ContinuumBridge.LOG.info("Radiation: Continuum's field is read and fed by WFLib; players take WFLib's dose, not Continuum's");
    }
}
