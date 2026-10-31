package com.wf.wflib.pda.client;

import net.minecraft.client.renderer.BlockEntityWithoutLevelRenderer;
import net.neoforged.neoforge.client.extensions.common.IClientItemExtensions;
import com.wf.wflib.pda.api.client.PdaRenderer;

public final class PdaItemClient implements IClientItemExtensions {
    public static final PdaItemClient INSTANCE = new PdaItemClient();

    private PdaItemClient() {
    }

    @Override
    public BlockEntityWithoutLevelRenderer getCustomRenderer() {
        return PdaRenderer.itemRenderer();
    }
}
