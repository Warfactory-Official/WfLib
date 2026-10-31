package com.wf.wflib.pda.api.client;

import net.minecraft.client.player.LocalPlayer;
import net.minecraft.resources.ResourceLocation;

import com.wf.wflib.core.api.OrderedRegistry;

import java.util.ArrayList;
import java.util.List;

/** Registered apps, priority descending = home grid order; {@link #visible} = home grid, refreshed per client tick. */
public final class PdaApps {
    public static final OrderedRegistry<PdaApp> APPS = OrderedRegistry.create("pda", "pda.apps");
    private static final List<PdaApp> VISIBLE = new ArrayList<>();
    private static final List<PdaApp> HOME = new ArrayList<>();

    private PdaApps() {
    }

    /** Client setup; the shell binds every app once, on first draw. */
    public static void register(PdaApp app, int priority) {
        APPS.register(app.id(), priority, app);
    }

    public static PdaApp get(ResourceLocation id) {
        for (PdaApp app : APPS.values()) {
            if (app.id().equals(id)) {
                return app;
            }
        }
        return null;
    }

    static List<PdaApp> all() {
        return APPS.values();
    }

    static List<PdaApp> visible() {
        if (!com.wf.wflib.pda.api.client.PdaThemeState.gamma()) return VISIBLE;
        HOME.clear();
        for (PdaApp app : VISIBLE) {
            String id = app.id().toString();
            if (!id.equals("wflib:map") && !id.equals("wflib:alerts") && !id.equals("wflib:terminal")) HOME.add(app);
        }
        return HOME;
    }

    static void refresh(LocalPlayer player) {
        VISIBLE.clear();
        List<PdaApp> all = APPS.values();
        for (int i = 0; i < all.size(); i++) {
            PdaApp app = all.get(i);
            if (app.visible(player)) {
                VISIBLE.add(app);
            }
        }
        PdaApp open = PdaClient.app();
        if (open != null && !VISIBLE.contains(open)) {
            PdaClient.home();
        }
    }
}
