package com.wf.wflib.client.fx;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

/**
 * Flashback is optional, so this is reflective: absent or failing => never a screenshot.
 * A Flashback screenshot is an export of one frame, started and ended on the replay's current whole tick.
 */
final class FlashbackFrame {
    private static final Field EXPORT_JOB;
    private static final Method SETTINGS;
    private static final Method START;
    private static final Method END;

    static {
        Field job = null;
        Method settings = null, start = null, end = null;
        try {
            Class<?> flashback = Class.forName("com.moulberry.flashback.Flashback");
            job = flashback.getField("EXPORT_JOB");
            settings = Class.forName("com.moulberry.flashback.exporting.ExportJob").getMethod("getSettings");
            Class<?> s = Class.forName("com.moulberry.flashback.exporting.ExportSettings");
            start = s.getMethod("startTick");
            end = s.getMethod("endTick");
        } catch (ReflectiveOperationException | LinkageError e) {
            job = null;
        }
        EXPORT_JOB = job;
        SETTINGS = settings;
        START = start;
        END = end;
    }

    private FlashbackFrame() {
    }

    /** A Flashback screenshot is being rendered: one frame, on a whole tick. */
    static boolean screenshot() {
        if (EXPORT_JOB == null) {
            return false;
        }
        try {
            Object job = EXPORT_JOB.get(null);
            if (job == null) {
                return false;
            }
            Object settings = SETTINGS.invoke(job);
            return (int) END.invoke(settings) == (int) START.invoke(settings);
        } catch (ReflectiveOperationException | RuntimeException e) {
            return false;
        }
    }
}
