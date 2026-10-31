package com.wf.wflib.build.api;

import com.wf.wflib.core.api.WFRegistry;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;

import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;
import com.wf.wflib.core.api.WFLib;

/** {@link BlueprintFormat} extension point, keyed {@code wflib:<id>}; one format per file extension. */
public final class BlueprintFormats {

    static final WFRegistry<BlueprintFormat> FORMATS = WFRegistry.create("build", "blueprint formats");

    private BlueprintFormats() {
    }

    public static ResourceLocation rl(String path) {
        return ResourceLocation.fromNamespaceAndPath(WFLib.MODID, path);
    }

    /** Dup id or dup extension throws. */
    public static void register(BlueprintFormat format) {
        if (forExtension(format.extension()) != null) {
            throw new IllegalStateException("Blueprint extension already taken: " + format.extension());
        }
        FORMATS.register(rl(format.id()), format);
    }

    @Nullable
    public static BlueprintFormat get(ResourceLocation id) {
        return id == null ? null : FORMATS.get(id);
    }

    @Nullable
    public static BlueprintFormat forFile(String fileName) {
        int dot = fileName.lastIndexOf('.');
        return dot < 0 ? null : forExtension(fileName.substring(dot + 1));
    }

    @Nullable
    private static BlueprintFormat forExtension(String extension) {
        String wanted = extension.toLowerCase(Locale.ROOT);
        for (BlueprintFormat format : FORMATS.values()) {
            if (format.extension().toLowerCase(Locale.ROOT).equals(wanted)) {
                return format;
            }
        }
        return null;
    }

    public static Set<ResourceLocation> ids() {
        return FORMATS.ids();
    }

    public static Set<String> extensions() {
        Set<String> out = new LinkedHashSet<>();
        for (BlueprintFormat format : FORMATS.values()) {
            out.add(format.extension().toLowerCase(Locale.ROOT));
        }
        return out;
    }
}
