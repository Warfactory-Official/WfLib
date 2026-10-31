package com.wf.wflib.drone.api;

import com.wf.wflib.core.api.OrderedRegistry;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.function.Function;

/** What the Drones app shows and does for one linked body; each mod registers a provider for its own. */
public interface RemoteBody {

    String name();

    /** Short, upper case. */
    String status();

    /** 0..1. */
    float fuel();

    boolean electric();

    List<Cell> cells();

    /** One line per weapon. */
    List<String> ammo();

    /** @return null = may take manual control, else why not (shown). */
    @Nullable
    String connectRefusal(ServerPlayer player);

    /** Manual control: only after {@link #connectRefusal} answered null. */
    void connect(ServerPlayer player);

    /** @param ratio 0..1 health */
    record Cell(String label, float ratio) {
    }

    /** Descending priority; first non-null answer wins. */
    OrderedRegistry<Function<Entity, RemoteBody>> PROVIDERS = OrderedRegistry.create("drone", "drone.remote_bodies");

    @Nullable
    static RemoteBody of(Entity entity) {
        List<Function<Entity, RemoteBody>> providers = PROVIDERS.values();
        for (int i = 0; i < providers.size(); i++) {
            RemoteBody body = providers.get(i).apply(entity);
            if (body != null) {
                return body;
            }
        }
        return null;
    }
}
