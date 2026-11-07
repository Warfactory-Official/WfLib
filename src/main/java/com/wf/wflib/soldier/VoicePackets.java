package com.wf.wflib.soldier;

import com.wf.wflib.core.api.WFLib;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Wire format of the voice menu. Optional channel: a client without WFLib's menu still joins, it just has no screen.
 * Changing a voice needs operator level 2, like the commands.
 */
public final class VoicePackets {
    private VoicePackets() {}

    static void register(PayloadRegistrar registrar) {
        registrar.playToClient(Menu.TYPE, Menu.CODEC, Menu::handle);
        registrar.playToServer(Set.TYPE, Set.CODEC, Set::handle);
        registrar.playToServer(Sample.TYPE, Sample.CODEC, Sample::handle);
    }

    /** Sends the menu state; false when the client lacks the channel. */
    static int open(ServerPlayer p) {
        if (!p.connection.hasChannel(Menu.TYPE)) {
            p.sendSystemMessage(Component.literal("This client has no voice menu; use /soldiers voices allies|enemies <pack> [speaker]."));
            return 0;
        }
        PacketDistributor.sendToPlayer(p, Menu.current());
        return 1;
    }

    public record Pack(String id, List<String> speakers) {}

    /** Packs and speakers installed on the server, and what allies / enemies currently use ("" = silent). */
    public record Menu(List<Pack> packs, String allies, String enemies) implements CustomPacketPayload {
        public static final Type<Menu> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(WFLib.MODID, "voice_menu"));
        static final StreamCodec<FriendlyByteBuf, Menu> CODEC = CustomPacketPayload.codec(Menu::write, Menu::read);

        static Menu current() {
            List<Pack> packs = new ArrayList<>();
            SoldierVoices.catalog().forEach((id, speakers) -> packs.add(new Pack(id, speakers)));
            Map<String, String> teams = SoldierVoices.teams();
            return new Menu(packs, teams.getOrDefault(VoiceCommands.ALLIES, ""), teams.getOrDefault(VoiceCommands.ENEMIES, ""));
        }

        private static Menu read(FriendlyByteBuf buf) {
            List<Pack> packs = buf.readList(b -> new Pack(b.readUtf(64), b.readList(s -> s.readUtf(64))));
            return new Menu(packs, buf.readUtf(129), buf.readUtf(129));
        }

        private void write(FriendlyByteBuf buf) {
            buf.writeCollection(packs, (b, p) -> {
                b.writeUtf(p.id(), 64);
                b.writeCollection(p.speakers(), (s, name) -> s.writeUtf(name, 64));
            });
            buf.writeUtf(allies, 129);
            buf.writeUtf(enemies, 129);
        }

        @Override
        public Type<Menu> type() { return TYPE; }

        static void handle(Menu packet, IPayloadContext context) {
            context.enqueueWork(() -> com.wf.wflib.soldier.client.VoiceScreen.show(packet));
        }
    }

    /** Menu choice. {@code team} is "allies" or "enemies"; {@code binding} is a pack, {@code pack/speaker}, or "". */
    public record Set(boolean allies, String binding) implements CustomPacketPayload {
        public static final Type<Set> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(WFLib.MODID, "voice_set"));
        static final StreamCodec<FriendlyByteBuf, Set> CODEC = CustomPacketPayload.codec(
                (p, buf) -> { buf.writeBoolean(p.allies); buf.writeUtf(p.binding, 129); },
                buf -> new Set(buf.readBoolean(), buf.readUtf(129)));

        @Override
        public Type<Set> type() { return TYPE; }

        static void handle(Set packet, IPayloadContext context) {
            context.enqueueWork(() -> {
                if (!(context.player() instanceof ServerPlayer p) || !p.hasPermissions(2)) return;
                try {
                    SoldierVoices.team(packet.allies ? VoiceCommands.ALLIES : VoiceCommands.ENEMIES, packet.binding);
                } catch (IOException e) {
                    p.sendSystemMessage(Component.literal(e.getMessage()));
                    PacketDistributor.sendToPlayer(p, Menu.current()); // put the screen back in step
                }
            });
        }
    }

    /** Plays one sample line of a voice next to the sender. */
    public record Sample(String binding) implements CustomPacketPayload {
        public static final Type<Sample> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(WFLib.MODID, "voice_sample"));
        static final StreamCodec<FriendlyByteBuf, Sample> CODEC = CustomPacketPayload.codec(
                (p, buf) -> buf.writeUtf(p.binding, 129), buf -> new Sample(buf.readUtf(129)));

        @Override
        public Type<Sample> type() { return TYPE; }

        static void handle(Sample packet, IPayloadContext context) {
            context.enqueueWork(() -> {
                if (context.player() instanceof ServerPlayer p && p.hasPermissions(2) && !SoldierVoices.sample(p, packet.binding)) {
                    p.sendSystemMessage(Component.literal("No sample line for that voice."));
                }
            });
        }
    }
}
