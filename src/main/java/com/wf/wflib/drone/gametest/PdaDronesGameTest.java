package com.wf.wflib.drone.gametest;

import com.wf.wflib.pda.gametest.PdaUplinkGameTest;
import com.wf.wflib.pda.terminal.PdaTerminal;
import com.wf.wflib.drone.api.DroneEntity;
import com.wf.wflib.cam.api.CameraChannels;
import com.wf.wflib.cam.api.FeedTarget;
import com.wf.wflib.drone.api.DroneStatus;
import com.wf.wflib.drone.api.PdaDrones;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.List;
import java.util.UUID;
import com.wf.wflib.pda.api.PdaContent;
import com.wf.wflib.cam.api.CameraContent;
import com.wf.wflib.core.api.WFLib;

@PrefixGameTestTemplate(false)
public class PdaDronesGameTest {
    private static final String TEMPLATE = "empty";

    @GameTest(templateNamespace = WFLib.MODID, template = TEMPLATE)
    public static void statusListsLinkedDronesOnly(GameTestHelper helper) {
        BlockPos at = helper.absolutePos(new BlockPos(1, 2, 1));
        DroneEntity drone = new DroneEntity(helper.getLevel(), Vec3.atCenterOf(at.above(3)));
        helper.getLevel().addFreshEntity(drone);
        FakePlayer player = PdaUplinkGameTest.player(helper, 9, 1, at);
        ItemStack pda = new ItemStack(PdaContent.PDA.get());
        pda.set(CameraContent.CHANNELS.get(), new CameraChannels(List.of(
                FeedTarget.ofDrone(drone.getUUID()),
                FeedTarget.ofCamera(helper.getLevel(), at),
                FeedTarget.ofDrone(new UUID(0xD20E, 1))), 0));
        player.setItemInHand(InteractionHand.MAIN_HAND, pda);

        List<DroneStatus.Entry> drones = PdaDrones.status(player).drones();
        drone.discard();
        if (drones.size() != 2 || !drones.get(0).loaded() || drones.get(0).status().isEmpty()
                || drones.get(1).loaded()) {
            helper.fail("drones " + drones);
        }
        helper.succeed();
    }

    /** Exchange (classified) => no feed id, no connect, status NO LINK. */
    @GameTest(templateNamespace = WFLib.MODID, template = TEMPLATE)
    public static void classifiedDroneShowsNoLink(GameTestHelper helper) {
        BlockPos at = helper.absolutePos(new BlockPos(1, 2, 1));
        DroneEntity drone = new DroneEntity(helper.getLevel(), Vec3.atCenterOf(at.above(3)));
        drone.orders().setExchange(null, null, true, false, List.of(), List.of());
        helper.getLevel().addFreshEntity(drone);
        FakePlayer player = PdaUplinkGameTest.player(helper, 9, 1, at);
        ItemStack pda = new ItemStack(PdaContent.PDA.get());
        pda.set(CameraContent.CHANNELS.get(), new CameraChannels(List.of(FeedTarget.ofDrone(drone.getUUID())), 0));
        player.setItemInHand(InteractionHand.MAIN_HAND, pda);
        DroneStatus.Entry entry = PdaDrones.status(player).drones().get(0);
        drone.discard();
        helper.assertTrue(entry.status().equals("NO LINK") && entry.feedId() == 0 && !entry.refusal().isEmpty(),
                "classified drone exposed: " + entry);
        helper.succeed();
    }

    @GameTest(templateNamespace = WFLib.MODID, template = TEMPLATE)
    public static void renamesLinkedDrone(GameTestHelper helper) {
        BlockPos at = helper.absolutePos(new BlockPos(1, 2, 1));
        DroneEntity drone = new DroneEntity(helper.getLevel(), Vec3.atCenterOf(at.above(3)));
        helper.getLevel().addFreshEntity(drone);
        FakePlayer player = PdaUplinkGameTest.player(helper, 0xB1, 1, at);
        ItemStack pda = new ItemStack(PdaContent.PDA.get());
        pda.set(CameraContent.CHANNELS.get(), new CameraChannels(List.of(FeedTarget.ofDrone(drone.getUUID())), 0));
        player.setItemInHand(InteractionHand.MAIN_HAND, pda);

        String bad = PdaTerminal.run(player, "rename drone 1 a!b").getFirst();
        String ok = PdaTerminal.run(player, "rename drone #1 HAWK").getFirst();
        String listed = PdaTerminal.run(player, "drones").getFirst();
        String label = drone.datalink().label();
        drone.discard();
        if (!bad.startsWith("err") || ok.startsWith("err") || !listed.startsWith("#1 HAWK") || !label.equals("HAWK")) {
            helper.fail("bad '" + bad + "' ok '" + ok + "' listed '" + listed + "' label " + label);
        }
        helper.succeed();
    }
}
