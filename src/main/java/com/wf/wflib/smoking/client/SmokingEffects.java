package com.wf.wflib.smoking.client;

import com.wf.gemrender.light.LightSink;
import com.wf.gemrender.light.Lights;
import com.wf.wflib.WFLib;
import com.wf.wflib.WFSounds;
import com.wf.wflib.smoking.CigaretteItem;
import com.wf.wflib.smoking.SmokingTimeline;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import org.joml.Vector3f;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** Sound + ember light for every player in use of a {@link CigaretteItem}. */
@EventBusSubscriber(modid = WFLib.MODID, value = Dist.CLIENT)
public final class SmokingEffects {
    static final int EMBER_RGB = 0xFF6A1A;
    static final float RANGE = 3F, IDLE = 2F, DRAG = 8F;
    /** Model space origin above the feet; {@link SmokingTimeline#wrist} frame. */
    static final double SHOULDER_HEIGHT = 1.5;

    private static final Map<UUID, Voice> VOICES = new HashMap<>();
    private static final Vector3f WRIST = new Vector3f();

    private SmokingEffects() {
    }

    public static void register() {
        Lights.register(SmokingEffects::collect);
    }

    private static boolean smoking(Player p) {
        return p.isUsingItem() && p.getUseItem().getItem() instanceof CigaretteItem;
    }

    private static int tick(Player p) {
        return SmokingTimeline.TICKS - p.getUseItemRemainingTicks();
    }

    private static final class Voice extends AbstractTickableSoundInstance {
        private final Player player;

        Voice(Player player) {
            super(WFSounds.CIGARETTE.get(), SoundSource.PLAYERS, player.getRandom());
            this.player = player;
            this.volume = 0.6F;
            update();
        }

        private void update() {
            x = player.getX();
            y = player.getEyeY();
            z = player.getZ();
        }

        @Override
        public void tick() {
            if (player.isRemoved() || !smoking(player)) {
                stop();
                return;
            }
            update();
        }
    }

    @SubscribeEvent
    static void onTick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) {
            VOICES.clear();
            return;
        }
        VOICES.values().removeIf(v -> v.isStopped());
        for (AbstractClientPlayer p : mc.level.players()) {
            if (smoking(p) && !VOICES.containsKey(p.getUUID())) {
                Voice v = new Voice(p);
                VOICES.put(p.getUUID(), v);
                mc.getSoundManager().play(v);
            }
        }
    }

    static float intensity(int tick, float flicker) {
        if (!SmokingTimeline.lit(tick)) {
            return 0.0F;
        }
        return (SmokingTimeline.dragging(tick) ? DRAG : IDLE) * flicker;
    }

    private static void collect(LightSink sink, float partialTick) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) {
            return;
        }
        for (AbstractClientPlayer p : mc.level.players()) {
            if (!smoking(p)) {
                continue;
            }
            float seconds = (tick(p) + partialTick) / 20.0F;
            float flicker = 0.85F + 0.15F * (float) Math.sin(seconds * 23.0);
            float k = intensity(tick(p), flicker);
            if (k <= 0.0F) {
                continue;
            }
            SmokingTimeline.wrist(seconds, WRIST);
            double yaw = Math.toRadians(Mth.rotLerp(partialTick, p.yBodyRotO, p.yBodyRot));
            double fx = -Math.sin(yaw), fz = Math.cos(yaw);
            double rx = -fz, rz = fx;
            sink.point(Mth.lerp(partialTick, p.xo, p.getX()) - WRIST.x * rx - WRIST.z * fx,
                    Mth.lerp(partialTick, p.yo, p.getY()) + SHOULDER_HEIGHT - WRIST.y,
                    Mth.lerp(partialTick, p.zo, p.getZ()) - WRIST.x * rz - WRIST.z * fz,
                    RANGE, EMBER_RGB, k, false);
        }
    }
}
