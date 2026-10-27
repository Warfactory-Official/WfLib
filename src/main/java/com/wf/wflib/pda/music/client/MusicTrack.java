package com.wf.wflib.pda.music.client;

import com.wf.wflib.pda.music.OggAudio;

/** Library entry; identity = ({@code source}, {@code name}). */
record MusicTrack(Source source, String name, String title, String artist, String album, long millis, long size) {
    enum Source {
        SERVER, LOCAL
    }

    static MusicTrack local(String name, OggAudio.Probe probe, long size) {
        OggAudio.Tags t = probe.tags();
        return new MusicTrack(Source.LOCAL, name, t.title(), t.artist(), t.album(), probe.millis(), size);
    }

    /** Shown name: tag title, else the file name without folders / extension. */
    String label() {
        if (!title.isEmpty()) {
            return title;
        }
        String file = name.substring(name.lastIndexOf('/') + 1);
        int dot = file.lastIndexOf('.');
        return dot > 0 ? file.substring(0, dot) : file;
    }

    boolean same(MusicTrack other) {
        return source == other.source && name.equals(other.name);
    }

    /** Placeholder for a playlist entry whose file is gone. */
    boolean missing() {
        return millis < 0;
    }

    static MusicTrack missing(Source source, String name) {
        return new MusicTrack(source, name, "", "", "", -1, 0);
    }
}
