package com.wf.wflib.pda.music.client;

import com.wf.wflib.pda.music.BackgroundImage;
import com.wf.wflib.pda.music.MusicChunkPacket;
import com.wf.wflib.pda.music.MusicFetchPacket;
import com.wf.wflib.pda.music.OggAudio;
import net.neoforged.neoforge.network.PacketDistributor;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.concurrent.CompletableFuture;

/** One server download at a time per channel; a new fetch or {@link #cancel} drops the previous. Main thread. */
public final class MusicNet {
    public static final MusicNet AUDIO = new MusicNet(false, OggAudio.MAX_BYTES);
    public static final MusicNet BACKGROUND = new MusicNet(true, BackgroundImage.MAX_BYTES);

    private static final class Active {
        final int id;
        final CompletableFuture<byte[]> done = new CompletableFuture<>();
        final ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        int next;
        int total = -1;

        Active(int id) {
            this.id = id;
        }
    }

    private static int counter;
    private final boolean background;
    private final int maxBytes;
    private Active active;

    private MusicNet(boolean background, int maxBytes) {
        this.background = background;
        this.maxBytes = maxBytes;
    }

    public CompletableFuture<byte[]> fetch(String name) {
        cancel();
        active = new Active(++counter);
        PacketDistributor.sendToServer(new MusicFetchPacket(active.id, background, name));
        return active.done;
    }

    public void cancel() {
        if (active != null) {
            if (!active.done.isDone()) {
                PacketDistributor.sendToServer(new MusicFetchPacket(active.id, background, ""));
                active.done.cancel(false);
            }
            active = null;
        }
    }

    /** 0..100 of the running download, -1 = none. */
    public int percent() {
        Active a = active;
        return a == null || a.done.isDone() || a.total <= 0 ? -1 : a.next * 100 / a.total;
    }

    public static void accept(MusicChunkPacket p) {
        AUDIO.receive(p);
        BACKGROUND.receive(p);
    }

    private void receive(MusicChunkPacket p) {
        Active a = active;
        if (a == null || p.transfer() != a.id || a.done.isDone()) {
            return;
        }
        if (p.total() <= 0 || p.total() > (maxBytes + MusicChunkPacket.CHUNK - 1) / MusicChunkPacket.CHUNK || p.index() != a.next
                || (a.total >= 0 && a.total != p.total())) {
            a.done.completeExceptionally(new IOException(p.total() == 0 ? "server refused" : "bad chunk"));
            return;
        }
        a.total = p.total();
        a.bytes.writeBytes(p.data());
        if (a.bytes.size() > maxBytes) {
            a.done.completeExceptionally(new IOException("oversize"));
            return;
        }
        a.next++;
        if (a.next == a.total) {
            a.done.complete(a.bytes.toByteArray());
        }
    }
}
