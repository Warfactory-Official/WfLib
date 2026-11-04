package com.wf.wflib.armor.lab;

import com.wf.wflib.armor.ArmorCatalogue;
import com.wf.wflib.labs.api.LabContent;

public final class ArmorLabContent implements LabContent {
    @Override
    public String id() {
        return "wflib:armor";
    }

    @Override
    public boolean dev(String contentId) {
        return contentId.indexOf(':') > 0 && ArmorCatalogue.isDev(net.minecraft.resources.ResourceLocation.parse(contentId));
    }
}
