package com.wf.wflib.missile;

import com.wf.wflib.missile.api.MissilePreset;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertTrue;

class MissileCatalogueTest {

    @BeforeAll
    static void defineCatalogue() {
        MissileCatalogue.define();
    }

    @Test
    void everyEntryHasItemModel() {
        for (MissilePreset p : MissileCatalogue.all()) {
            Path json = Path.of("src/main/resources/assets", p.id().getNamespace(), "models/item",
                    p.itemPath() + ".json");
            assertTrue(Files.isRegularFile(json), json.toString());
        }
    }
}
