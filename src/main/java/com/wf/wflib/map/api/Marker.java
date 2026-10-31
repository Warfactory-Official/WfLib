package com.wf.wflib.map.api;

import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;

/** One shared map marker; {@code dimension} = level id. */
public record Marker(long id, ResourceLocation dimension, BlockPos pos, String label) {
    public static final int MAX_LABEL = 24;

    public void write(RegistryFriendlyByteBuf buf) {
        buf.writeVarLong(id);
        buf.writeResourceLocation(dimension);
        buf.writeBlockPos(pos);
        buf.writeUtf(label, MAX_LABEL);
    }

    public static Marker read(RegistryFriendlyByteBuf buf) {
        return new Marker(buf.readVarLong(), buf.readResourceLocation(), buf.readBlockPos(), buf.readUtf(MAX_LABEL));
    }
}
