package com.wf.wflib.packs;

import com.wf.wflib.packs.api.PackEntry;
import com.wf.wflib.packs.api.PackHost;
import com.wf.wflib.packs.api.PackHosts;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.ConnectionProtocol;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.network.ConfigurationTask;
import net.neoforged.neoforge.network.configuration.ICustomConfigurationTask;
import net.neoforged.neoforge.network.event.RegisterConfigurationTasksEvent;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.BiFunction;
import java.util.stream.Collectors;

/**
 * Configuration task: server sends every host's {@code (packId, sha256)} list, client reconciles each through its
 * host's reconciler and answers with its own, server compares. Registered after the systems compare and runs after
 * NeoForge registry sync => a missing pack whose items the server has disconnects there first. Play phase: same list
 * after a content reload; client mismatch => client disconnects.
 */
public final class PackHandshake {
    public static final ConfigurationTask.Type TASK = new ConfigurationTask.Type("wflib:pack_check");

    private PackHandshake() {
    }

    public record HostPacks(String host, List<PackEntry> packs) {
        static final StreamCodec<ByteBuf, PackEntry> ENTRY = StreamCodec.composite(ByteBufCodecs.STRING_UTF8,
                PackEntry::id, ByteBufCodecs.STRING_UTF8, PackEntry::sha256, ByteBufCodecs.STRING_UTF8, PackEntry::mod,
                PackEntry::new);
        static final StreamCodec<ByteBuf, HostPacks> CODEC = StreamCodec.composite(ByteBufCodecs.STRING_UTF8,
                HostPacks::host, ENTRY.apply(ByteBufCodecs.list()), HostPacks::packs, HostPacks::new);
    }

    public record ServerPacks(List<HostPacks> hosts) implements CustomPacketPayload {
        public static final Type<ServerPacks> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath("wflib", "server_packs"));
        public static final StreamCodec<ByteBuf, ServerPacks> CODEC = HostPacks.CODEC.apply(ByteBufCodecs.list())
                .map(ServerPacks::new, ServerPacks::hosts);

        @Override
        public Type<ServerPacks> type() {
            return TYPE;
        }
    }

    public record ClientPacks(List<HostPacks> hosts) implements CustomPacketPayload {
        public static final Type<ClientPacks> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath("wflib", "client_packs"));
        static final StreamCodec<ByteBuf, ClientPacks> CODEC = HostPacks.CODEC.apply(ByteBufCodecs.list())
                .map(ClientPacks::new, ClientPacks::hosts);

        @Override
        public Type<ClientPacks> type() {
            return TYPE;
        }
    }

    record Task(ServerPacks packs) implements ICustomConfigurationTask {
        @Override
        public void run(Consumer<CustomPacketPayload> sender) {
            sender.accept(packs);
        }

        @Override
        public Type type() {
            return TASK;
        }
    }

    public static ServerPacks serverPacks() {
        return new ServerPacks(local());
    }

    static List<HostPacks> local() {
        return PackHosts.all().stream().map(h -> new HostPacks(h.spec().modId(), h.entries())).toList();
    }

    static void registerPayloads(RegisterPayloadHandlersEvent event) {
        event.registrar("packs-1")
                .commonToClient(ServerPacks.TYPE, ServerPacks.CODEC, PackHandshake::onServerPacks)
                .configurationToServer(ClientPacks.TYPE, ClientPacks.CODEC, PackHandshake::onClientPacks);
    }

    static void registerTasks(RegisterConfigurationTasksEvent event) {
        event.register(new Task(serverPacks()));
    }

    /** Client: reconciles local content first. */
    static void onServerPacks(ServerPacks p, IPayloadContext ctx) {
        String problem = compare(p.hosts(), (host, list) -> host.reconciler().get().reconcile(list));
        if (ctx.protocol() == ConnectionProtocol.CONFIGURATION) {
            ctx.reply(new ClientPacks(local()));
        } else if (problem != null) {
            ctx.disconnect(Component.literal(problem));
        }
    }

    static void onClientPacks(ClientPacks p, IPayloadContext ctx) {
        String problem = compare(p.hosts(),
                (host, list) -> PackHost.mismatch(host.spec(), host.entries(), list));
        if (problem != null) {
            ctx.disconnect(Component.literal(problem));
        } else {
            ctx.finishCurrentTask(TASK);
        }
    }

    /** Per local host, the remote list (empty if the remote lacks the host) through {@code check}; null => all match. */
    @Nullable
    static String compare(List<HostPacks> remote, BiFunction<PackHost, List<PackEntry>, String> check) {
        Map<String, List<PackEntry>> byHost = remote.stream().collect(Collectors.toMap(HostPacks::host,
                HostPacks::packs, (a, b) -> b));
        List<String> problems = new ArrayList<>();
        for (PackHost host : PackHosts.all()) {
            String problem = check.apply(host, byHost.getOrDefault(host.spec().modId(), List.of()));
            if (problem != null) {
                problems.add(problem);
            }
            byHost.remove(host.spec().modId());
        }
        byHost.forEach((id, list) -> {
            if (!list.isEmpty()) {
                problems.add("Packs of mod " + id + " exist on only one side.");
            }
        });
        return problems.isEmpty() ? null : String.join("\n\n", problems);
    }
}
