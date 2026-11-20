package com.wf.wflib.continuum;

import com.hbm.interfaces.IEntityLookOverlay;
import com.hbm.interfaces.ILookOverlay;
import com.wf.wflib.core.api.WFSystems;
import com.wf.wflib.probe.api.ProbeInfo;
import com.wf.wflib.probe.api.ProbeProviders;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.fml.loading.FMLEnvironment;

/**
 * The look-at panel (docs, 3.6). NTM's {@code ILookOverlay} is WFLib's probe's ancestor, so a Continuum block or entity that has a readout gets it in the probe:
 * its title in the title colour and each line as a line of text, the legacy {@code &[<rgb>&]} colour prefix honoured. Client only. Continuum draws the same
 * readout itself beside the crosshair when its own overlay is on; with both on a player sees it twice, so {@link #install} switches Continuum's off.
 */
final class LookOverlayBridge {
    private LookOverlayBridge() {
    }

    /** Appends {@code data} to {@code info}: the title, then a line each. */
    static void fill(ProbeInfo info, ILookOverlay.Data data) {
        info.title(Component.literal(data.title()), 0xFF000000 | data.titleColor());
        for (String line : data.lines()) {
            int colour = 0xFFFFFF;
            if (line.startsWith("&[")) {
                int end = line.lastIndexOf("&]");
                if (end > 2) {
                    try {
                        colour = Integer.parseInt(line.substring(2, end));
                    } catch (NumberFormatException ignored) {
                        // no colour: white
                    }
                    line = line.substring(end + 2);
                }
            }
            info.text(Component.literal(line), 0xFF000000 | colour);
        }
    }

    static void install() {
        if (!FMLEnvironment.dist.isClient() || !WFSystems.enabled("probe")) return;
        ProbeProviders.addBlocks(ResourceLocation.fromNamespaceAndPath(ContinuumBridge.ID, "look_overlay"),
                (BlockState state) -> state.getBlock() instanceof ILookOverlay,
                (info, ctx, state, pos, be) -> {
                    ILookOverlay.Data data = ((ILookOverlay) state.getBlock()).getLookOverlay(ctx.level(), pos, ctx.player());
                    if (data != null) fill(info, data);
                }, 0);
        ProbeProviders.addEntities(ResourceLocation.fromNamespaceAndPath(ContinuumBridge.ID, "entity_look_overlay"),
                entity -> entity instanceof IEntityLookOverlay,
                (info, ctx, entity) -> {
                    ILookOverlay.Data data = IEntityLookOverlay.of(entity, ctx.player());
                    if (data != null) fill(info, data);
                }, 0);
        com.hbm.config.ClientConfig.DODD_RBMK_DIAGNOSTIC = false;
        ContinuumBridge.LOG.info("Look-at: Continuum's overlays show in WFLib's probe; its own crosshair overlay is off");
    }
}
