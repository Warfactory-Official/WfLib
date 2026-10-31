package com.wf.wflib.missile.flight;

import com.wf.wflib.missile.api.MissileEntity;
import net.minecraft.resources.ResourceLocation;

import java.util.EnumMap;
import java.util.Map;
import com.wf.wflib.missile.api.AscentStage;
import com.wf.wflib.missile.api.FlightStage;
import com.wf.wflib.missile.api.FlightStages;

/** A composed flight: one {@link FlightStage} per {@link MissileEntity.Phase}. */
public final class FlightProfile {

    private final EnumMap<MissileEntity.Phase, FlightStage> stages;

    public FlightProfile(Map<MissileEntity.Phase, FlightStage> stages) {
        this.stages = new EnumMap<>(stages);
    }

    /**
     * Compose a profile from the three canonical stages.
     */
    public static FlightProfile of(FlightStage ascent, FlightStage cruise, FlightStage attack) {
        EnumMap<MissileEntity.Phase, FlightStage> map = new EnumMap<>(MissileEntity.Phase.class);
        map.put(MissileEntity.Phase.ASCEND, ascent);
        map.put(MissileEntity.Phase.CRUISE, cruise);
        map.put(MissileEntity.Phase.ATTACK, attack);
        return new FlightProfile(map);
    }

    /**
     * The default guided cruise missile: vertical boost -> terrain/high-altitude cruise -> terminal dive.
     */
    public static FlightProfile standard() {
        return of(AscentStage.INSTANCE, CruiseStage.INSTANCE, AttackStage.INSTANCE);
    }

    /**
     * Compose a profile by resolving one stage id per phase through the {@link FlightStages}.
     */
    public static FlightProfile fromIds(ResourceLocation ascentId, ResourceLocation cruiseId, ResourceLocation attackId) {
        return of(FlightStages.get(MissileEntity.Phase.ASCEND, ascentId),
                FlightStages.get(MissileEntity.Phase.CRUISE, cruiseId),
                FlightStages.get(MissileEntity.Phase.ATTACK, attackId));
    }

    /**
     * @return the stage that flies the given phase.
     */
    public FlightStage stage(MissileEntity.Phase phase) {
        return this.stages.get(phase);
    }
}
