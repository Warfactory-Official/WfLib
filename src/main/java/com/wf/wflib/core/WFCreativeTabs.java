package com.wf.wflib.core;

import com.wf.wflib.core.api.CreativeTabs;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import com.wf.wflib.missile.MissileSystem;
import com.wf.wflib.core.api.WFLib;

/** WFLib's creative tab: every item, block/machine and preset missile on one page. */
public final class WFCreativeTabs {

    public static final DeferredRegister<CreativeModeTab> TABS =
            DeferredRegister.create(Registries.CREATIVE_MODE_TAB, WFLib.MODID);

    public static final DeferredHolder<CreativeModeTab, CreativeModeTab> WFLIB = TABS.register("wflib", () ->
            CreativeModeTab.builder()
                    .title(Component.translatable("itemGroup.wflib"))
                    .icon(() -> new ItemStack(MissileSystem.MISSILE_DISPENSER_ITEM.get()))
                    .displayItems((params, output) -> CreativeTabs.WFLIB.values()
                            .forEach(contributor -> contributor.add(output)))
                    .build());

    private WFCreativeTabs() {
    }

    public static void register(IEventBus bus) {
        TABS.register(bus);
    }
}
