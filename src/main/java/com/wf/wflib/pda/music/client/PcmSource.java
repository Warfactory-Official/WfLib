package com.wf.wflib.pda.music.client;

import com.wf.wflib.pda.music.OggAudio;
import com.wf.wflib.radio.api.OggOpus;
import org.lwjgl.stb.STBVorbis;
import org.lwjgl.stb.STBVorbisInfo;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.system.MemoryUtil;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.IntBuffer;
import java.nio.ShortBuffer;
import java.util.concurrent.atomic.AtomicInteger;

/** Seekable 16-bit interleaved PCM of one probed file; {@link #close} frees native memory once readers are done. */
interface PcmSource extends AutoCloseable {
    int rate();

    int channels();

    /** Samples per channel. */
    long length();

    /** Decoder positioned at {@code sample}; caller closes. */
    Reader reader(long sample) throws IOException;

    @Override
    void close();

    interface Reader extends AutoCloseable {
        /** @return samples per channel written to {@code out[offset...]}; 0 = end of track */
        int read(short[] out, int offset, int max) throws IOException;

        @Override
        void close();
    }

    /** {@code data} must have passed {@link OggAudio#probe}; Vorbis is also trial-decoded natively before use. */
    static PcmSource open(byte[] data, OggAudio.Probe probe) throws IOException {
        return probe.codec() == OggAudio.Codec.OPUS ? new Opus(data, probe) : new Vorbis(data, probe);
    }

    final class Opus implements PcmSource {
        private final OggOpus file;
        private final OggAudio.Probe probe;

        Opus(byte[] data, OggAudio.Probe probe) throws IOException {
            this.file = OggOpus.parse(data);
            this.probe = probe;
        }

        @Override
        public int rate() {
            return OggOpus.RATE;
        }

        @Override
        public int channels() {
            return probe.channels();
        }

        @Override
        public long length() {
            return file.length();
        }

        @Override
        public Reader reader(long sample) throws IOException {
            OggOpus.Reader r = file.reader(sample, probe.channels());
            return new Reader() {
                @Override
                public int read(short[] out, int offset, int max) throws IOException {
                    return r.read(out, offset, max);
                }

                @Override
                public void close() {
                }
            };
        }

        @Override
        public void close() {
        }
    }

    final class Vorbis implements PcmSource {
        private final NativeBytes bytes;
        private final OggAudio.Probe probe;

        Vorbis(byte[] data, OggAudio.Probe probe) throws IOException {
            this.bytes = new NativeBytes(data);
            this.probe = probe;
            try (Reader trial = reader(0)) {
                if (trial.read(new short[4096 * probe.channels()], 0, 4096) <= 0) {
                    throw new IOException("Vorbis decodes to nothing");
                }
            } catch (IOException | RuntimeException e) {
                bytes.release();
                throw e;
            }
        }

        @Override
        public int rate() {
            return probe.rate();
        }

        @Override
        public int channels() {
            return probe.channels();
        }

        @Override
        public long length() {
            return probe.samples();
        }

        @Override
        public Reader reader(long sample) throws IOException {
            bytes.retain();
            long handle = 0;
            try (MemoryStack stack = MemoryStack.stackPush()) {
                IntBuffer error = stack.mallocInt(1);
                handle = STBVorbis.stb_vorbis_open_memory(bytes.buffer, error, null);
                if (handle == 0) {
                    throw new IOException("stb_vorbis error " + error.get(0));
                }
                STBVorbisInfo info = STBVorbisInfo.malloc(stack);
                STBVorbis.stb_vorbis_get_info(handle, info);
                if (info.channels() != probe.channels() || info.sample_rate() != probe.rate()) {
                    throw new IOException("Vorbis header disagrees with probe");
                }
                if (sample > 0 && !STBVorbis.stb_vorbis_seek(handle, (int) Math.min(sample, Integer.MAX_VALUE))) {
                    throw new IOException("Vorbis seek failed");
                }
            } catch (IOException | RuntimeException e) {
                if (handle != 0) {
                    STBVorbis.stb_vorbis_close(handle);
                }
                bytes.release();
                throw e;
            }
            return new VorbisReader(handle, probe.channels(), bytes);
        }

        @Override
        public void close() {
            bytes.release();
        }
    }

    final class VorbisReader implements Reader {
        private final long handle;
        private final int channels;
        private final NativeBytes bytes;
        private boolean closed;

        VorbisReader(long handle, int channels, NativeBytes bytes) {
            this.handle = handle;
            this.channels = channels;
            this.bytes = bytes;
        }

        @Override
        public synchronized int read(short[] out, int offset, int max) {
            if (closed) {
                return 0;
            }
            ShortBuffer buffer = MemoryUtil.memAllocShort(max * channels);
            try {
                int n = STBVorbis.stb_vorbis_get_samples_short_interleaved(handle, channels, buffer);
                buffer.get(0, out, offset * channels, n * channels);
                return n;
            } finally {
                MemoryUtil.memFree(buffer);
            }
        }

        @Override
        public synchronized void close() {
            if (!closed) {
                closed = true;
                STBVorbis.stb_vorbis_close(handle);
                bytes.release();
            }
        }
    }

    /** Direct copy of the file, freed when the source and every reader have released it. */
    final class NativeBytes {
        final ByteBuffer buffer;
        private final AtomicInteger refs = new AtomicInteger(1);

        NativeBytes(byte[] data) {
            buffer = MemoryUtil.memAlloc(data.length);
            buffer.put(data).flip();
        }

        void retain() {
            refs.incrementAndGet();
        }

        void release() {
            if (refs.decrementAndGet() == 0) {
                MemoryUtil.memFree(buffer);
            }
        }
    }
}
