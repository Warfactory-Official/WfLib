package com.wf.wflib.pda.gametest;

import com.wf.wflib.pda.api.Pda;
import com.wf.wflib.pda.terminal.PdaTerminal;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import com.wf.wflib.pda.api.PdaContent;
import com.wf.wflib.core.api.WFLib;

/** Server-side access must follow the carried PDA without moving either held item. */
@PrefixGameTestTemplate(false)
public class PdaCarriedGameTest {
    @GameTest(templateNamespace = WFLib.MODID, template = "empty")
    public static void terminalUsesCarriedPdaWithoutMovingHands(GameTestHelper helper) {
        var player = PdaUplinkGameTest.player(helper, 0xCA, 1, helper.absolutePos(new BlockPos(1, 2, 1)));
        player.getInventory().clearContent();
        player.getInventory().selected = 0;
        ItemStack sword = new ItemStack(Items.IRON_SWORD);
        ItemStack shield = new ItemStack(Items.SHIELD);
        player.setItemInHand(InteractionHand.MAIN_HAND, sword);
        player.setItemInHand(InteractionHand.OFF_HAND, shield);
        ItemStack pda = new ItemStack(PdaContent.PDA.get());
        for (int slot : new int[]{8, 17, 35}) {
            player.getInventory().setItem(slot, pda);
            helper.assertTrue(Pda.carries(player) && !Pda.held(player), "carried but not physically held");
            helper.assertTrue(Pda.carriedStack(player) == pda, "uses actual source stack");
            helper.assertTrue(!PdaTerminal.run(player, "help").getFirst().startsWith("err:"), "terminal carried access");
            helper.assertTrue(player.getMainHandItem() == sword && player.getOffhandItem() == shield,
                    "held items unchanged");
            player.getInventory().setItem(slot, ItemStack.EMPTY);
        }
        player.setItemInHand(InteractionHand.OFF_HAND, pda);
        helper.assertTrue(Pda.carries(player) && Pda.held(player) && Pda.carriedStack(player) == pda,
                "offhand carried and held");
        player.setItemInHand(InteractionHand.OFF_HAND, shield);
        helper.assertTrue(!Pda.carries(player) && PdaTerminal.run(player, "help").getFirst().startsWith("err:"),
                "server rejects missing PDA");
        player.getInventory().clearContent();
        helper.succeed();
    }
}
