package com.wf.wflib.pda.client.theme;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import static org.junit.jupiter.api.Assertions.*;

class PdaSoundFilesTest {
    @TempDir Path directory;
    @Test void noManifestOrMissingAudioUsesFallbackWithoutCreatingFiles() throws Exception {
        assertTrue(PdaSoundFiles.read(directory).isEmpty());
        assertFalse(Files.exists(directory.resolve("sounds.properties")));
        Files.writeString(directory.resolve("sounds.properties"),"click=missing.ogg\nalert=wrong.wav\nunknown=x.ogg\n");
        assertTrue(PdaSoundFiles.read(directory).isEmpty());
    }
    @Test void resolvesPerEventLocalPathsAndIgnoresUnsupportedOrOversizedFiles() throws Exception {
        Files.write(directory.resolve("click.ogg"),new byte[]{1,2,3});
        Path external=Files.createTempFile(directory,"external",".ogg");
        Files.write(directory.resolve("big.ogg"),new byte[(int)PdaSoundFiles.MAX_BYTES+1]);
        Files.writeString(directory.resolve("sounds.properties"),"click=click.ogg\ntab="+external+"\nopen=big.ogg\nclose=bad.wav\nunknown=click.ogg\n");
        var loaded=PdaSoundFiles.read(directory);
        assertEquals(directory.resolve("click.ogg"),loaded.get("click"));
        assertEquals(external,loaded.get("tab"));assertEquals(2,loaded.size());
    }
}
