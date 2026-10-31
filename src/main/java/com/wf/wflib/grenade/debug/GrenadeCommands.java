package com.wf.wflib.grenade.debug;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.wf.wflib.grenade.api.ChargeSpec;
import com.wf.wflib.grenade.api.GrenadeWarheads;
import com.wf.wflib.grenade.api.ThrownCharge;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

import java.util.Arrays;

/** {@code /wflib smoke [dye]}. */
public final class GrenadeCommands {
    private GrenadeCommands() {
    }

    public static void onRegisterCommands(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("wflib")
                .requires(src -> src.hasPermission(2))
                .then(Commands.literal("smoke")
                        .executes(ctx -> spawnSmoke(ctx.getSource(), DyeColor.WHITE))
                        .then(Commands.argument("dye", StringArgumentType.word())
                                .suggests((ctx, b) -> SharedSuggestionProvider.suggest(
                                        Arrays.stream(DyeColor.values()).map(DyeColor::getName), b))
                                .executes(ctx -> spawnSmoke(ctx.getSource(),
                                        DyeColor.byName(StringArgumentType.getString(ctx, "dye"), DyeColor.WHITE))))));
    }

    /** Smoke grenade reaction at the source position, no thrown body. */
    private static int spawnSmoke(CommandSourceStack src, DyeColor dye) {
        Vec3 pos = src.getPosition();
        ThrownCharge.react(src.getLevel(), pos, ItemStack.EMPTY,
                new ChargeSpec(GrenadeWarheads.smoke(dye), 0, 0.2f, false, false, 0, 0, 0), new Vec3(0, 1, 0),
                src.getEntity(), null, false);
        src.sendSuccess(() -> Component.literal("Smoke " + dye.getName() + " at " + BlockPos.containing(pos)), true);
        return 1;
    }
}
