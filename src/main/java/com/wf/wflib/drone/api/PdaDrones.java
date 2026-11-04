package com.wf.wflib.drone.api;

import com.wf.wflib.cam.api.CameraChannels;
import com.wf.wflib.cam.api.CameraNet;
import com.wf.wflib.cam.api.CameraSpec;
import com.wf.wflib.cam.api.Datalink;
import com.wf.wflib.cam.api.FeedTarget;
import com.wf.wflib.radio.api.RadioCarrier;
import com.wf.wflib.stream.api.HostWakeup;
import com.wf.wflib.stream.api.HostWakeupData;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import com.wf.wflib.cam.api.CameraContent;
import com.wf.wflib.pda.api.Pda;

/**
 * Drones app, server end: the held PDA's drone channels with state; CONNECT through {@link RemoteBody}; dispatch to a
 * linked autonomous body or the nearest pad. Classified bodies: no feed, no connect, no orders.
 */
public final class PdaDrones {
    /** = {@code maxBound} config ceiling. */
    static final int CAP = 32;
    private static final String NO_LINK = "NO LINK";

    public enum Action {
        SELECT, CONNECT, REMOVE
    }

    private PdaDrones() {
    }

    /** wflib's own body. */
    public static RemoteBody bodyOf(Entity entity) {
        return entity instanceof DroneEntity drone ? new DroneEntityBody(drone) : null;
    }

    public enum Link { PAIRED, ALREADY, FULL }

    /** Adds {@code body}'s feed to the PDA's channels; already there => selected. */
    public static Link link(ItemStack pda, UUID body) {
        CameraChannels channels = channels(pda);
        FeedTarget target = FeedTarget.ofDrone(body);
        if (channels.has(target)) {
            pda.set(CameraContent.CHANNELS.get(), channels.select(channels.targets().indexOf(target)));
            return Link.ALREADY;
        }
        int cap = CameraChannels.maxBound();
        if (channels.full(cap)) {
            return Link.FULL;
        }
        pda.set(CameraContent.CHANNELS.get(), channels.with(target, cap));
        return Link.PAIRED;
    }

    public static CameraChannels channels(ItemStack pda) {
        return pda.getOrDefault(CameraContent.CHANNELS.get(), CameraChannels.EMPTY);
    }

    /** Sneak + use a held PDA on a drone links it. */
    public static void onEntityInteract(PlayerInteractEvent.EntityInteract event) {
        Player player = event.getEntity();
        if (!(event.getTarget() instanceof DroneEntity drone) || !player.isShiftKeyDown()
                || !Pda.is(event.getItemStack())) {
            return;
        }
        event.setCanceled(true);
        event.setCancellationResult(InteractionResult.sidedSuccess(player.level().isClientSide));
        if (!player.level().isClientSide) {
            player.displayClientMessage(Component.translatable(drone.orders().isClassified() ? "wflib.pda.link.classified"
                    : "wflib.pda.link." + link(event.getItemStack(), drone.getUUID()).name().toLowerCase(Locale.ROOT)),
                    true);
        }
    }

    /** Gametest seam. */
    public static DroneStatus status(ServerPlayer player) {
        CameraChannels channels = channels(Pda.carriedStack(player));
        List<DroneStatus.Entry> out = new ArrayList<>();
        for (FeedTarget target : channels.targets()) {
            target.drone().ifPresent(id -> out.add(entry(player, id, target)));
        }
        return new DroneStatus(out, Optional.ofNullable(DroneDispatchers.nearest(player)).map(d -> {
            List<String> offers = new ArrayList<>();
            for (DroneTask.Kind kind : d.offers()) {
                offers.add(kind.id());
            }
            return new DroneStatus.Pad(d.dispatchPos(),
                    (int) Math.sqrt(d.dispatchPos().distToCenterSqr(player.position())), offers);
        }));
    }

