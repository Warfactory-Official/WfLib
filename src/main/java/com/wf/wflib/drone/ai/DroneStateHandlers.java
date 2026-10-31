package com.wf.wflib.drone.ai;

import com.wf.wflib.drone.api.DroneState;
import com.wf.wflib.drone.ai.state.CollectHandler;
import com.wf.wflib.drone.ai.state.SupplyHandler;
import com.wf.wflib.drone.ai.state.WorkHandler;
import com.wf.wflib.drone.ai.state.DeliverHandler;
import com.wf.wflib.drone.ai.state.DepletedHandler;
import com.wf.wflib.drone.ai.state.DownedHandler;
import com.wf.wflib.drone.ai.state.ExfilHandler;
import com.wf.wflib.drone.ai.state.IdleHandler;
import com.wf.wflib.drone.ai.state.LandingHandler;
import com.wf.wflib.drone.ai.state.MineLayHandler;
import com.wf.wflib.drone.api.MusterHandler;
import com.wf.wflib.drone.ai.state.PayloadRunHandler;
import com.wf.wflib.drone.ai.state.SurveilHandler;
import com.wf.wflib.drone.ai.state.TakeoffHandler;
import com.wf.wflib.drone.ai.state.TransitHandler;

import java.util.EnumMap;
import java.util.Map;
import com.wf.wflib.drone.api.DroneStateHandler;

/** Binds each {@link DroneState} to the handler that flies it. */
public final class DroneStateHandlers {

    private static final Map<DroneState, DroneStateHandler> BY_STATE = new EnumMap<>(DroneState.class);

    /** Every state gets exactly one handler; a missing one throws. */
    public static void define() {
        register(IdleHandler.INSTANCE);
        register(TakeoffHandler.INSTANCE);
        register(MusterHandler.INSTANCE);
        register(TransitHandler.INSTANCE);
        register(DeliverHandler.INSTANCE);
        register(CollectHandler.INSTANCE);
        register(WorkHandler.INSTANCE);
        register(SupplyHandler.INSTANCE);
        register(SurveilHandler.INSTANCE);
        register(ExfilHandler.INSTANCE);
        register(LandingHandler.INSTANCE);
        register(PayloadRunHandler.INSTANCE);
        register(MineLayHandler.INSTANCE);
        register(DepletedHandler.INSTANCE);
        register(DownedHandler.INSTANCE);
        for (DroneState state : DroneState.values()) {
            if (!BY_STATE.containsKey(state)) {
                throw new IllegalStateException("No handler for " + state);
            }
        }
    }

    private DroneStateHandlers() {
    }

    private static void register(DroneStateHandler handler) {
        if (BY_STATE.put(handler.state(), handler) != null) {
            throw new IllegalStateException("Duplicate handler for " + handler.state());
        }
    }

    public static DroneStateHandler get(DroneState state) {
        return BY_STATE.get(state);
    }
}
