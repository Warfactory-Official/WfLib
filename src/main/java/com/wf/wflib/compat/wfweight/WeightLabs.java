package com.wf.wflib.compat.wfweight;

import com.warfactory.ultimateweight.UltimateWeightCommon;
import com.wf.wflib.labs.api.LabContent;
import com.wf.wflib.labs.api.LabContents;
import com.wf.wflib.labs.api.LabProducers;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.Map;

/** Class-loaded only with wfweight present. */
public final class WeightLabs {
    private WeightLabs() {
    }

    public static void register() {
        LabProducers.register(new WeightLoadoutProducer());
        LabContents.register(new LabContent() {
            @Override
            public String id() {
                return "wfweight:weights";
            }

            @Override
            public Map<String, String> packs() {
                try {
                    byte[] yaml = UltimateWeightCommon.serializeActiveConfig().getBytes(StandardCharsets.UTF_8);
                    return Map.of("weight_config", HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                            .digest(yaml)));
                } catch (java.security.NoSuchAlgorithmException e) {
                    throw new IllegalStateException(e);
                }
            }
        });
    }
}
