package com.wf.wflib.ui;

import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.logging.LogUtils;
import com.wf.wflib.core.api.WFLib;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.client.event.RegisterShadersEvent;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;

/** {@code wflib:skin_damage}; null after a failed compile => {@code SkinPart} plain-blit fallback. */
public final class SkinShader {
    private static final Logger LOG = LogUtils.getLogger();
    @Nullable
    private static ShaderInstance shader;

    private SkinShader() {
    }

    public static void register(IEventBus modBus) {
        modBus.addListener(RegisterShadersEvent.class, e -> {
            try {
                e.registerShader(new ShaderInstance(e.getResourceProvider(),
                        ResourceLocation.fromNamespaceAndPath(WFLib.MODID, "skin_damage"),
                        DefaultVertexFormat.POSITION_TEX), s -> shader = s);
            } catch (Exception x) {
                shader = null;
                LOG.warn("skin_damage shader unavailable; skin parts use a plain blit", x);
            }
        });
    }

    @Nullable
    public static ShaderInstance get() {
        return shader;
    }
}
