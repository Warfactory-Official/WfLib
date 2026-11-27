package com.wf.wflib.rail;

import com.wf.wflib.core.api.PlayerGap;
import com.wf.wflib.core.api.WFNetwork;
import com.wf.wflib.rail.align.AlignCompiler;
import com.wf.wflib.rail.api.Alignment;
import com.wf.wflib.rail.align.AlignmentData;
import com.wf.wflib.rail.align.RightOfWaySurvey;
import com.wf.wflib.rail.align.AlignmentEdits;
import com.wf.wflib.rail.align.AlignmentService;
import com.wf.wflib.rail.client.PublishedAlignments;
import com.wf.wflib.rail.client.RouteOwnership;
import java.util.UUID;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

/** The rail package's own payloads, registered here so the feature stays in one piece. */
public final class RailNetwork {

    private RailNetwork() {
    }

    public static void register(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar = event.registrar("rail-2").optional();

        registrar.playToServer(RailSavePacket.TYPE, RailSavePacket.STREAM_CODEC,
                (pkt, ctx) -> ctx.enqueueWork(() -> {
                    if (ctx.player() instanceof ServerPlayer player
                            && player.level() instanceof ServerLevel level
                            && SAVE_GAP.allow(player)) {
                        AlignmentEdits.SaveOutcome outcome = AlignmentEdits.save(player, level, pkt.id(),
                                pkt.name(), pkt.coreColour(), pkt.designClass(), pkt.points(),
                                pkt.baseRevision());
                        WFNetwork.sendToPlayer(player,
                                new RailSaveResultPacket(pkt.id(), outcome, revisionOf(level, pkt.id())));
                    }
                }));
        registrar.playToServer(RailStatusPacket.TYPE, RailStatusPacket.STREAM_CODEC,
                (pkt, ctx) -> ctx.enqueueWork(() -> {
                    if (ctx.player() instanceof ServerPlayer player
                            && player.level() instanceof ServerLevel level
                            && EDIT_GAP.allow(player)) {
                        AlignmentEdits.setStatus(player, level, pkt.id(), pkt.status());
                    }
                }));
        registrar.playToServer(RailBuildPacket.TYPE, RailBuildPacket.STREAM_CODEC,
                (pkt, ctx) -> ctx.enqueueWork(() -> {
                    if (ctx.player() instanceof ServerPlayer player
                            && player.level() instanceof ServerLevel level
                            && EDIT_GAP.allow(player)) {
                        AlignmentEdits.setBuilt(player, level, pkt.id(), pkt.from(), pkt.to(), pkt.laid());
                    }
                }));
        registrar.playToServer(RailDeletePacket.TYPE, RailDeletePacket.STREAM_CODEC,
                (pkt, ctx) -> ctx.enqueueWork(() -> {
                    if (ctx.player() instanceof ServerPlayer player
                            && player.level() instanceof ServerLevel level
                            && EDIT_GAP.allow(player)) {
                        AlignmentEdits.delete(player, level, pkt.id());
                    }
                }));
        registrar.playToServer(RailRightOfWayRequestPacket.TYPE, RailRightOfWayRequestPacket.STREAM_CODEC,
                (pkt, ctx) -> ctx.enqueueWork(() -> {
                    if (ctx.player() instanceof ServerPlayer player
                            && player.level() instanceof ServerLevel level
                            && ROW_GAP.allow(player)
                            && AlignmentEdits.valid(level.getWorldBorder(), pkt.points())) {
                        var centreline = AlignCompiler.compile(pkt.points(), pkt.designClass()).centreline();
                        WFNetwork.sendToPlayer(player, new RailRightOfWayPacket(
                                RightOfWaySurvey.survey(player, level, centreline, pkt.designClass())));
                    }
                }));
        registrar.playToClient(RailRightOfWayPacket.TYPE, RailRightOfWayPacket.STREAM_CODEC,
                (pkt, ctx) -> ctx.enqueueWork(
                        () -> RouteOwnership.accept(pkt)));
        registrar.playToClient(RailAlignmentPacket.TYPE, RailAlignmentPacket.STREAM_CODEC,
                (pkt, ctx) -> ctx.enqueueWork(
                        () -> PublishedAlignments.accept(pkt)));
        registrar.playToClient(RailSaveResultPacket.TYPE, RailSaveResultPacket.STREAM_CODEC,
                (pkt, ctx) -> ctx.enqueueWork(
                        () -> PublishedAlignments.acceptResult(pkt)));
    }

    /** The version a route is at now, which is what the client must save against next. */
    private static int revisionOf(ServerLevel level, UUID id) {
        Alignment alignment = AlignmentData.of(level).get(id);
        return alignment == null ? 0 : alignment.revision().number();
    }

    /**
     * Rate limit on the right-of-way query.
     *
     * <p>It is cheap (claim lookups over data already in memory, nothing loads a chunk), but it is
     * driven by a client dragging a point, and a client decides for itself how often to ask. This is
     * the server deciding instead.</p>
     */
    private static final PlayerGap ROW_GAP = new PlayerGap(150L);

    /**
     * Rate limit on saves.
     *
     * <p>A save compiles the route and writes the world's save data, so it is the expensive one. The
     * client already debounces at more than twice this, and a dropped save is not lost: the client keeps
     * its changes until an acknowledgement arrives and sends them again if one does not.</p>
     */
    private static final PlayerGap SAVE_GAP = new PlayerGap(400L);

    private static final PlayerGap EDIT_GAP = new PlayerGap(200L);

    @SubscribeEvent
    public static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            AlignmentService.push(player);
        }
    }

    /** A dimension change means a different set of routes entirely. */
    @SubscribeEvent
    public static void onChangeDimension(PlayerEvent.PlayerChangedDimensionEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            AlignmentService.push(player);
        }
    }

    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            AlignmentService.forget(player);
        }
    }
}
