package com.wf.wflib.round.lab;

import com.wf.wflib.labs.api.LabContext;
import com.wf.wflib.labs.api.LabProducer;
import com.wf.wflib.labs.api.LabSink;
import com.wf.wflib.labs.api.LabTable;

/** The cover materials {@code wflib:cover} uses, with the penetration table values the game reads for them. */
public final class CoverBlocksProducer implements LabProducer {
    public static final String ID = "wflib:cover_blocks";

    private static final LabTable TABLE = LabTable.of("cover_blocks")
            .dimStr("block").dimStr("material_class")
            .measure("resistance", "mm_rha/m").measure("toughness", "kg_m/s").measure("ricochet_deg", "deg")
            .measure("equivalent_blocks", "").measureStr("examples")
            .build();

    @Override
    public String id() {
        return ID;
    }

    @Override
    public String domain() {
        return "shared";
    }

    @Override
    public LabTable table() {
        return TABLE;
    }

    @Override
    public void produce(LabContext ctx, LabSink sink) {
        for (CoverBlocks.Entry e : CoverBlocks.all()) {
            sink.row(e.id(), e.material().name().toLowerCase(java.util.Locale.ROOT), e.resistance(), e.toughness(),
                    e.ricochet(), e.equivalents(), String.join(",", e.examples()));
        }
    }
}
