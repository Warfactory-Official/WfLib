package com.wf.wflib.pda.api;

import com.wf.wflib.core.api.OrderedRegistry;
import net.minecraft.server.level.ServerPlayer;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/** PDA Terminal noun: {@code <plural>} lists, {@code rename <singular> <#n|name> <new|->} renames. */
public interface TerminalNoun {
    OrderedRegistry<TerminalNoun> NOUNS = OrderedRegistry.create("pda.terminal_nouns");

    String plural();

    String singular();

    /** Reply to an empty listing. */
    String none();

    /** Listing order; null = no uplink. */
    @Nullable
    List<? extends Entry> entries(ServerPlayer player);

    interface Entry {
        String name();

        /** Listing line after {@code #n}. */
        String line(ServerPlayer viewer);

        /** @param name null = reset; @return reply line */
        String rename(ServerPlayer player, @Nullable String name);
    }
}
