package com.wf.wflib.audio;

import com.wf.wflib.audio.api.AudioCategory;
import net.neoforged.neoforge.common.ModConfigSpec;

/** {@code wflib-audio-client.toml}. Volumes also sliders in Options > Music & Sounds; vanilla category sliders do not reach them. */
public final class AudioClientConfig {
    public static final ModConfigSpec SPEC;
    /** Per {@link AudioCategory} ordinal. */
    public static final ModConfigSpec.DoubleValue[] VOLUME;
    public static final ModConfigSpec.DoubleValue CABIN_MUFFLE;

    static {
        ModConfigSpec.Builder b = new ModConfigSpec.Builder();
        AudioCategory[] cats = AudioCategory.values();
        VOLUME = new ModConfigSpec.DoubleValue[cats.length];
        for (AudioCategory c : cats) {
            VOLUME[c.ordinal()] = b.defineInRange(c.getSerializedName(), 1.0, 0.0, 1.0);
        }
        CABIN_MUFFLE = b
                .comment("How strongly a closed vehicle cabin muffles outside sound, 0 (off) to 1.")
                .defineInRange("cabinMuffle", 1.0, 0.0, 1.0);
        SPEC = b.build();
    }

    private AudioClientConfig() {
    }
}
