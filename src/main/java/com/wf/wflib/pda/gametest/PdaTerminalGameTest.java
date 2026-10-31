package com.wf.wflib.pda.gametest;

import com.wf.wflib.recon.api.ReconHubBlockEntity;
import com.wf.wflib.pda.terminal.PdaTerminal;
import com.wf.wflib.recon.api.ReconNet;
import com.wf.wflib.recon.api.GridNode;
import com.wf.wflib.recon.api.ReconGrid;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.UUID;
import com.wf.wflib.pda.api.PdaContent;
import com.wf.wflib.recon.api.ReconContent;
import com.wf.wflib.core.api.WFLib;

@PrefixGameTestTemplate(false)
public class PdaTerminalGameTest {
    private static final String TEMPLATE = "empty";

    @GameTest(templateNamespace = WFLib.MODID, template = TEMPLATE, timeoutTicks = 60)
    public static void renamesOwnNetNode(GameTestHelper helper) {
        BlockPos hub = helper.absolutePos(new BlockPos(1, 1, 1));
        helper.getLevel().setBlockAndUpdate(hub, ReconContent.RECON_HUB.get().defaultBlockState());
        FakePlayer player = PdaUplinkGameTest.player(helper, 0xB2, 1, hub.above());
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(PdaContent.PDA.get()));
        UUID team = new UUID(0x9DA0_0000L, 0xB2);
        ((ReconHubBlockEntity) helper.getLevel().getBlockEntity(hub)).adopt(team);
        long net = ReconNet.netId(team);
        helper.runAfterDelay(5, () -> {
            String renamed = PdaTerminal.run(player, "rename node hub ALPHA").getFirst();
            helper.runAfterDelay(2, () -> {
                ReconGrid grid = ReconNet.grid(helper.getLevel(), net);
                GridNode node = grid == null ? null : grid.node(hub);
                String listed = PdaTerminal.run(player, "nodes").getFirst();
                if (renamed.startsWith("err") || node == null || !node.label().equals("ALPHA")
                        || !listed.startsWith("#1 ALPHA")) {
                    helper.fail("renamed '" + renamed + "' label " + (node == null ? null : node.label())
                            + " listed '" + listed + "'");
                }
                helper.succeed();
            });
        });
    }
}
