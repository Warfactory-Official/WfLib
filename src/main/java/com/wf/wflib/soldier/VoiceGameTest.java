package com.wf.wflib.soldier;

import com.wf.wflib.core.api.WFLib;
import com.wf.wflib.soldier.api.SoldierMedical;
import com.wf.wflib.soldier.api.SoldierSpec;
import com.wf.wflib.soldier.api.Soldiers;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/** Server-side emission only; converter tests exercise real Vorbis encoding, client playback needs a client. */
@PrefixGameTestTemplate(false)
public class VoiceGameTest {
    @GameTest(templateNamespace = WFLib.MODID, template = "empty")
    public static void externalPackEmitsOnceAndUnconsciousSoldiersStaySilent(GameTestHelper h) throws Exception {
        String id = "voice_test_" + java.util.UUID.randomUUID().toString().substring(0, 8);
        var directory = SoldierVoices.root().resolve("packs");
        Files.createDirectories(directory);
        var file = Files.createTempFile(directory, "voice-test-", ".zip");
        net.minecraft.server.level.ServerPlayer player = null;
        try {
            try (var zip = new ZipOutputStream(Files.newOutputStream(file))) {
                entry(zip, "voices.json", "{\"format\":1,\"id\":\"" + id
                        + "\",\"speakers\":{\"one\":{\"contact\":[{\"sound\":\"" + id + ":contact\",\"ticks\":20}]}}}");
                entry(zip, "assets/" + id + "/sounds.json", "{\"contact\":{\"sounds\":[\"" + id + ":contact\"]}}");
                entry(zip, "assets/" + id + "/sounds/contact.ogg", "OggSserver-test-fixture");
            }
            SoldierVoices.reload();
            var spec = new SoldierSpec("voice_test", "VoiceTest", false, null, List.of(), Map.of(), 0);
            player = Soldiers.spawn(h.getLevel(), h.absoluteVec(new net.minecraft.world.phys.Vec3(1, 2, 1)), 0, spec, null);
            h.assertTrue(player != null, "could not spawn test soldier");
            var bot = SoldierAi.botOf(player.getGameProfile().getName());
            bot.voice = id;
            h.assertTrue(SoldierVoices.say(player, bot, "contact"), "external callout did not emit");
            h.assertTrue(!SoldierVoices.say(player, bot, "contact"), "repeated callout bypassed cooldown");
            h.assertTrue(bot.callouts == 1 && bot.lastCallout.equals("contact"), "callout telemetry missing");
            var downed = new SoldierAi.Bot("VoiceDown");
            downed.voice = id;
            downed.health = new SoldierMedical.Condition(true, true, true, false, 0.6, 0, 0);
            SoldierVoices.clear();
            h.assertTrue(!SoldierVoices.say(player, downed, "contact"), "unconscious soldier spoke");
            downed.health = SoldierMedical.Condition.HEALTHY;
            h.assertTrue(SoldierVoices.say(player, downed, "contact"), "conscious control was also silent");
            h.succeed();
        } finally {
            if (player != null) Soldiers.remove(player);
            Files.deleteIfExists(file);
            SoldierVoices.reload();
        }
    }

    @GameTest(templateNamespace = WFLib.MODID, template = "empty")
    public static void aBindingPinsTheSpeakerAndRejectsUnknownOnes(GameTestHelper h) throws Exception {
        String id = "voice_pin_" + java.util.UUID.randomUUID().toString().substring(0, 8);
        var directory = SoldierVoices.root().resolve("packs");
        Files.createDirectories(directory);
        var file = Files.createTempFile(directory, "voice-pin-", ".zip");
        net.minecraft.server.level.ServerPlayer player = null;
        try {
            try (var zip = new ZipOutputStream(Files.newOutputStream(file))) {
                entry(zip, "voices.json", "{\"format\":1,\"id\":\"" + id + "\",\"speakers\":{"
                        + "\"one\":{\"contact\":[{\"sound\":\"" + id + ":one\",\"ticks\":20}]},"
                        + "\"two\":{\"attack\":[{\"sound\":\"" + id + ":two\",\"ticks\":20}]}}}");
                entry(zip, "assets/" + id + "/sounds.json", "{\"one\":{\"sounds\":[\"" + id + ":one\"]},\"two\":{\"sounds\":[\"" + id + ":two\"]}}");
                entry(zip, "assets/" + id + "/sounds/one.ogg", "OggSfixture-one");
                entry(zip, "assets/" + id + "/sounds/two.ogg", "OggSfixture-two");
            }
            SoldierVoices.reload();
            h.assertTrue(SoldierVoices.valid(id) && SoldierVoices.valid(id + "/two") && SoldierVoices.valid(""), "valid bindings refused");
            h.assertTrue(!SoldierVoices.valid(id + "/three") && !SoldierVoices.valid(id + "/two/extra"), "bad binding accepted");
            h.assertTrue(SoldierVoices.catalog().get(id).equals(List.of("one", "two")), "catalog " + SoldierVoices.catalog().get(id));
            var spec = new SoldierSpec("voice_pin", "VoicePin", false, null, List.of(), Map.of(), 0);
            player = Soldiers.spawn(h.getLevel(), h.absoluteVec(new net.minecraft.world.phys.Vec3(1, 2, 1)), 0, spec, null);
            h.assertTrue(player != null, "could not spawn test soldier");
            var bot = SoldierAi.botOf(player.getGameProfile().getName());
            bot.voice = id + "/two";
            h.assertTrue(!SoldierVoices.say(player, bot, "contact"), "pinned speaker 'two' has no contact line, yet something spoke");
            h.assertTrue(SoldierVoices.say(player, bot, "attack"), "pinned speaker 'two' did not use its own attack line");
            var other = new SoldierAi.Bot("VoicePinOne"); // the first one is still mid-line
            other.voice = id + "/one";
            other.health = SoldierMedical.Condition.HEALTHY;
            SoldierVoices.clear();
            h.assertTrue(SoldierVoices.say(player, other, "contact"), "pinned speaker 'one' did not speak");
            h.succeed();
        } finally {
            if (player != null) Soldiers.remove(player);
            Files.deleteIfExists(file);
            SoldierVoices.reload();
        }
    }

    private static void entry(ZipOutputStream zip, String name, String text) throws Exception {
        zip.putNextEntry(new ZipEntry(name)); zip.write(text.getBytes(StandardCharsets.UTF_8)); zip.closeEntry();
    }
}