    private static DroneStatus.Entry entry(ServerPlayer player, UUID id, FeedTarget target) {
        ServerLevel level = player.serverLevel();
        Entity e = level.getEntity(id);
        String shortId = id.toString().substring(0, 8);
        if (e == null || !e.isAlive()) {
            DroneRecall.recall(level, id);
            HostWakeupData.Entry seen = HostWakeup.lookup(player.server, id);
            String status = seen == null ? "OUT OF RANGE" : seen.dimension() == level.dimension() ? "ASLEEP"
                    : "OTHER DIMENSION";
            return offline(id, shortId, status, "not loaded");
        }
        ControllableDrone body = DroneTracker.byId(level, id);
        if (body != null && body.orders().isClassified()) {
            return offline(id, body.callsign(), NO_LINK, "no link");
        }
        RemoteBody remote = RemoteBody.of(e);
        String name = body != null ? body.callsign() : remote != null ? remote.name() : e.getName().getString();
        DroneState state = body == null ? null : body.flight().getDroneState();
        String status = state != null && state != DroneState.IDLE ? state.name()
                : remote != null ? remote.status() : "ONLINE";
        DroneTask task = body == null ? null : body.orders().getProgram().current();
        Vec3 operator = Datalink.operatorAt(player);
        double distance = operator.distanceTo(e.position());
        CameraSpec spec = CameraNet.specOf(e);
        String refusal = remote == null ? "no manual control" : Datalink.jammed(level, e) ? "jammed"
                : remote.connectRefusal(player);
        List<String> offers = new ArrayList<>();
        if (body != null) {
            for (DroneTask.Kind kind : DroneTask.Kind.values()) {
                if (body.supports(kind)) {
                    offers.add(kind.id());
                }
            }
        }
        return new DroneStatus.Entry(id, target.resolve(level), name, status, task == null ? "" : task.label(),
                body != null ? (float) body.chargeFraction() : remote != null ? remote.fuel() : 0.0f,
                remote != null && remote.electric(),
                spec == null ? 0.0f : Datalink.quality(level, spec, operator, e, e.position()),
                remote == null ? List.of() : remote.cells(), remote == null ? List.of() : remote.ammo(),
                (int) distance, refusal == null ? "" : refusal, offers,
                e instanceof RadioCarrier rc && rc.radioVoice() != null,
                e instanceof RadioCarrier rc ? rc.radioStation() : "", body == null ? List.of() : route(body));
    }

    private static DroneStatus.Entry offline(UUID id, String name, String status, String refusal) {
        return new DroneStatus.Entry(id, 0, name, status, "", 0.0f, false, 0.0f, List.of(), List.of(), -1, refusal,
                List.of(), false, "", List.of());
    }

    @Nullable
    private static UUID channelDrone(ItemStack pda, int index) {
        CameraChannels channels = channels(pda);
        return index >= 0 && index < channels.size() ? channels.targets().get(index).drone().orElse(null) : null;
    }

    public static void act(ServerPlayer player, Action action, int index) {
        ItemStack pda = Pda.carriedStack(player);
        UUID id = channelDrone(pda, index);
        if (id == null) {
            return;
        }
        switch (action) {
            case SELECT -> {
                pda.set(CameraContent.CHANNELS.get(), channels(pda).select(index));
                if (player.serverLevel().getEntity(id) == null) {
                    wake(player, id, false);
                }
            }
            case CONNECT -> wake(player, id, true);
            case REMOVE -> pda.set(CameraContent.CHANNELS.get(), channels(pda).without(index));
        }
    }

    /** Asleep => load first; {@code connect} then takes manual control, else subscribes the feed. */
    private static void wake(ServerPlayer player, UUID id, boolean connect) {
        HostWakeup.Result result = HostWakeup.request(player, id, new HostWakeup.WakeCallback() {
            @Override
            public void onWake(ServerPlayer requester, Entity host) {
                if (!connect) {
                    CameraNet.subscribe(requester, host.getId());
                    return;
                }
                ControllableDrone body = DroneTracker.byId(requester.serverLevel(), host.getUUID());
                RemoteBody remote = body != null && body.orders().isClassified() ? null : RemoteBody.of(host);
                String refusal = remote == null ? "no link"
                        : Datalink.jammed(requester.serverLevel(), host) ? "jammed" : remote.connectRefusal(requester);
                if (refusal != null) {
                    requester.displayClientMessage(Component.literal(refusal), true);
                } else {
                    remote.connect(requester);
                }
            }

            @Override
            public void onTimeout(ServerPlayer requester) {
                requester.displayClientMessage(Component.literal("link timed out"), true);
            }
        });
        if (result == HostWakeup.Result.UNKNOWN && player.serverLevel().getEntity(id) == null) {
            DroneRecall.recall(player.serverLevel(), id);
        }
    }

