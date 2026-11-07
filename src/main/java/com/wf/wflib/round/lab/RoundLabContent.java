package com.wf.wflib.round.lab;

import com.wf.wflib.labs.api.LabContent;
import com.wf.wflib.round.api.KineticPresets;
import net.minecraft.resources.ResourceLocation;

public final class RoundLabContent implements LabContent {
    @Override
    public String id() {
        return "wflib:round";
    }

    @Override
    public boolean dev(String contentId) {
        ResourceLocation id = ResourceLocation.tryParse(contentId);
        return id != null && KineticPresets.isDev(id);
    }
}
