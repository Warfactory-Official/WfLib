package com.wf.wflib.explosion.client;

import com.wf.wflib.explosion.AuxParticlePacket;
import com.wf.wflib.explosion.ExplosionBlockFXPacket;
import com.wf.wflib.explosion.ExplosionKnockbackPacket;
import com.wf.wflib.explosion.ExplosionSystem;
import com.wf.wflib.explosion.standard.ExplosionEffectStandard;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.entity.NoopRenderer;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;

public final class ExplosionClient {
    private ExplosionClient() {
    }

    public static void register(IEventBus modBus) {
        modBus.addListener(EntityRenderersEvent.RegisterRenderers.class, event -> {
            event.registerEntityRenderer(ExplosionSystem.NUKE_EXPLOSION.get(), NoopRenderer::new);
            event.registerEntityRenderer(ExplosionSystem.NUKE_TOREX.get(), NoopRenderer::new);
        });
    }

    public static void handleKnockback(ExplosionKnockbackPacket pkt) {
        LocalPlayer player = Minecraft.getInstance().player;
        if (player != null) {
            player.setDeltaMovement(player.getDeltaMovement().add(pkt.x(), pkt.y(), pkt.z()));
            player.hurtMarked = true; // hurtMarked => client resyncs velocity to server
        }
    }

    public static void handleBlockFX(ExplosionBlockFXPacket pkt) {
        ClientLevel level = Minecraft.getInstance().level;
        if (level != null) {
            ExplosionEffectStandard.performClient(level, pkt.x(), pkt.y(), pkt.z(), pkt.size(), pkt.blocks());
        }
    }

    public static void handleAuxParticle(AuxParticlePacket pkt) {
        ClientLevel level = Minecraft.getInstance().level;
        if (level != null) {
            WFEffects.dispatch(pkt.effect(), level, pkt.x(), pkt.y(), pkt.z(), pkt.data());
        }
    }
}
