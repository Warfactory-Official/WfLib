package com.wf.wflib.soldier;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;

import static org.junit.jupiter.api.Assertions.*;

class VoicePackTest {
    @TempDir Path temp;

    private Path pack(String manifest, String extra) throws IOException {
        Path path = temp.resolve("pack.zip");
        try (var zip = new ZipOutputStream(Files.newOutputStream(path))) {
            entry(zip, "voices.json", manifest);
            entry(zip, "assets/test/sounds.json", "{\"voice/contact\":{\"sounds\":[\"test:voice/contact\"]}}");
            entry(zip, "assets/test/sounds/voice/contact.ogg", "OggSfixture");
            if (extra != null) entry(zip, extra, "bad");
        }
        return path;
    }

    private static void entry(ZipOutputStream zip, String name, String value) throws IOException {
        zip.putNextEntry(new ZipEntry(name)); zip.write(value.getBytes(java.nio.charset.StandardCharsets.UTF_8)); zip.closeEntry();
    }

    private static String manifest(String event, String sound, int ticks) {
        return "{\"format\":1,\"id\":\"test\",\"speakers\":{\"one\":{\"" + event
                + "\":[{\"sound\":\"" + sound + "\",\"ticks\":" + ticks + "}]}}}";
    }

    @Test void readsExternalProfileAndExportsOnlyAudioResources() throws Exception {
        var source = VoicePack.read(pack(manifest("contact", "test:voice/contact", 38), null));
        assertEquals(38, source.speakers().get("one").get("contact").getFirst().ticks());
        Path bundle = temp.resolve("generated/bundle.zip");
        SoldierVoices.buildBundle(Map.of(source.id(), source), bundle);
        try (var zip = new ZipFile(bundle.toFile())) {
            assertNotNull(zip.getEntry("pack.mcmeta"));
            assertNotNull(zip.getEntry("assets/test/sounds/voice/contact.ogg"));
            assertNull(zip.getEntry("voices.json"));
        }
        byte[] first = Files.readAllBytes(bundle);
        SoldierVoices.buildBundle(Map.of(source.id(), source), bundle);
        assertArrayEquals(first, Files.readAllBytes(bundle));
    }

    @Test void rejectsTraversalAndResourcesOutsideThePackNamespace() throws Exception {
        assertThrows(IOException.class, () -> VoicePack.read(pack(manifest("contact", "test:voice/contact", 38), "../outside")));
        assertThrows(IOException.class, () -> VoicePack.read(pack(manifest("contact", "test:voice/contact", 38), "assets/minecraft/sounds.json")));
    }

    @Test void rejectsMissingAudioUnknownEventsAndUnboundedDurations() throws Exception {
        assertThrows(IOException.class, () -> VoicePack.read(pack(manifest("contact", "test:missing", 38), null)));
        assertThrows(IOException.class, () -> VoicePack.read(pack(manifest("made_up", "test:voice/contact", 38), null)));
        assertThrows(IOException.class, () -> VoicePack.read(pack(manifest("contact", "test:voice/contact", 601), null)));
    }

    @Test void failedBundleBuildKeepsPreviousFile() throws Exception {
        var source = VoicePack.read(pack(manifest("contact", "test:voice/contact", 38), null));
        Path bundle = temp.resolve("bundle.zip");
        Files.writeString(bundle, "previous");
        Files.delete(source.file());
        assertThrows(IOException.class, () -> SoldierVoices.buildBundle(Map.of(source.id(), source), bundle));
        assertEquals("previous", Files.readString(bundle));
    }

    @Test void teamAssignmentsAreIndependentOfFriendliness() throws Exception {
        Path file = temp.resolve("teams.json");
        Files.writeString(file, "{\"friendly\":\"stalker_bandit\",\"enemy\":\"stalker_military\"}");
        assertEquals(Map.of("friendly", "stalker_bandit", "enemy", "stalker_military"), SoldierVoices.readTeams(file));
        Files.writeString(file, "{\"friendly\":\"../../escape\"}");
        assertThrows(IOException.class, () -> SoldierVoices.readTeams(file));
    }

    @Test void aTeamBindingMayPinOneSpeaker() throws Exception {
        Path file = temp.resolve("teams.json");
        Files.writeString(file, "{\"enemy\":\"trepang2_horizon/horizon_heavy\"}");
        assertEquals(Map.of("enemy", "trepang2_horizon/horizon_heavy"), SoldierVoices.readTeams(file));
        Files.writeString(file, "{\"enemy\":\"a/b/c\"}");
        assertThrows(IOException.class, () -> SoldierVoices.readTeams(file));
        assertEquals("pack", SoldierVoices.binding("pack", "auto"));
        assertEquals("pack", SoldierVoices.binding("pack", ""));
        assertEquals("pack/who", SoldierVoices.binding("pack", "who"));
        assertTrue(SoldierVoices.valid(""), "no voice is always allowed");
        assertFalse(SoldierVoices.valid("not_installed"));
        assertFalse(SoldierVoices.valid("../escape"));
    }

    @Test void menuSurvivesTheWire() {
        var menu = new VoicePackets.Menu(java.util.List.of(new VoicePackets.Pack("one", java.util.List.of("a", "b")),
                new VoicePackets.Pack("two", java.util.List.of())), "one/a", "");
        var buf = new net.minecraft.network.FriendlyByteBuf(io.netty.buffer.Unpooled.buffer());
        VoicePackets.Menu.CODEC.encode(buf, menu);
        assertEquals(menu, VoicePackets.Menu.CODEC.decode(buf));
    }

    @Test void deliveryAcceptsOnlyPublicHttpUrlsWithoutCredentials() {
        assertEquals("https://example.com/packs", VoiceDelivery.baseUrl("https://example.com/packs/"));
        for (String bad : new String[]{"file:///tmp/voices.zip", "http://user:secret@example.com", "http://example.com?q=secret", "not-a-url"})
            assertThrows(IllegalArgumentException.class, () -> VoiceDelivery.baseUrl(bad));
    }
}
