package com.wf.wflib.drone.client;

import com.wf.wflib.core.api.WFLib;
import com.wf.wflib.drone.api.DroneTask;
import com.wf.wflib.map.api.client.MapMenu;
import com.wf.wflib.map.api.client.MapPoint;
import com.wf.wflib.map.api.client.MapShape;
import com.wf.wflib.map.api.client.MapStyle;
import com.wf.wflib.map.api.client.MapTool;
import com.wf.wflib.map.api.client.MapView;
import com.wf.wflib.map.api.client.WFMaps;
import com.wf.wflib.pda.api.client.PdaClient;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Drones on every map host: each linked body's planned route (status), the PDA's unsent queue; right-click menu
 * orders the Drones app's current recipient (selected linked body, else nearest pad).
 */
final class DroneMapLayer implements MapTool {
    static final String LAYER = "wflib:drone_routes";
    private static final String GROUP = "WF Drone Routes";
    private static final ResourceLocation ICON = ResourceLocation.fromNamespaceAndPath(WFLib.MODID,
            "textures/map/pda_marker.png");
    private static final int PLANNED = 0x6CC24A;
    private static final int SELECTED = 0x4FC3F7;
    private static final int QUEUED = 0xE0A040;

    /** Client thread, after every status update and queue edit. */
    static void publish(List<DronesApp.Row> rows, DronesApp.Row selected, List<DroneTask> queue, Vec3 queueFrom) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) {
            return;
        }
        List<MapShape> shapes = new ArrayList<>();
        for (DronesApp.Row row : rows) {
            List<Vec3> route = row.entry().route();
            if (route.size() >= 2) {
                route(shapes, row.name(), route, row == selected ? SELECTED : PLANNED, false);
            }
        }
        if (!queue.isEmpty()) {
            List<Vec3> points = new ArrayList<>();
            points.add(queueFrom);
            for (DroneTask task : queue) {
                points.add(task.at() != null ? task.at() : queueFrom);
            }
            route(shapes, "QUEUE", points, QUEUED, true);
        }
        WFMaps.publish(LAYER, mc.level.dimension(), shapes);
    }

    /** Line through {@code points}; a numbered pin on each point after the first. */
    private static void route(List<MapShape> shapes, String name, List<Vec3> points, int colour, boolean queue) {
        List<MapPoint> path = new ArrayList<>(points.size());
        for (Vec3 p : points) {
            path.add(new MapPoint(p.x, p.z));
        }
        int y = (int) points.getFirst().y;
        shapes.add(new MapShape.Line(path, y, queue ? 1.5f : 2.0f, MapStyle.fill(colour, 0.85f),
                MapShape.Look.of(GROUP, 32).title(name)));
        for (int i = 1; i < points.size(); i++) {
            Vec3 p = points.get(i);
            String label = (queue ? "Q" : "") + i;
            shapes.add(new MapShape.Icon(new MapPoint(p.x, p.z), (int) p.y, ICON, 32, 10, 0, colour, 1,
                    MapShape.Look.of(GROUP, 33).title(String.format(Locale.ROOT, "%s step %d%n%d %d %d", name, i,
                            (int) Math.floor(p.x), (int) Math.floor(p.y), (int) Math.floor(p.z))).label(label, colour)));
        }
    }

    @Override
    public boolean available() {
        return PdaClient.carried() && DronesApp.orderable();
    }

    @Override
    public void menu(MapMenu menu, MapView view) {
        MapMenu drone = menu.sub("PDA drone: " + DronesApp.recipient());
        drone.item("Go here and hold", pos -> DronesApp.mapOrder("hold", Vec3.atBottomCenterOf(pos), false));
        drone.item("Add waypoint here", pos -> DronesApp.mapOrder("moveto", Vec3.atBottomCenterOf(pos), true));
        drone.item("Loiter here", pos -> DronesApp.mapOrder("loiter", Vec3.atBottomCenterOf(pos), false));
        drone.item("Strike here", pos -> DronesApp.mapOrder("strike", Vec3.atBottomCenterOf(pos), false));
        drone.item("Set PDA target here", pos -> DronesApp.mapTarget(Vec3.atBottomCenterOf(pos)));
        int queued = DronesApp.queued();
        if (queued > 0) {
            drone.item("Send queue (" + queued + " steps)", pos -> DronesApp.sendQueue());
            drone.item("Clear queue", pos -> DronesApp.clearQueue());
        }
    }
}
