package com.wf.wflib.pda.music;

import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.List;

/**
 * Gate for every audio file entering the PDA player (server scan, served chunk set, local scan, playback): pure
 * Java, nothing native sees bytes that failed {@link #probe}. Accepts exactly one Ogg logical stream of Vorbis or
 * Opus (mono / stereo) whose every page has a valid CRC, contiguous sequence numbers and non-decreasing granules;
 * header packets present and well formed; ends on an EOS page.
 */
public final class OggAudio {
    public static final int MAX_BYTES = 64 << 20;
    public static final int MAX_NAME = 160;
    /** Cover images above this are dropped (the track stays valid). */
    public static final int MAX_COVER_BYTES = 8 << 20;
    private static final int MAX_HEADER_BYTES = 12 << 20;
    private static final long MAX_SECONDS = 12 * 3600;
    private static final int MAX_FIELD = 96;
    private static final int[] CRC = new int[256];

    static {
        for (int i = 0; i < 256; i++) {
            int r = i << 24;
            for (int k = 0; k < 8; k++) {
                r = (r & 0x80000000) != 0 ? (r << 1) ^ 0x04C11DB7 : r << 1;
            }
            CRC[i] = r;
        }
    }

    private OggAudio() {
    }

    public enum Codec {
        VORBIS, OPUS
    }

    /** {@code cover}: raw PNG / JPEG bytes, null = none. Strings are sanitised, never null. */
    public record Tags(String title, String artist, String album, @Nullable byte[] cover) {
        public static final Tags EMPTY = new Tags("", "", "", null);
    }

    /** {@code samples}: per channel at {@code rate}. */
    public record Probe(Codec codec, int rate, int channels, long samples, Tags tags) {
        public long millis() {
            return samples * 1000 / rate;
        }
    }

    /** @throws IOException not a well formed Ogg Vorbis / Opus file; message is for logs */
    public static Probe probe(byte[] d) throws IOException {
        if (d.length < 64 || d.length > MAX_BYTES) {
            throw new IOException("size " + d.length);
        }
        List<byte[]> headers = new ArrayList<>(3);
        Codec codec = null;
        int needed = 0;
        byte[] cur = new byte[256];
        int curLen = 0;
        boolean mid = false;
        boolean eos = false;
        int serial = 0;
        long sequence = 0;
        long lastGranule = -1;
        for (int at = 0; at < d.length; ) {
            if (eos) {
                throw new IOException("data after EOS at " + at);
            }
            if (at + 27 > d.length || d[at] != 'O' || d[at + 1] != 'g' || d[at + 2] != 'g' || d[at + 3] != 'S'
                    || d[at + 4] != 0) {
                throw new IOException("bad Ogg page at " + at);
            }
            int flags = d[at + 5] & 0xFF;
            if ((flags & ~7) != 0) {
                throw new IOException("bad page flags at " + at);
            }
            boolean first = at == 0;
            if (first != ((flags & 2) != 0)) {
                throw new IOException("BOS flag misplaced at " + at);
            }
            if (((flags & 1) != 0) != mid) {
                throw new IOException("packet continuation mismatch at " + at);
            }
            eos = (flags & 4) != 0;
            long granule = le64(d, at + 6);
            int pageSerial = le32(d, at + 14);
            if (first) {
                serial = pageSerial;
            } else if (pageSerial != serial) {
                throw new IOException("second logical stream at " + at);
            }
            if (le32(d, at + 18) != sequence++) {
                throw new IOException("page sequence gap at " + at);
            }
            int segments = d[at + 26] & 0xFF;
            int body = at + 27 + segments;
            if (body > d.length) {
                throw new IOException("truncated page at " + at);
            }
            int size = 0;
            for (int i = 0; i < segments; i++) {
                size += d[at + 27 + i] & 0xFF;
            }
            int end = body + size;
            if (end > d.length) {
                throw new IOException("truncated page at " + at);
            }
            if (crc(d, at, end) != le32(d, at + 22)) {
                throw new IOException("page CRC mismatch at " + at);
            }
            if (granule != -1) {
                if (granule < lastGranule) {
                    throw new IOException("granule decreases at " + at);
                }
                lastGranule = granule;
            }
            int b = body;
            for (int i = 0; i < segments; i++) {
                int lace = d[at + 27 + i] & 0xFF;
                boolean keep = headers.size() < (needed == 0 ? 1 : needed);
                if (keep) {
                    if (curLen + lace > MAX_HEADER_BYTES) {
                        throw new IOException("header packet too large");
                    }
                    if (curLen + lace > cur.length) {
                        cur = Arrays.copyOf(cur, Math.max(curLen + lace, cur.length * 2));
                    }
                    System.arraycopy(d, b, cur, curLen, lace);
                    curLen += lace;
                }
                b += lace;
                mid = lace == 255;
                if (!mid && keep) {
                    byte[] packet = Arrays.copyOf(cur, curLen);
                    curLen = 0;
                    if (headers.isEmpty()) {
                        codec = identify(packet);
                        needed = codec == Codec.VORBIS ? 3 : 2;
                    } else {
                        checkHeader(codec, headers.size(), packet);
                    }
                    headers.add(packet);
                }
            }
            if (first && headers.isEmpty()) {
                throw new IOException("identification packet spans pages");
            }
            at = end;
        }
        if (!eos || mid || codec == null || headers.size() != needed) {
            throw new IOException("incomplete stream");
        }
        byte[] id = headers.getFirst();
        int rate;
        long samples;
        int channels = id[codec == Codec.VORBIS ? 11 : 9] & 0xFF;
        if (codec == Codec.VORBIS) {
            rate = le32(id, 12);
            samples = lastGranule;
        } else {
            rate = 48_000;
            samples = lastGranule - le16(id, 10);
        }
        if (samples <= 0 || samples > MAX_SECONDS * rate) {
            throw new IOException("length " + samples);
        }
        return new Probe(codec, rate, channels, samples, tags(codec, headers.get(1)));
    }

