package com.wf.wflib.core.api;

/**
 * Creative tab contents, contributed by each system in {@code register}; an off system adds nothing. Descending
 * priority, ties in registration order.
 */
public final class CreativeTabs {

    @FunctionalInterface
    public interface Contributor {
        void add(net.minecraft.world.item.CreativeModeTab.Output out);
    }

    public static final OrderedRegistry<Contributor> WFLIB = OrderedRegistry.create("creative tab: wflib");

    private CreativeTabs() {
    }

    /** Registers {@code contributor} under {@code wflib:<system>}. */
    public static void contribute(String system, int priority, Contributor contributor) {
        WFLIB.register(net.minecraft.resources.ResourceLocation.fromNamespaceAndPath(WFLib.MODID, system), priority,
                contributor);
    }
}
