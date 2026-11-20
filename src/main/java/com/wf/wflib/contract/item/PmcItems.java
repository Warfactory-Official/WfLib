package com.wf.wflib.contract.item;

import net.minecraft.world.item.Item;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

/** The items contracts hand out. */
public final class PmcItems {
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(com.wf.wflib.core.api.WFLib.MODID);
    /** What a Data Recovery contract wants carried out of the site. */
    public static final DeferredItem<Item> DATA_DRIVE = ITEMS.registerSimpleItem("data_drive", new Item.Properties().stacksTo(16));
    /** What a Supply Cache contract wants carried out. */
    public static final DeferredItem<Item> CACHE_MANIFEST = ITEMS.registerSimpleItem("cache_manifest", new Item.Properties().stacksTo(16));

    /** Recovered data to analyse with {@code /contract analyze}. */
    public static final DeferredItem<Item> INTEL_DRIVE = ITEMS.registerSimpleItem("intel_drive", new Item.Properties().stacksTo(16));

    /** Evidence to recover from a site: a company laptop. */
    public static final DeferredItem<Item> LAPTOP = ITEMS.registerSimpleItem("laptop", new Item.Properties().stacksTo(1));
    /** A wrapped brick of contraband: what a narcotics run carries. */
    public static final DeferredItem<Item> NARCOTICS_BRICK = ITEMS.registerSimpleItem("narcotics_brick", new Item.Properties().stacksTo(16));

    private PmcItems() {
    }
}