    private static Codec identify(byte[] p) throws IOException {
        if (p.length == 30 && p[0] == 1 && ascii(p, 1, "vorbis")) {
            int channels = p[11] & 0xFF;
            int rate = le32(p, 12);
            int small = p[28] & 0xF;
            int large = (p[28] & 0xFF) >> 4;
            if (le32(p, 7) != 0 || channels < 1 || channels > 2 || rate < 8000 || rate > 192_000
                    || small < 6 || large > 13 || small > large || (p[29] & 1) == 0) {
                throw new IOException("bad Vorbis identification header");
            }
            return Codec.VORBIS;
        }
        if (p.length == 19 && ascii(p, 0, "OpusHead")) {
            int channels = p[9] & 0xFF;
            if ((p[8] & 0xF0) != 0 || channels < 1 || channels > 2 || p[18] != 0) {
                throw new IOException("unsupported OpusHead");
            }
            return Codec.OPUS;
        }
        throw new IOException("neither Vorbis nor Opus");
    }

    private static void checkHeader(Codec codec, int index, byte[] p) throws IOException {
        boolean ok = switch (codec) {
            case VORBIS -> index == 1 ? p.length >= 12 && p[0] == 3 && ascii(p, 1, "vorbis")
                    : p.length >= 16 && p[0] == 5 && ascii(p, 1, "vorbis");
            case OPUS -> p.length >= 16 && ascii(p, 0, "OpusTags");
        };
        if (!ok) {
            throw new IOException("bad " + codec + " header " + index);
        }
    }

    /** Malformed comments never fail the file: partial tags are kept. */
    private static Tags tags(Codec codec, byte[] p) {
        String title = "";
        String artist = "";
        String albumArtist = "";
        String album = "";
        byte[] cover = null;
        int coverRank = -1;
        try {
            int at = codec == Codec.VORBIS ? 7 : 8;
            int vendor = le32(p, at);
            at += 4;
            if (vendor < 0 || vendor > p.length - at - 4) {
                return Tags.EMPTY;
            }
            at += vendor;
            int count = le32(p, at);
            at += 4;
            for (int i = 0; i < count && i < 1024; i++) {
                if (at + 4 > p.length) {
                    break;
                }
                int len = le32(p, at);
                at += 4;
                if (len < 0 || len > p.length - at) {
                    break;
                }
                int eq = -1;
                for (int k = 0; k < Math.min(len, 32); k++) {
                    if (p[at + k] == '=') {
                        eq = k;
                        break;
                    }
                }
                if (eq > 0) {
                    String key = new String(p, at, eq, StandardCharsets.US_ASCII).toUpperCase(java.util.Locale.ROOT);
                    int valueAt = at + eq + 1;
                    int valueLen = len - eq - 1;
                    switch (key) {
                        case "TITLE" -> title = text(p, valueAt, valueLen);
                        case "ARTIST" -> artist = text(p, valueAt, valueLen);
                        case "ALBUMARTIST" -> albumArtist = text(p, valueAt, valueLen);
                        case "ALBUM" -> album = text(p, valueAt, valueLen);
                        case "METADATA_BLOCK_PICTURE" -> {
                            byte[] picture = picture(p, valueAt, valueLen);
                            // type 3 = front cover wins over other types
                            int rank = picture == null ? -1 : pictureType(p, valueAt, valueLen) == 3 ? 2 : 1;
                            if (rank > coverRank) {
                                cover = picture;
                                coverRank = rank;
                            }
                        }
                        case "COVERART" -> {
                            byte[] raw = decode64(p, valueAt, valueLen);
                            if (coverRank < 0 && imageMagic(raw)) {
                                cover = raw;
                                coverRank = 0;
                            }
                        }
                        default -> {
                        }
                    }
                }
                at += len;
            }
        } catch (RuntimeException e) {
            // keep what was read
        }
        return new Tags(title, artist.isEmpty() ? albumArtist : artist, album, cover);
    }

