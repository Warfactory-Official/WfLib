package com.wf.wflib.item.gametest;

import com.wf.wflib.WFLib;
import com.wf.wflib.item.ItemMasses;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.ArrayList;
import java.util.List;

@GameTestHolder(WFLib.MODID)
@PrefixGameTestTemplate(false)
public class ItemMassGameTest {

    @GameTest(template = "empty")
    public static void everyWflibItemHasAMass(GameTestHelper helper) {
        List<ResourceLocation> missing = new ArrayList<>();
        for (Item item : BuiltInRegistries.ITEM) {
            ResourceLocation id = BuiltInRegistries.ITEM.getKey(item);
            if (id.getNamespace().equals(WFLib.MODID) && !(ItemMasses.unit(item) > 0)) {
                missing.add(id);
            }
        }
        helper.assertTrue(missing.isEmpty(), "no mass: " + missing);
        helper.succeed();
    }
}
