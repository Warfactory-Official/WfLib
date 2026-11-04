package com.wf.wflib.drone.api;

import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.UUID;

/** One-step mission for a body already in the world: no pad, no spawn. */
public final class DroneTasking {

    private DroneTasking() {
    }

    /**
     * Grounded => home = where it stands. Airborne => replaces the program, flies there via TRANSIT.
     *
     * @return null on success, else why not
     */
    @Nullable
    public static String assign(ControllableDrone body, @Nullable UUID operator, DroneTask.Kind kind, Vec3 at) {
        return assign(body, operator, kind.at(at));
    }

    /** {@link #assign(ControllableDrone, UUID, DroneTask.Kind, Vec3)} with the step built by the caller. */
    @Nullable
    public static String assign(ControllableDrone body, @Nullable UUID operator, DroneTask task) {
        return assign(body, operator, DroneProgram.of(List.of(task)));
    }

    /** Whole program; replaces whatever it was flying. */
    @Nullable
    public static String assign(ControllableDrone body, @Nullable UUID operator, DroneProgram program) {
        for (DroneTask task : program.tasks()) {
            if (!body.supports(task.kind())) {
                return body.callsign() + " cannot fly " + task.kind().id();
            }
        }
        if (body.orders().isClassified()) {
            return "no link";
        }
        DroneState state = body.flight().getDroneState();
        if (state != DroneState.IDLE && !state.powered()) {
            return body.callsign() + " is " + state.name().toLowerCase(java.util.Locale.ROOT);
        }
        body.setOperatorId(operator);
        if (state == DroneState.IDLE) {
            body.route().setExfil(body.asEntity().position());
        }
        body.orders().setProgram(program);
        if (state != DroneState.IDLE) {
            body.flight().setState(DroneState.TRANSIT);
        }
        return null;
    }

    /**
     * A program that ends on a bare waypoint would end the mission there and fly home; ordered from a PDA the
     * operator means "go there and stay", so the last {@link DroneTask.MoveTo} becomes a station-hold.
     */
    public static DroneProgram holdAtEnd(List<DroneTask> tasks) {
        if (!tasks.isEmpty() && tasks.getLast() instanceof DroneTask.MoveTo last) {
            List<DroneTask> out = new java.util.ArrayList<>(tasks);
            out.set(out.size() - 1, new DroneTask.Loiter(last.at(), 0, 0));
            return DroneProgram.of(out);
        }
        return DroneProgram.of(tasks);
    }

    /** Drops the program and sets down where it is. */
    @Nullable
    public static String land(ControllableDrone body) {
        String error = airborne(body);
        if (error != null) {
            return error;
        }
        body.orders().setProgram(DroneProgram.EMPTY);
        body.flight().setState(DroneState.LANDING);
        return null;
    }

    /** Home = the exfil point it launched from. */
    @Nullable
    public static String recall(ControllableDrone body, @Nullable UUID operator) {
        String error = airborne(body);
        return error != null ? error : assign(body, operator, new DroneTask.Exfil());
    }

    /** Cruise height above ground, clamped to 8..256; takes effect on the next leg it climbs or descends to. */
    @Nullable
    public static String altitude(ControllableDrone body, double blocks) {
        if (body.orders().isClassified()) {
            return "no link";
        }
        body.flight().setCruiseAltitude(Mth.clamp(blocks, 8.0, 256.0));
        return null;
    }

    @Nullable
    private static String airborne(ControllableDrone body) {
        if (body.orders().isClassified()) {
            return "no link";
        }
        DroneState state = body.flight().getDroneState();
        return state.powered() ? null : body.callsign() + (state == DroneState.IDLE ? " is on the ground"
                : " is " + state.name().toLowerCase(java.util.Locale.ROOT));
    }
}
