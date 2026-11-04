package com.wf.wflib.labs;

import com.wf.wflib.labs.api.LabSink;
import com.wf.wflib.labs.api.LabTable;

import java.io.BufferedWriter;
import java.io.Closeable;
import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.zip.Deflater;
import java.util.zip.GZIPOutputStream;

/** One {@code <table>.ndjson.gz}: a JSON array per row, column order, gzip level 1. */
final class LabWriter implements LabSink, Closeable {
    private static final ThreadLocal<StringBuilder> LINE = ThreadLocal.withInitial(() -> new StringBuilder(256));

    /** Sees every written row line, under the writer's monitor (file order); must not block. */
    interface Tap {
        /** @param line JSON array plus trailing newline; valid for the call only */
        void row(CharSequence line);

        boolean cancelled();
    }

    private final LabTable table;
    private final BufferedWriter out;
    private final Tap tap;
    private long rows;

    LabWriter(LabTable table, Path file) {
        this(table, file, null);
    }

    LabWriter(LabTable table, Path file, Tap tap) {
        this.table = table;
        this.tap = tap;
        try {
            OutputStream raw = Files.newOutputStream(file);
            GZIPOutputStream gz = new GZIPOutputStream(raw, 1 << 16) {
                {
                    def.setLevel(Deflater.BEST_SPEED);
                }
            };
            this.out = new BufferedWriter(new OutputStreamWriter(gz, StandardCharsets.UTF_8), 1 << 16);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    LabTable table() {
        return table;
    }

    synchronized long rows() {
        return rows;
    }

    @Override
    public void row(Object... values) {
        if (values.length != table.columns().size()) {
            throw new IllegalArgumentException(table.name() + ": " + values.length + " values for "
                    + table.columns().size() + " columns");
        }
        if (tap != null && tap.cancelled()) {
            throw new java.util.concurrent.CancellationException(table.name());
        }
        StringBuilder sb = LINE.get();
        sb.setLength(0);
        sb.append('[');
        for (int i = 0; i < values.length; i++) {
            if (i > 0) {
                sb.append(',');
            }
            append(sb, values[i], table.columns().get(i));
        }
        sb.append("]\n");
        try {
            synchronized (this) {
                out.append(sb);
                rows++;
                if (tap != null) {
                    tap.row(sb);
                }
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private void append(StringBuilder sb, Object v, LabTable.Column col) {
        if (v == null) {
            sb.append("null");
        } else if (col.kind() == LabTable.Kind.STR) {
            if (!(v instanceof CharSequence s)) {
                throw new IllegalArgumentException(table.name() + "." + col.name() + " wants a string: " + v);
            }
            quote(sb, s);
        } else if (v instanceof Boolean b) {
            sb.append(b ? '1' : '0');
        } else if (v instanceof Float f) {
            number(sb, f.isNaN() || f.isInfinite() ? null : Float.toString(f));
        } else if (v instanceof Double d) {
            number(sb, d.isNaN() || d.isInfinite() ? null : Double.toString(d));
        } else if (v instanceof Number n) {
            sb.append(n);
        } else {
            throw new IllegalArgumentException(table.name() + "." + col.name() + " wants a number: " + v);
        }
    }

    private static void number(StringBuilder sb, String s) {
        sb.append(s == null ? "null" : s);
    }

    private static void quote(StringBuilder sb, CharSequence s) {
        sb.append('"');
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '"' -> sb.append("\\\"");
                case '\\' -> sb.append("\\\\");
                case '\n' -> sb.append("\\n");
                case '\r' -> sb.append("\\r");
                case '\t' -> sb.append("\\t");
                default -> {
                    if (c < 0x20) {
                        sb.append(String.format("\\u%04x", (int) c));
                    } else {
                        sb.append(c);
                    }
                }
            }
        }
        sb.append('"');
    }

    @Override
    public synchronized void close() throws IOException {
        out.close();
    }
}
