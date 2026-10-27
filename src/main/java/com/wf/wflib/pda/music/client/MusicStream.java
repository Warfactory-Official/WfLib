package com.wf.wflib.pda.music.client;

import net.minecraft.client.sounds.AudioStream;
import org.lwjgl.BufferUtils;

import javax.sound.sampled.AudioFormat;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

/** PCM of one {@link PcmSource.Reader} for the sound engine; an empty buffer ends the source. Sound thread. */
final class MusicStream implements AudioStream {
    /** Engine queues 4 buffers of 16 KiB. */
    private static final int ENGINE_BYTES = 4 * 16384;

    private final PcmSource.Reader reader;
    private final int channels;
    private final long start;
    private final AudioFormat format;
    private short[] scratch = new short[0];
    private volatile long read;
    private volatile boolean ended;

    MusicStream(PcmSource.Reader reader, int rate, int channels, long start) {
        this.reader = reader;
        this.channels = channels;
        this.start = start;
        this.format = new AudioFormat(rate, 16, channels, true, false);
    }

    /** Sample being heard, per channel; the engine's queued buffers run ahead of the speaker. */
    long position() {
        return start + Math.max(0, read - ENGINE_BYTES / (2L * channels));
    }

    boolean ended() {
        return ended;
    }

    @Override
    public AudioFormat getFormat() {
        return format;
    }

    @Override
    public ByteBuffer read(int size) throws IOException {
        int frames = size / (2 * channels);
        if (scratch.length < frames * channels) {
            scratch = new short[frames * channels];
        }
        int filled = 0;
        while (filled < frames) {
            int n = reader.read(scratch, filled, frames - filled);
            if (n <= 0) {
                ended = true;
                break;
            }
            filled += n;
        }
        read += filled;
        ByteBuffer out = BufferUtils.createByteBuffer(filled * channels * 2).order(ByteOrder.LITTLE_ENDIAN);
        for (int i = 0; i < filled * channels; i++) {
            out.putShort(scratch[i]);
        }
        out.flip();
        return out;
    }

    @Override
    public void close() {
        reader.close();
    }
}
