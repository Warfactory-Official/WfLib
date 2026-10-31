package com.wf.wflib.map.api.client;

import net.minecraft.resources.ResourceLocation;

import java.util.List;

/** Toolbar toggle; state = {@link MapTool#armed()}. */
public record MapButton(String onLabel, String offLabel, ResourceLocation icon, List<String> tooltip) {
}
