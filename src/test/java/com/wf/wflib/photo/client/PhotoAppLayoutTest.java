package com.wf.wflib.photo.client;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

import com.wf.wflib.pda.api.client.PdaShell;
import com.wf.wflib.ui.api.UiLayout;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

/** The PDA layout is built lazily on first draw, so a bad expression crashes the client; build it here instead. */
class PhotoAppLayoutTest {
    @Test
    void galleryLayoutBinds() {
        com.wf.wflib.pda.PdaShellOnce.ensure();
        PhotoApp.register();
        UiLayout layout = new UiLayout(ResourceLocation.fromNamespaceAndPath("test", "photos"),
                new PhotoApp().build(), 0);
        assertDoesNotThrow(layout::instantiate);
    }
}
