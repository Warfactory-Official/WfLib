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
                e instanceof RadioCarrier rc ? rc.radioStation() : "");
    }

    private static DroneStatus.Entry offline(UUID id, String name, String status, String refusal) {
        return new DroneStatus.Entry(id, 0, name, status, "", 0.0f, false, 0.0f, List.of(), List.of(), -1, refusal,
                List.of(), false, "");
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

    /**
     * @param drone linked autonomous body to task; empty => nearest pad
     * @return reply line; pad launches join the held PDA's channels
     */
    public static String dispatch(ServerPlayer player, DroneTask.Kind kind, Vec3 at, Optional<UUID> drone) {
        if (!Pda.carries(player)) {
            return "err: no PDA carried";
        }
        ItemStack pda = Pda.carriedStack(player);
        if (drone.isPresent()) {
            UUID id = drone.get();
            if (!channels(pda).has(FeedTarget.ofDrone(id))) {
                return "err: not linked";
            }
            ControllableDrone body = DroneTracker.byId(player.serverLevel(), id);
            if (body == null) {
                return "err: drone not in range";
            }
            RemoteBody remote = RemoteBody.of(body.asEntity());
            String refusal = remote == null ? null : remote.connectRefusal(player);
            if (refusal != null && !body.asEntity().hasPassenger(player)) {
                return "err: " + refusal;
            }
            String error = DroneTasking.assign(body, player.getUUID(), kind, at);
            return error != null ? "err: " + error : String.format(Locale.ROOT, "%s %s toward %d %d",
                    body.callsign(), kind.id(), (int) at.x, (int) at.z);
        }
        DroneDispatcher dispatcher = DroneDispatchers.nearest(player);
        if (dispatcher == null) {
            return "err: no drone pad in reach";
        }
        DroneMission.Result result = dispatcher.dispatch(player, kind, at);
        if (!result.ok()) {
            return "err: " + result.error();
        }
        CameraChannels channels = channels(pda);
        int cap = CameraChannels.maxBound();
        for (ControllableDrone launched : result.drones()) {
            channels = channels.with(FeedTarget.ofDrone(launched.droneId()), cap);
        }
        pda.set(CameraContent.CHANNELS.get(), channels);
        return String.format(Locale.ROOT, "%s x%d launched toward %d %d", kind.id(), result.ordered(), (int) at.x,
                (int) at.z);
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
