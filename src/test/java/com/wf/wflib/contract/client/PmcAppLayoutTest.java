package com.wf.wflib.contract.client;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

import com.wf.wflib.pda.api.client.PdaShell;
import com.wf.wflib.ui.api.UiLayout;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

/** The PDA layout is built lazily on first draw, so a bad expression would crash the client; build it here instead. */
class PmcAppLayoutTest {
    @Test
    void theContractsAppBinds() {
        com.wf.wflib.pda.PdaShellOnce.ensure();
        PmcApp.register();
        UiLayout layout = new UiLayout(ResourceLocation.fromNamespaceAndPath("test", "pmc"), PmcApp.INSTANCE.build(), 0);
        assertDoesNotThrow(layout::instantiate);
    }
}
