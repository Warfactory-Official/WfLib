package com.wf.wflib.physics.step;

import com.mojang.logging.LogUtils;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import org.slf4j.Logger;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Pins every entity/block-entity type of unpatched mods to the server thread under Tessellate. Reflective; no-op
 * without the API. Namespaces: {@value #PROPERTY} overrides {@link #DEFAULT_NAMESPACES}.
 */
public final class TessellateMainThread {

    private static final Logger LOGGER = LogUtils.getLogger();
    static final String PROPERTY = "wflib.tessellate.mainThreadNamespaces";
    // Patched AE2/IR/UMC run on workers; stock needs -D...=ae2,immersiverailroading.
    static final String DEFAULT_NAMESPACES = "";

    private TessellateMainThread() {
    }

    /** After registries freeze (FMLLoadComplete). */
    public static void register() {
        Class<?> api;
        try {
            api = Class.forName(TessellateBridge.API, true, TessellateMainThread.class.getClassLoader());
        } catch (ClassNotFoundException absent) {
            return;
        }
        Set<String> namespaces = Arrays.stream(System.getProperty(PROPERTY, DEFAULT_NAMESPACES).split(","))
                .map(String::trim).filter(s -> !s.isEmpty()).collect(Collectors.toSet());
        try {
            MethodHandles.Lookup lookup = MethodHandles.publicLookup();
            MethodHandle entity = lookup.findStatic(api, "registerMainThreadEntity",
                    MethodType.methodType(void.class, net.minecraft.world.entity.EntityType.class));
            MethodHandle blockEntity = lookup.findStatic(api, "registerMainThreadBlockEntity",
                    MethodType.methodType(void.class, net.minecraft.world.level.block.entity.BlockEntityType.class));
            int entities = 0;
            int blockEntities = 0;
            for (var type : BuiltInRegistries.ENTITY_TYPE) {
                if (pinned(namespaces, BuiltInRegistries.ENTITY_TYPE.getKey(type))) {
                    entity.invoke(type);
                    entities++;
                }
            }
            for (var type : BuiltInRegistries.BLOCK_ENTITY_TYPE) {
                if (pinned(namespaces, BuiltInRegistries.BLOCK_ENTITY_TYPE.getKey(type))) {
                    blockEntity.invoke(type);
                    blockEntities++;
                }
            }
            LOGGER.info("Tessellate main-thread types {}: {} entity, {} block entity", namespaces, entities, blockEntities);
        } catch (ReflectiveOperationException mismatch) {
            throw new IllegalStateException("Tessellate main-thread API mismatch", mismatch);
        } catch (Throwable t) {
            throw t instanceof RuntimeException r ? r : new IllegalStateException(t);
        }
    }

    private static boolean pinned(Set<String> namespaces, ResourceLocation key) {
        return key != null && namespaces.contains(key.getNamespace());
    }
}
