package com.wf.wflib.warhead;

import com.wf.wflib.explosion.api.ExplosionAEF;
import com.wf.wflib.explosion.api.BlockAllocatorStandard;
import com.wf.wflib.explosion.api.BlockProcessorStandard;
import com.wf.wflib.explosion.api.EntityProcessorCross;
import com.wf.wflib.explosion.api.PlayerProcessorStandard;
import com.wf.wflib.fire.api.FireType;
import com.wf.wflib.fire.api.FireUtil;
import com.wf.wflib.explosion.api.ExplosionCreator;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import com.wf.wflib.warhead.api.WarheadCarrier;
import com.wf.wflib.core.api.WFLib;

/** Fuel-air: blast as STANDARD at size, entity reach x{@link #OVERPRESSURE_REACH}, brief fireball. */
public final class ThermobaricWarhead {

    public static final ResourceLocation ID = ResourceLocation.fromNamespaceAndPath(WFLib.MODID, "thermobaric");
    public static final float SIZE = 6F;
    static final float OVERPRESSURE_REACH = 2F;
    private static final int FIREBALL_TICKS = 100;

    private ThermobaricWarhead() {
    }

    public static void detonate(WarheadCarrier source, Vec3 pos) {
        Level level = source.level();
        if (level.isClientSide) {
            return;
        }
        float size = source.blastSize() > 0 ? source.blastSize() : SIZE;
        var xnt = new ExplosionAEF(level, pos.x, pos.y, pos.z, size, source.exploder());
        if (source.breaksBlocks()) {
            xnt.setBlockAllocator(new BlockAllocatorStandard(size < 8 ? 16 : 32));
            xnt.setBlockProcessor(new BlockProcessorStandard().setNoDrop());
        }
        xnt.setEntityProcessor(new EntityProcessorCross().withRangeMod(OVERPRESSURE_REACH));
        xnt.setPlayerProcessor(new PlayerProcessorStandard());
        xnt.igniterFaction(source.igniterFactionId());
        xnt.explode();
        ExplosionCreator.composeEffectLarge(level, pos.x, pos.y, pos.z);
        FireUtil.spawn(level, pos, Math.round(size), 1.5f, FIREBALL_TICKS, FireType.NORMAL);
    }
}
