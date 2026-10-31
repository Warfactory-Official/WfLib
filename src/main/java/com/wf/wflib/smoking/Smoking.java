package com.wf.wflib.smoking;

import com.norwood.ahf.hook.AhfHooks;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.neoforge.network.codec.NeoForgeStreamCodecs;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.NeoForgeRegistries;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3f;
import com.wf.wflib.smoking.api.event.CigaretteSmokedEvent;
import com.wf.wflib.core.api.WFLib;

/** One cigarette per tap: {@link Session} runs {@link SmokingTimeline#TICKS} unless the cigarette leaves its hand. */
public final class Smoking {
    /** {@code start} = level game time of the tap. */
    public record Session(InteractionHand hand, long start) {
        public static final StreamCodec<FriendlyByteBuf, Session> STREAM_CODEC = StreamCodec.composite(
                NeoForgeStreamCodecs.enumCodec(InteractionHand.class), Session::hand,
                ByteBufCodecs.VAR_LONG, Session::start, Session::new);
    }

    /** Absent => not smoking; never default-created. Registered by {@code SmokingSystem}. */
    public static final DeferredHolder<AttachmentType<?>, AttachmentType<Session>> SESSION = DeferredHolder.create(
            NeoForgeRegistries.Keys.ATTACHMENT_TYPES, ResourceLocation.fromNamespaceAndPath(WFLib.MODID, "smoking"));

    private Smoking() {
    }

    @Nullable
    public static Session session(LivingEntity e) {
        return e.getExistingDataOrNull(SESSION);
    }

    /** Both sides; the server's copy syncs over the client's. */
    static void start(Player p, InteractionHand hand) {
        p.setData(SESSION, new Session(hand, p.level().getGameTime()));
    }

    /** Ticks since the tap, clamped to the timeline; -1 => not smoking. */
    public static int tick(LivingEntity e) {
        Session s = session(e);
        return s == null ? -1 : (int) Math.clamp(e.level().getGameTime() - s.start(), 0, SmokingTimeline.TICKS - 1);
    }

    static void onTick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        Session s = session(player);
        if (s == null) {
            return;
        }
        ItemStack stack = player.getItemInHand(s.hand());
        if (!(stack.getItem() instanceof CigaretteItem)) {
            player.removeData(SESSION);
            return;
        }
        long tick = player.level().getGameTime() - s.start();
        if (tick >= SmokingTimeline.TICKS) {
            NeoForge.EVENT_BUS.post(new com.wf.wflib.smoking.api.event.CigaretteSmokedEvent(player, stack.copy()));
            if (!player.hasInfiniteMaterials()) {
                stack.shrink(1);
            }
            player.removeData(SESSION);
            return;
        }
        if (tick % 3 == 0 && SmokingTimeline.exhaling((int) tick)) {
            Vec3 look = player.getLookAngle();
            Vec3 mouth = player.getEyePosition().add(look.scale(0.3)).add(0.0, -0.1, 0.0);
            ((ServerLevel) player.level()).sendParticles(ParticleTypes.SMOKE, mouth.x, mouth.y, mouth.z, 3,
                    0.03, 0.02, 0.03, 0.01);
        }
    }

    static int targets(LivingEntity e, AhfHooks.HandFrame frame, long tick, Vector3f right, Vector3f left) {
        Session s = session(e);
        if (!(e instanceof Player p) || s == null) {
            return 0;
        }
        HumanoidArm arm = s.hand() == InteractionHand.MAIN_HAND ? p.getMainArm() : p.getMainArm().getOpposite();
        Vector3f out = arm == HumanoidArm.RIGHT ? right : left;
        SmokingTimeline.wrist(tick(p) / 20.0f, out);
        if (arm == HumanoidArm.LEFT) {
            out.x = -out.x;
        }
        return 1 << arm.ordinal();
    }
}
