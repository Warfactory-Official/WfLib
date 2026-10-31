package com.wf.wflib.ui.api.input;

import net.minecraft.world.item.ItemStack;

/** Item stack an {@code item} widget draws; {@code item} = the repeat item (null outside a repeat). */
@FunctionalInterface
public interface UiStack {
    ItemStack stack(Object item);
}
