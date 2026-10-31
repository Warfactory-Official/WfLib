package com.wf.wflib.ui.api.input;

import net.minecraft.network.chat.Component;

import java.util.List;

/** Lines of a hovered tooltip box; {@code item} = the hit's repeat item (null outside a repeat). Called on hover only. */
@FunctionalInterface
public interface UiTooltip {
    List<Component> lines(Object item);
}
