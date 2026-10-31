package com.wf.wflib.packs.gametest;

import com.wf.wflib.core.api.WFLib;
import com.wf.wflib.packs.PackHandshake;
import com.wf.wflib.packs.api.PackEntry;
import com.wf.wflib.packs.api.PackHost;
import com.wf.wflib.packs.api.PackHosts;
import com.wf.wflib.packs.api.PackSpec;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.ConnectionProtocol;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.network.ConfigurationTask;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import net.neoforged.neoforge.network.configuration.ICustomConfigurationTask;
import net.neoforged.neoforge.network.event.RegisterConfigurationTasksEvent;
import net.neoforged.neoforge.network.registration.NetworkRegistry;

import java.util.ArrayList;
import java.util.List;

/** Task and payload wiring; the client half (reply, pack finder) needs a real client. */
@PrefixGameTestTemplate(false)
public class PacksGameTest {
    static final PackSpec SPEC = PackSpec.builder(WFLib.MODID, "wflib test").build();

    private PacksGameTest() {
    }

    public static void register() {
        PackHosts.register(SPEC);
    }

    @GameTest(templateNamespace = WFLib.MODID, template = "empty")
    public static void handshakeIsWired(GameTestHelper h) {
        RegisterConfigurationTasksEvent e = new RegisterConfigurationTasksEvent(null);
        ModList.get().getModContainerById(WFLib.MODID).orElseThrow().getEventBus().post(e);
        List<ConfigurationTask> tasks = e.getConfigurationTasks().stream()
                .filter(t -> t.type().equals(PackHandshake.TASK)).toList();
        h.assertTrue(tasks.size() == 1, "pack task registered " + tasks.size() + "x");
        List<CustomPacketPayload> sent = new ArrayList<>();
        ((ICustomConfigurationTask) tasks.getFirst()).run(sent::add);
        h.assertTrue(sent.equals(List.of(PackHandshake.serverPacks())), "task sends the local lists: " + sent);
        h.assertTrue(PackHandshake.serverPacks().hosts().stream().anyMatch(p -> p.host().equals(WFLib.MODID)),
                "test host listed");
        h.assertTrue(NetworkRegistry.getCodec(PackHandshake.ServerPacks.TYPE.id(), ConnectionProtocol.CONFIGURATION,
                PacketFlow.CLIENTBOUND) != null, "server_packs payload registered");
        h.assertTrue(NetworkRegistry.getCodec(PackHandshake.ServerPacks.TYPE.id(), ConnectionProtocol.PLAY,
                PacketFlow.CLIENTBOUND) != null, "server_packs payload registered for play");
        h.assertTrue(NetworkRegistry.getCodec(PackHandshake.ClientPacks.TYPE.id(), ConnectionProtocol.CONFIGURATION,
                PacketFlow.SERVERBOUND) != null, "client_packs payload registered");
        h.succeed();
    }

    @GameTest(templateNamespace = WFLib.MODID, template = "empty")
    public static void payloadSurvivesTheWire(GameTestHelper h) {
        var original = new PackHandshake.ServerPacks(List.of(
                new PackHandshake.HostPacks("a", List.of(new PackEntry("p", "ff", "mod"), new PackEntry("q", "00", ""))),
                new PackHandshake.HostPacks("b", List.of())));
        ByteBuf buf = Unpooled.buffer();
        PackHandshake.ServerPacks.CODEC.encode(buf, original);
        h.assertTrue(PackHandshake.ServerPacks.CODEC.decode(buf).equals(original), "round trip");
        h.succeed();
    }

    @GameTest(templateNamespace = WFLib.MODID, template = "empty")
    public static void defaultReconcilerDiffsAgainstTheLocalList(GameTestHelper h) {
        PackHost host = PackHosts.get(WFLib.MODID);
        h.assertTrue(host.reconciler().get().reconcile(host.entries()) == null, "own list matches");
        String m = host.reconciler().get().reconcile(List.of(new PackEntry("ghost", "1", "")));
        h.assertTrue(m != null && m.startsWith("wflib test packs differ") && m.contains("Missing: ghost"), "diff " + m);
        h.succeed();
    }
}