    /** Value is base64 of a FLAC PICTURE block (RFC 9639 8.8). */
    @Nullable
    private static byte[] picture(byte[] p, int at, int len) {
        byte[] block = decode64(p, at, len);
        if (block == null || block.length < 32) {
            return null;
        }
        int mime = be32(block, 4);
        int o = 8;
        if (mime < 0 || mime > block.length - o - 4) {
            return null;
        }
        o += mime;
        int desc = be32(block, o);
        o += 4;
        if (desc < 0 || desc > block.length - o - 20) {
            return null;
        }
        o += desc + 16;
        int dataLen = be32(block, o);
        o += 4;
        if (dataLen <= 0 || dataLen > MAX_COVER_BYTES || dataLen > block.length - o) {
            return null;
        }
        byte[] image = Arrays.copyOfRange(block, o, o + dataLen);
        return imageMagic(image) ? image : null;
    }

    private static int pictureType(byte[] p, int at, int len) {
        byte[] block = decode64(p, at, Math.min(len, 8));
        return block == null || block.length < 4 ? -1 : be32(block, 0);
    }

    /** Null on bad base64 or oversize. */
    @Nullable
    private static byte[] decode64(byte[] p, int at, int len) {
        if (len <= 0 || len > MAX_COVER_BYTES / 3 * 4 + 1024) {
            return null;
        }
        try {
            return Base64.getDecoder().decode(Arrays.copyOfRange(p, at, at + len));
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private static boolean imageMagic(@Nullable byte[] b) {
        if (b == null || b.length < 16 || b.length > MAX_COVER_BYTES) {
            return false;
        }
        boolean png = (b[0] & 0xFF) == 0x89 && b[1] == 'P' && b[2] == 'N' && b[3] == 'G';
        boolean jpeg = (b[0] & 0xFF) == 0xFF && (b[1] & 0xFF) == 0xD8 && (b[2] & 0xFF) == 0xFF;
        return png || jpeg;
    }

    private static String text(byte[] p, int at, int len) {
        String s = new String(p, at, Math.min(len, MAX_FIELD * 4), StandardCharsets.UTF_8);
        StringBuilder out = new StringBuilder(Math.min(s.length(), MAX_FIELD));
        for (int i = 0; i < s.length() && out.length() < MAX_FIELD; i++) {
            char c = s.charAt(i);
            out.append(c < ' ' || c == 0x7F || c == 0xFFFD ? ' ' : c);
        }
        return out.toString().strip();
    }

    /** Ogg CRC-32 (poly 0x04C11DB7, no reflection) of {@code [from, to)} with the checksum field read as zero. */
    private static int crc(byte[] d, int from, int to) {
        int crc = 0;
        for (int i = from; i < to; i++) {
            int b = i >= from + 22 && i < from + 26 ? 0 : d[i] & 0xFF;
            crc = (crc << 8) ^ CRC[(crc >>> 24) ^ b];
        }
        return crc;
    }

    private static boolean ascii(byte[] d, int at, String s) {
        if (at + s.length() > d.length) {
            return false;
        }
        for (int i = 0; i < s.length(); i++) {
            if (d[at + i] != s.charAt(i)) {
                return false;
            }
        }
        return true;
    }

    private static int le16(byte[] d, int at) {
        return (d[at] & 0xFF) | (d[at + 1] & 0xFF) << 8;
    }

    private static int le32(byte[] d, int at) {
        return le16(d, at) | le16(d, at + 2) << 16;
    }

    private static long le64(byte[] d, int at) {
        return (le32(d, at) & 0xFFFFFFFFL) | (long) le32(d, at + 4) << 32;
    }

    private static int be32(byte[] d, int at) {
        return (d[at] & 0xFF) << 24 | (d[at + 1] & 0xFF) << 16 | (d[at + 2] & 0xFF) << 8 | d[at + 3] & 0xFF;
    }

    /** Library file names: relative, forward slashes, no traversal, supported extension. */
    public static boolean validName(String name) {
        if (name.isEmpty() || name.length() > MAX_NAME || name.startsWith("/") || name.contains("\\")
                || name.contains("//")) {
            return false;
        }
        String lower = name.toLowerCase(java.util.Locale.ROOT);
        if (!(lower.endsWith(".ogg") || lower.endsWith(".opus") || lower.endsWith(".oga"))) {
            return false;
        }
        for (String part : name.split("/", -1)) {
            if (part.isEmpty() || part.equals(".") || part.equals("..")) {
                return false;
            }
        }
        for (int i = 0; i < name.length(); i++) {
            if (name.charAt(i) < ' ' || name.charAt(i) == 0x7F) {
                return false;
            }
        }
        return true;
    }
}
