package com.wf.wflib.core.client;

import com.wf.wflib.round.client.RoundDebug;
import net.minecraft.client.Minecraft;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterClientCommandsEvent;
import com.wf.wflib.core.api.WFLib;

@EventBusSubscriber(modid = WFLib.MODID, value = Dist.CLIENT)
// TODO(C-sweep): each owner registers its own client commands in registerClient; no piece owns this yet.
public final class WFClientCommands {

    private WFClientCommands() {
    }

    @SubscribeEvent
    public static void onRegisterClientCommands(RegisterClientCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("wfmodels").executes(ctx -> {
            Minecraft.getInstance().execute(() -> Minecraft.getInstance().setScreen(new ModelGalleryScreen()));
            return 1;
        }));
        event.getDispatcher().register(com.wf.wflib.anomaly.client.AnomalyDebug.command());
        event.getDispatcher().register(Commands.literal("wfdebug").then(Commands.literal("rounds")
                .executes(ctx -> rounds(ctx.getSource(), !RoundDebug.enabled()))
                .then(Commands.literal("on").executes(ctx -> rounds(ctx.getSource(), true)))
                .then(Commands.literal("off").executes(ctx -> rounds(ctx.getSource(), false)))
                .then(Commands.literal("status").executes(ctx -> {
                    ctx.getSource().sendSuccess(() -> Component.literal(RoundDebug.status()), false);
                    return 1;
                }))));
    }

    private static int rounds(CommandSourceStack source, boolean on) {
        RoundDebug.setEnabled(on);
        source.sendSuccess(() -> Component.literal("Round debug overlay " + (on ? "on" : "off")), false);
        return 1;
    }
}
