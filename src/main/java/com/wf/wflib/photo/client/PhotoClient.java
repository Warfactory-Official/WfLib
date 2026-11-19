package com.wf.wflib.photo.client;

import com.wf.wflib.photo.PhotoActionPacket;
import com.wf.wflib.photo.PhotoChunkPacket;
import com.wf.wflib.photo.PhotoListPacket;
import com.wf.wflib.photo.PhotoListRequestPacket;
import com.wf.wflib.photo.PhotoShutterPacket;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.minecraft.client.Minecraft;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RenderFrameEvent;
import net.neoforged.neoforge.client.extensions.common.RegisterClientExtensionsEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.network.PacketDistributor;
import org.jetbrains.annotations.Nullable;

/** Client facade of the photo system: packet entry points, the gallery list, and the viewer. */
public final class PhotoClient {
    private static final List<PhotoListPacket.Row> ROWS = new ArrayList<>();

    private PhotoClient() {
    }

    public static void setup() {
        PhotoApp.register();
        NeoForge.EVENT_BUS.addListener(RenderFrameEvent.Pre.class, PhotoCapture::onFrame);
        NeoForge.EVENT_BUS.addListener(ClientTickEvent.Post.class, event -> PhotoCapture.tickUpload());
        NeoForge.EVENT_BUS.addListener(ClientPlayerNetworkEvent.LoggingOut.class, event -> {
            ROWS.clear();
            PhotoCapture.clear();
            PhotoImages.clear();
        });
    }

    /** Mod bus: draws the camera item from its model. */
    public static void clientExtensions(RegisterClientExtensionsEvent event) {
        CameraRenderer.register(event);
    }

    public static void shutter(PhotoShutterPacket packet) {
        PhotoCapture.arm(packet);
    }

    public static void list(PhotoListPacket packet) {
        for (PhotoListPacket.Row old : ROWS) {
            if (packet.rows().stream().noneMatch(r -> r.id().equals(old.id()))) {
                PhotoImages.forget(old.id());
            }
        }
        ROWS.clear();
        ROWS.addAll(packet.rows());
        PhotoApp.listChanged();
    }

    public static void chunk(PhotoChunkPacket packet) {
        PhotoImages.chunk(packet);
    }

    static List<PhotoListPacket.Row> rows() {
        return ROWS;
    }

    @Nullable
    static PhotoListPacket.Row row(UUID id) {
        for (PhotoListPacket.Row r : ROWS) {
            if (r.id().equals(id)) {
                return r;
            }
        }
        return null;
    }

    static void requestList() {
        PacketDistributor.sendToServer(new PhotoListRequestPacket());
    }

    static void action(int op, UUID id) {
        PacketDistributor.sendToServer(new PhotoActionPacket(op, id));
    }

    /** Opens the viewer for photo {@code id}; the server only serves it to its owner or once printed. */
    public static void view(UUID id) {
        Minecraft.getInstance().setScreen(new PhotoScreen(id));
    }
}
