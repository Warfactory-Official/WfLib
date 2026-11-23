package com.wf.wflib.continuum;

import com.hbm.api.block.IToolable;
import com.hbm.blocks.bomb.Landmine;
import com.hbm.interfaces.IBomb;
import com.hbm.tileentity.bomb.TileEntityLandmine;
import com.wf.wflib.core.api.WFSystems;
import com.wf.wflib.demolition.api.IDetonatable;
import com.wf.wflib.probe.api.ProbeAction;
import com.wf.wflib.probe.api.ProbeActions;
import com.wf.wflib.probe.api.ProbeProviders;
import net.minecraft.ChatFormatting;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.InteractionHand;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.neoforged.fml.loading.FMLEnvironment;

/**
 * Continuum's landmines and charges on WFLib's tools. Its blocks are {@code IBomb}s: tagged {@code wflib:explosive} (the bridge's tag file) they can be wired to
 * a WFLib detonator, and set off with the rest of its circuit. A landmine also shows in the probe, with what Continuum keeps to itself (primed, waiting for a
 * player) and a defuse row for a player holding Continuum's defuser. WFLib's own mines are entities; Continuum's are blocks, so nothing of theirs is replaced.
 */
final class ExplosivesBridge {
    private ExplosivesBridge() {
    }

    static final ResourceLocation DEFUSE = ResourceLocation.fromNamespaceAndPath(ContinuumBridge.ID, "defuse_mine");
    private static final String PRIMED = "Primed", WAITING = "Waiting";

    static void install() {
        if (WFSystems.enabled("demolition")) IDetonatable.ADAPTERS.add(ExplosivesBridge::adapt);
        if (!WFSystems.enabled("probe")) return;
        ProbeActions.registerBlock(DEFUSE, (player, level, pos, state, arg) -> defuse(player, level, pos, state));
        ProbeProviders.addBlockData(ResourceLocation.fromNamespaceAndPath(ContinuumBridge.ID, "landmine_data"),
                state -> state.getBlock() instanceof Landmine,
                (data, player, level, pos, state, be) -> {
                    if (be instanceof TileEntityLandmine mine) {
                        data.putBoolean(PRIMED, mine.isPrimed());
                        data.putBoolean(WAITING, mine.waitingForPlayer);
                    }
                }, 0);
        if (FMLEnvironment.dist.isClient()) installProbe();
        ContinuumBridge.LOG.info("Explosives: Continuum's mines and charges can be wired to a WFLib detonator; its landmines show in the probe");
    }

    /** Lift a landmine as an item without setting it off; needs Continuum's defuser in the main hand. */
    static boolean defuse(Player player, Level level, BlockPos pos, BlockState state) {
        return state.getBlock() instanceof Landmine mine
                && IToolable.ToolType.getType(player.getMainHandItem()) == IToolable.ToolType.DEFUSER
                && mine.onScrew(level, player, pos, Direction.UP, Vec3.atCenterOf(pos), InteractionHand.MAIN_HAND, IToolable.ToolType.DEFUSER);
    }

    /** The detonatable behind a Continuum bomb block; WFLib asks only blocks of the {@code wflib:explosive} tag. */
    static IDetonatable adapt(BlockState state) {
        if (!(state.getBlock() instanceof IBomb bomb)) return null;
        return (level, pos, s, player) -> bomb.explode(level, pos, player, player == null ? null : player.getUUID());
    }

    /** What the panel says of a mine, as lines of text. */
    static String status(boolean primed, boolean waiting) {
        return waiting ? "Dormant until a player is near" : primed ? "Armed" : "Arming: waiting for everyone to clear";
    }

    private static void installProbe() {
        ProbeProviders.addBlocks(ResourceLocation.fromNamespaceAndPath(ContinuumBridge.ID, "landmine"),
                state -> state.getBlock() instanceof Landmine,
                (info, ctx, state, pos, be) -> {
                    Landmine mine = (Landmine) state.getBlock();
                    info.entry(Component.literal("Kind"), Component.literal(mine.type.name().toLowerCase(java.util.Locale.ROOT)));
                    info.entry(Component.literal("Trigger"), Component.literal(mine.range + " blocks wide, " + mine.height + " high"));
                    if (ctx.hasData()) {
                        boolean primed = ctx.data().getBoolean(PRIMED), waiting = ctx.data().getBoolean(WAITING);
                        info.text(Component.literal(status(primed, waiting)), primed ? ChatFormatting.RED : ChatFormatting.GOLD);
                    }
                    ProbeAction defuse = ProbeAction.of(DEFUSE, Component.literal("Defuse"));
                    ItemStack held = ctx.mainHand();
                    info.action(IToolable.ToolType.getType(held) == IToolable.ToolType.DEFUSER ? defuse : defuse.unavailable(Component.literal("Hold a defuser")));
                }, 0);
    }
}
