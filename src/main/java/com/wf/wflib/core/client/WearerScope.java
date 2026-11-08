package com.wf.wflib.core.client;

import net.minecraft.client.model.EntityModel;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;

/**
 * Brackets a draw of something worn that GemRender poses through the jointed wearer body (crouch, kneel, lean, aim): that
 * body only knows its wearer inside the armour layer's own scope, so a piece drawn anywhere else is posed as if the wearer
 * stood. A no-op without that body mod. Render thread only.
 */
public final class WearerScope {
    private static final MethodHandle BEGIN, END;

    static {
        MethodHandle begin = null, end = null;
        try {
            Class<?> scope = Class.forName("com.norwood.ahf.client.body.ArmorRenderer");
            MethodHandles.Lookup lookup = MethodHandles.publicLookup();
            begin = lookup.findStatic(scope, "begin", MethodType.methodType(void.class, EntityModel.class));
            end = lookup.findStatic(scope, "end", MethodType.methodType(void.class));
        } catch (ReflectiveOperationException | LinkageError absent) {
            // no jointed body: nothing to bracket
        }
        BEGIN = begin;
        END = end;
    }

    private WearerScope() {}

    public static void begin(EntityModel<?> parent) {
        if (BEGIN == null) return;
        try {
            BEGIN.invoke(parent);
        } catch (Throwable ignored) {
            // a changed signature must not break rendering
        }
    }

    public static void end() {
        if (END == null) return;
        try {
            END.invoke();
        } catch (Throwable ignored) {
            // see begin
        }
    }
}
