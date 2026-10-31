package com.wf.wflib.core.api;

import java.util.List;

/** Implemented by {@code InventoryMenu} (mixin): groups placed in this menu instance. */
public interface SlotGroupHost {
    List<SlotLayout.Placed> wflib$placedGroups();
}
