package com.wf.wflib.core;

import com.wf.wflib.core.api.WFSystems;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.network.ConfigurationTask;
import net.neoforged.neoforge.network.configuration.ICustomConfigurationTask;
import net.neoforged.neoforge.network.event.RegisterConfigurationTasksEvent;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.Consumer;
import com.wf.wflib.core.api.WFLib;

/** Configuration phase: server asks, client answers with its effective set, mismatch => disconnect. */
public final class SystemsNetwork {
    private static final ConfigurationTask.Type TASK = new ConfigurationTask.Type(
            ResourceLocation.fromNamespaceAndPath(WFLib.MODID, "systems").toString());

    private SystemsNetwork() {}

    public record SystemsPayload(List<String> ids) implements CustomPacketPayload {
        static final Type<SystemsPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(WFLib.MODID, "systems"));
        static final StreamCodec<net.minecraft.network.FriendlyByteBuf, SystemsPayload> CODEC = StreamCodec.composite(
                ByteBufCodecs.STRING_UTF8.apply(ByteBufCodecs.list()), SystemsPayload::ids, SystemsPayload::new);

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    private record Task() implements ICustomConfigurationTask {
        @Override
        public void run(Consumer<CustomPacketPayload> sender) {
            sender.accept(local());
        }

        @Override
        public Type type() {
            return TASK;
        }
    }

    public static void registerPayloads(RegisterPayloadHandlersEvent event) {
        event.registrar("core-1").configurationBidirectional(SystemsPayload.TYPE, SystemsPayload.CODEC,
                SystemsNetwork::handle);
    }

    public static void registerTasks(RegisterConfigurationTasksEvent event) {
        event.register(new Task());
    }

    private static SystemsPayload local() {
        return new SystemsPayload(new ArrayList<>(new TreeSet<>(WFSystems.effective())));
    }

    /** Symmetric difference of two effective sets ({@code <modid>:<sys>}). */
    public static Set<String> differing(Set<String> client, Set<String> server) {
        Set<String> differ = new TreeSet<>(client);
        differ.addAll(server);
        Set<String> common = new TreeSet<>(client);
        common.retainAll(server);
        differ.removeAll(common);
        return differ;
    }

    private static void handle(SystemsPayload payload, IPayloadContext ctx) {
        if (ctx.flow() == PacketFlow.CLIENTBOUND) {
            ctx.reply(local());
            return;
        }
        Set<String> client = new TreeSet<>(payload.ids());
        Set<String> server = new TreeSet<>(WFSystems.effective());
        Set<String> differ = differing(client, server);
        if (differ.isEmpty()) {
            ctx.finishCurrentTask(TASK);
        } else {
            ctx.disconnect(Component.literal("wflib systems differ between client and server: " + differ
                    + " (on server: " + server + ", on client: " + client + ")"));
        }
    }
}
