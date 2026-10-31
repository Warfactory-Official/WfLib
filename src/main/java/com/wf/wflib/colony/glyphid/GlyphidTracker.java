package com.wf.wflib.colony.glyphid;

import net.minecraft.world.level.Level;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.entity.EntityLeaveLevelEvent;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import com.wf.wflib.colony.api.EntityGlyphid;

/** Per-level set of live glyphids from join/leave events. */
public final class GlyphidTracker {

    private static final Map<Level, Set<EntityGlyphid>> BY_LEVEL = new ConcurrentHashMap<>();

    private GlyphidTracker() {
    }

    public static Set<EntityGlyphid> glyphids(Level level) {
        return BY_LEVEL.getOrDefault(level, Set.of());
    }

    public static int count(Level level) {
        return glyphids(level).size();
    }

    @SubscribeEvent
    public static void onJoin(EntityJoinLevelEvent event) {
        if (event.getEntity() instanceof EntityGlyphid glyphid && !event.getLevel().isClientSide) {
            BY_LEVEL.computeIfAbsent(event.getLevel(), k -> ConcurrentHashMap.newKeySet()).add(glyphid);
        }
    }

    @SubscribeEvent
    public static void onLeave(EntityLeaveLevelEvent event) {
        if (!(event.getEntity() instanceof EntityGlyphid glyphid) || event.getLevel().isClientSide) {
            return;
        }
        Set<EntityGlyphid> set = BY_LEVEL.get(event.getLevel());
        if (set != null) {
            set.remove(glyphid);
            if (set.isEmpty()) {
                BY_LEVEL.remove(event.getLevel());
            }
        }
    }
}
