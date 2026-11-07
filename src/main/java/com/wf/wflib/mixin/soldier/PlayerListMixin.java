package com.wf.wflib.mixin.soldier;

import com.wf.wflib.soldier.api.Soldiers;
import net.minecraft.network.chat.Component;
import net.minecraft.server.players.PlayerList;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(PlayerList.class)
abstract class PlayerListMixin {
    /** "Ally_3 joined the game" and soldiers' death messages are not chat. */
    @Inject(method = "broadcastSystemMessage(Lnet/minecraft/network/chat/Component;Z)V", at = @At("HEAD"), cancellable = true)
    private void wflib$quietSoldiers(Component message, boolean overlay, CallbackInfo ci) {
        if (Soldiers.quiet()) ci.cancel();
    }
}