    /** Order verbs a linked autonomous body takes besides the {@link DroneTask.Kind} ids. */
    public static final List<String> CONTROLS = List.of("stop", "hold", "rtb", "land", "alt", "climb");

    /**
     * @param verb a {@link DroneTask.Kind} id (one-step {@link #program}), or one of {@link #CONTROLS} (linked body
     *      only): {@code stop} = hold where it is, {@code hold} = hold over {@code at}, {@code alt} / {@code climb} =
     *      cruise altitude set to / raised by {@code arg}
     * @param arg loiter radius (&lt;= 0: default), or the altitude for {@code alt} / {@code climb}
     * @param drone linked autonomous body to task; empty => nearest pad
     * @return reply line
     */
    public static String dispatch(ServerPlayer player, String verb, Vec3 at, double arg, Optional<UUID> drone) {
        if (!Double.isFinite(at.x + at.y + at.z + arg)) {
            return "err: bad coordinates";
        }
        if (!CONTROLS.contains(verb)) {
            if (Arrays.stream(DroneTask.Kind.values()).noneMatch(k -> k.id().equals(verb))) {
                return "err: unknown order '" + verb + "'";
            }
            DroneTask.Kind kind = DroneTask.Kind.byId(verb);
            return program(player, List.of(kind == DroneTask.Kind.LOITER && arg > 0
                    ? new DroneTask.Loiter(at, arg, 0) : kind.at(at)), drone);
        }
        if (!Pda.carries(player)) {
            return "err: no PDA carried";
        }
        if (drone.isEmpty()) {
            return "err: " + verb + " needs a linked drone selected";
        }
        try {
            return control(player, linked(player, Pda.carriedStack(player), drone.get()), verb, at, arg);
        } catch (IllegalStateException e) {
            return "err: " + e.getMessage();
        }
    }

    /**
     * Queue for a linked autonomous body, else launched from the nearest pad; a trailing waypoint holds
     * ({@link DroneTasking#holdAtEnd}). Pad launches join the held PDA's channels.
     *
     * @return reply line
     */
    public static String program(ServerPlayer player, List<DroneTask> tasks, Optional<UUID> drone) {
        if (!Pda.carries(player)) {
            return "err: no PDA carried";
        }
        if (tasks.isEmpty() || tasks.size() > DroneTask.MAX_STEPS) {
            return "err: 1-" + DroneTask.MAX_STEPS + " steps";
        }
        for (DroneTask task : tasks) {
            Vec3 at = task.at();
            if (at != null && !Double.isFinite(at.x + at.y + at.z)) {
                return "err: bad coordinates";
            }
        }
        DroneProgram program = DroneTasking.holdAtEnd(tasks);
        String plan = describe(program);
        ItemStack pda = Pda.carriedStack(player);
        if (drone.isPresent()) {
            try {
                ControllableDrone body = linked(player, pda, drone.get());
                String error = DroneTasking.assign(body, player.getUUID(), program);
                return error != null ? "err: " + error : body.callsign() + " " + plan;
            } catch (IllegalStateException e) {
                return "err: " + e.getMessage();
            }
        }
        DroneDispatcher dispatcher = DroneDispatchers.nearest(player);
        if (dispatcher == null) {
            return "err: no drone pad in reach";
        }
        DroneMission.Result result = dispatcher.dispatch(player, program);
        if (!result.ok()) {
            return "err: " + result.error();
        }
        CameraChannels channels = channels(pda);
        int cap = CameraChannels.maxBound();
        for (ControllableDrone launched : result.drones()) {
            channels = channels.with(FeedTarget.ofDrone(launched.droneId()), cap);
        }
        pda.set(CameraContent.CHANNELS.get(), channels);
        return String.format(Locale.ROOT, "pad x%d launched: %s", result.ordered(), plan);
    }

    /** "hold over 1000 5800", or "3 steps, ends hold over ...". */
    private static String describe(DroneProgram program) {
        DroneTask last = program.tasks().getLast();
        Vec3 at = last.at();
        String end = switch (last) {
            case DroneTask.Exfil e -> "rtb";
            case DroneTask.Loiter l when l.stationary() -> "hold over";
            case DroneTask.Loiter l -> "loiter r" + (int) l.radius();
            default -> last.kind().id();
        } + (at == null ? "" : String.format(Locale.ROOT, " %d %d", (int) Math.floor(at.x), (int) Math.floor(at.z)));
        int steps = program.tasks().size();
        return steps == 1 ? end : steps + " steps, ends " + end;
    }

