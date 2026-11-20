package com.wf.wflib.contract;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonParser;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

/** Every data file the mod ships must load, or the game silently drops it with only a log line. */
class ShippedDataTest {
    private static final Path RES = Path.of("src/main/resources");

    private static List<Path> files(String dir) throws IOException {
        try (Stream<Path> s = Files.list(RES.resolve("data/wflib/wflib/" + dir))) {
            return s.filter(p -> p.toString().endsWith(".json")).sorted().toList();
        }
    }

    private static ResourceLocation id(Path p) {
        return ResourceLocation.fromNamespaceAndPath("wflib", p.getFileName().toString().replace(".json", ""));
    }

    @Test
    void everyShippedContractLoads() throws IOException {
        var files = files("contracts");
        assertTrue(files.size() >= 5, "the starter contracts are there");
        for (Path p : files) {
            var def = ContractDef.parse(id(p), JsonParser.parseString(Files.readString(p)).getAsJsonObject());
            assertFalse(def.description().isEmpty() && def.source().equals("intel"), p + ": intel contracts explain themselves");
        }
    }

    @Test
    void everyShippedContractPaysSomethingWithoutAnEconomy() throws IOException {
        for (Path p : files("contracts")) {
            var def = ContractDef.parse(id(p), JsonParser.parseString(Files.readString(p)).getAsJsonObject());
            assertFalse(def.items().isEmpty(), p + " has rewards.items for a server with no economy");
        }
    }

    @Test
    void everyModItemAContractNamesExistsWithModelTextureAndName() throws IOException {
        String lang = Files.readString(RES.resolve("assets/wflib/lang/en_us.json"));
        for (Path p : files("contracts")) {
            var def = ContractDef.parse(id(p), JsonParser.parseString(Files.readString(p)).getAsJsonObject());
            def.objectives().forEach(o -> {
                ResourceLocation item = switch (o) {
                    case ContractDef.RetrieveItem r -> r.item();
                    case ContractDef.DeliverItem d -> d.item();
                    default -> null;
                };
                if (item != null && item.getNamespace().equals("wflib")) mine(lang, item.getPath());
            });
        }
        for (String name : List.of("data_drive", "cache_manifest", "intel_drive", "laptop", "narcotics_brick")) mine(lang, name);
    }

    private static void mine(String lang, String name) {
        assertTrue(lang.contains("\"item.wflib." + name + "\""), name + " has a name");
        assertTrue(Files.exists(RES.resolve("assets/wflib/models/item/" + name + ".json")), name + " has a model");
        assertTrue(Files.exists(RES.resolve("assets/wflib/textures/item/" + name + ".png")), name + " has a texture");
    }

    @Test
    void theIntelContractIsTheOnlyOneNotOnTheBoard() throws IOException {
        for (Path p : files("contracts")) {
            var def = ContractDef.parse(id(p), JsonParser.parseString(Files.readString(p)).getAsJsonObject());
            assertEquals(p.getFileName().toString().startsWith("intel_") ? "intel" : "board", def.source(), p.toString());
        }
    }
}