    /** Linked autonomous body the player may order. @throws IllegalStateException why not */
    private static ControllableDrone linked(ServerPlayer player, ItemStack pda, UUID id) {
        if (!channels(pda).has(FeedTarget.ofDrone(id))) {
            throw new IllegalStateException("not linked");
        }
        ControllableDrone body = DroneTracker.byId(player.serverLevel(), id);
        if (body == null) {
            throw new IllegalStateException("drone not in range");
        }
        RemoteBody remote = RemoteBody.of(body.asEntity());
        String refusal = remote == null ? null : remote.connectRefusal(player);
        if (refusal != null && !body.asEntity().hasPassenger(player)) {
            throw new IllegalStateException(refusal);
        }
        return body;
    }

    private static String control(ServerPlayer player, ControllableDrone body, String verb, Vec3 at, double arg) {
        String name = body.callsign();
        UUID operator = player.getUUID();
        Vec3 here = body.asEntity().position();
        String error;
        String done;
        switch (verb) {
            case "stop" -> {
                error = DroneTasking.assign(body, operator, new DroneTask.Loiter(here, 0, 0));
                done = String.format(Locale.ROOT, "%s holding at %d %d", name, (int) here.x, (int) here.z);
            }
            case "hold" -> {
                error = DroneTasking.assign(body, operator, new DroneTask.Loiter(at, 0, 0));
                done = String.format(Locale.ROOT, "%s hold over %d %d", name, (int) at.x, (int) at.z);
            }
            case "rtb" -> {
                error = DroneTasking.recall(body, operator);
                Vec3 home = body.route().getExfil();
                done = String.format(Locale.ROOT, "%s returning to %d %d", name, (int) home.x, (int) home.z);
            }
            case "land" -> {
                error = DroneTasking.land(body);
                done = String.format(Locale.ROOT, "%s landing at %d %d", name, (int) here.x, (int) here.z);
            }
            default -> {
                error = DroneTasking.altitude(body, verb.equals("climb") ? body.flight().getCruiseAltitude() + arg : arg);
                done = String.format(Locale.ROOT, "%s cruise altitude %d", name, (int) body.flight().getCruiseAltitude());
            }
        }
        return error != null ? "err: " + error : done;
    }

    /** Where it is, then each step still to fly; exfil-bound => home last. Empty for bodies with no program. */
    private static List<Vec3> route(ControllableDrone body) {
        List<Vec3> out = new ArrayList<>();
        out.add(body.asEntity().position());
        DroneProgram program = body.orders().getProgram();
        Vec3 home = body.route().getExfil();
        for (int i = program.cursor(); i < program.tasks().size(); i++) {
            Vec3 at = program.tasks().get(i).at();
            out.add(at != null ? at : home);
        }
        if (body.flight().getDroneState() == DroneState.EXFIL) {
            out.add(home);
        }
        return out.size() > 1 ? out : List.of();
    }

    /** wflib's own drone: CONNECT = remote ride ({@link DroneEntity#pilot}). */
    private record DroneEntityBody(DroneEntity drone) implements RemoteBody {
        @Override
        public String name() {
            return this.drone.callsign();
        }

        @Override
        public String status() {
            return this.drone.flight().getDroneState().name();
        }

        @Override
        public float fuel() {
            return this.drone.battery().percent() / 100.0f;
        }

        @Override
        public boolean electric() {
            return true;
        }

        @Override
        public List<Cell> cells() {
            return List.of();
        }

        @Override
        public List<String> ammo() {
            return List.of();
        }

        @Nullable
        @Override
        public String connectRefusal(ServerPlayer player) {
            if (this.drone.cameraSpec() == null) {
                return "no camera";
            }
            if (this.drone.damage().crashed()) {
                return "downed";
            }
            return this.drone.pilot() != null ? "in use" : null;
        }

        @Override
        public void connect(ServerPlayer player) {
            this.drone.setDetachedBodyAnchor(player, player.position());
            if (!player.startRiding(this.drone, true)) {
                this.drone.setDetachedBodyAnchor(player, null);
            }
        }
    }
}
