package com.wf.wflib.wiki;

import net.minecraft.network.chat.Style;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FormattedCharSequence;
import org.commonmark.ext.gfm.strikethrough.Strikethrough;
import org.commonmark.ext.gfm.tables.TableBlock;
import org.commonmark.ext.gfm.tables.TableBody;
import org.commonmark.ext.gfm.tables.TableCell;
import org.commonmark.ext.gfm.tables.TableHead;
import org.commonmark.ext.gfm.tables.TableRow;
import org.commonmark.ext.task.list.items.TaskListItemMarker;
import org.commonmark.node.BlockQuote;
import org.commonmark.node.BulletList;
import org.commonmark.node.Code;
import org.commonmark.node.Emphasis;
import org.commonmark.node.FencedCodeBlock;
import org.commonmark.node.HardLineBreak;
import org.commonmark.node.Heading;
import org.commonmark.node.HtmlInline;
import org.commonmark.node.Image;
import org.commonmark.node.IndentedCodeBlock;
import org.commonmark.node.Link;
import org.commonmark.node.ListBlock;
import org.commonmark.node.ListItem;
import org.commonmark.node.Node;
import org.commonmark.node.OrderedList;
import org.commonmark.node.Paragraph;
import org.commonmark.node.SoftLineBreak;
import org.commonmark.node.StrongEmphasis;
import org.commonmark.node.Text;
import org.commonmark.node.ThematicBreak;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import com.wf.wflib.wiki.api.Article;
import com.wf.wflib.wiki.api.Wiki;

/**
 * Article body -> positioned draw lists for a {@code width}-unit column. Pure: text widths and image sizes come
 * from {@link Metrics}. Draw order: boxes, pics, texts.
 */
public final class ArticleLayout {
    public interface Metrics {
        float width(FormattedCharSequence text);

        int lineHeight();

        /** Texture size in px, or null if it does not load. */
        float[] textureSize(ResourceLocation texture);
    }

    /** ARGB. */
    public record Theme(int text, int dim, int accent, int link, int broken, int edge, int codeText, int codeBack,
                        int headBack) {
        public static final Theme DEFAULT = new Theme(0xFFD2D6CC, 0xFF8D9488, 0xFFA9D46C, 0xFF7FC4E8, 0xFFD0584A,
                0xFF787F76, 0xFFE3C98A, 0xFF1A1D1A, 0xFF2C2F2C);
    }

    public record Box(float x0, float y0, float x1, float y1, int argb) {
    }

    /** {@code item} xor {@code texture}. */
    public record Pic(float x, float y, float w, float h, ResourceLocation item, ResourceLocation texture) {
    }

    public record Span(float x, float y, float w, float scale, FormattedCharSequence sequence, int argb,
                       boolean underline, boolean strike) {
    }

    public record Hit(float x0, float y0, float x1, float y1, Wiki.Target target) {
    }

    public static final class Page {
        public final List<Box> boxes = new ArrayList<>();
        public final List<Pic> pics = new ArrayList<>();
        public final List<Span> texts = new ArrayList<>();
        public final List<Hit> hits = new ArrayList<>();
        public final Map<String, Float> anchors = new HashMap<>();
        public float height;

        void add(Page other, float dx, float dy) {
            for (Box b : other.boxes) {
                boxes.add(new Box(b.x0 + dx, b.y0 + dy, b.x1 + dx, b.y1 + dy, b.argb));
            }
            for (Pic p : other.pics) {
                pics.add(new Pic(p.x + dx, p.y + dy, p.w, p.h, p.item, p.texture));
            }
            for (Span t : other.texts) {
                texts.add(new Span(t.x + dx, t.y + dy, t.w, t.scale, t.sequence, t.argb, t.underline, t.strike));
            }
            for (Hit h : other.hits) {
                hits.add(new Hit(h.x0 + dx, h.y0 + dy, h.x1 + dx, h.y1 + dy, h.target));
            }
        }
    }

    private static final float GAP = 5;
    private static final float TIGHT_GAP = 1;
    private static final float LEADING = 2;
    private static final float ITEM_ICON = 16;
    private static final float[] HEADING_SCALE = {1.6f, 1.35f, 1.15f, 1, 1, 1};
    private static final float CELL_PAD = 3;

    private ArticleLayout() {
    }

    public static Page layout(Article article, float width, Metrics metrics, Theme theme) {
        Run run = new Run(article, metrics, theme);
        run.page.height = run.blocks(article.document().getFirstChild(), 0, 0, width, theme.text, false);
        return run.page;
    }

    /** Inline state; equal states may share one text run. */
    private record State(boolean bold, boolean italic, boolean strike, boolean code, Wiki.Target link, int colour) {
        Style style() {
            return Style.EMPTY.withBold(bold).withItalic(italic);
        }
    }

    private enum Kind {WORD, PIC, BREAK}

    private static final class Atom {
        final Kind kind;
        final String raw;
        final State state;
        final boolean spaceBefore;
        float w;
        float h;
        ResourceLocation item;
        ResourceLocation texture;

        Atom(Kind kind, String raw, State state, boolean spaceBefore) {
            this.kind = kind;
            this.raw = raw;
            this.state = state;
            this.spaceBefore = spaceBefore;
        }
    }

    private static final class Run {
        final Article article;
        final Metrics m;
        final Theme t;
        Page page = new Page();
        final Map<String, Integer> slugs = new HashMap<>();

        Run(Article article, Metrics m, Theme t) {
            this.article = article;
            this.m = m;
            this.t = t;
        }

        // ------------------------------------------------------------ blocks

        /** @return y after the last block, gap included. */
        float blocks(Node first, float x, float y, float w, int colour, boolean tight) {
            float gap = tight ? TIGHT_GAP : GAP;
            for (Node n = first; n != null; n = n.getNext()) {
                if (n instanceof Paragraph) {
                    y = flow(n, x, y, w, 1, colour, 0, false) + gap;
                } else if (n instanceof Heading h) {
                    y = heading(h, x, y, w) + gap;
                } else if (n instanceof BlockQuote) {
                    float end = blocks(n.getFirstChild(), x + 8, y, w - 8, t.dim, false);
                    page.boxes.add(new Box(x, y, x + 2, end - GAP, t.edge));
                    y = end;
                } else if (n instanceof ListBlock list) {
                    y = list(list, x, y, w, colour) + gap;
                } else if (n instanceof FencedCodeBlock code) {
                    y = code(code.getLiteral(), x, y, w) + gap;
                } else if (n instanceof IndentedCodeBlock code) {
                    y = code(code.getLiteral(), x, y, w) + gap;
                } else if (n instanceof ThematicBreak) {
                    page.boxes.add(new Box(x, y + 3, x + w, y + 4, t.edge));
                    y += 8 + gap - 1;
                } else if (n instanceof TableBlock table) {
                    y = table(table, x, y, w, colour) + gap;
                }
            }
            return y;
        }

        float heading(Heading h, float x, float y, float w) {
            int level = Math.min(6, Math.max(1, h.getLevel()));
            float scale = HEADING_SCALE[level - 1];
            if (y > 0) {
                y += level <= 2 ? 4 : 2;
            }
            page.anchors.putIfAbsent(slug(ArticleParser.plain(h)), y);
            float end = flow(h, x, y, w, scale, level <= 2 ? t.accent : t.text, 0, true);
            if (level <= 2) {
                page.boxes.add(new Box(x, end - 1, x + w, end, t.edge));
                end += 2;
            }
            return end;
        }

        String slug(String text) {
            StringBuilder b = new StringBuilder();
            for (char c : text.strip().toLowerCase(Locale.ROOT).toCharArray()) {
                if (Character.isLetterOrDigit(c) || c == '_' || c == '-') {
                    b.append(c);
                } else if (c == ' ') {
                    b.append('-');
                }
            }
            String base = b.toString();
            int seen = slugs.merge(base, 1, Integer::sum);
            return seen == 1 ? base : base + "-" + (seen - 1);
        }

        float list(ListBlock list, float x, float y, float w, int colour) {
            boolean ordered = list instanceof OrderedList;
            int number = ordered ? ((OrderedList) list).getMarkerStartNumber() == null ? 1
                    : ((OrderedList) list).getMarkerStartNumber() : 0;
            int count = 0;
            for (Node n = list.getFirstChild(); n != null; n = n.getNext()) {
                count++;
            }
            float indent = 12;
            if (ordered) {
                indent = m.width(FormattedCharSequence.forward((number + count - 1) + ".", Style.EMPTY)) + 5;
            }
            boolean tight = list.isTight();
            int lh = m.lineHeight();
            for (Node item = list.getFirstChild(); item != null; item = item.getNext()) {
                if (!(item instanceof ListItem)) {
                    continue;
                }
                Node first = item.getFirstChild();
                TaskListItemMarker task = first instanceof TaskListItemMarker mk ? mk : null;
                if (ordered) {
                    String marker = number++ + ".";
                    page.texts.add(new Span(x, y + 1, m.width(FormattedCharSequence.forward(marker, Style.EMPTY)), 1,
                            FormattedCharSequence.forward(marker, Style.EMPTY), t.dim, false, false));
                } else if (task != null) {
                    float side = lh - 1;
                    page.boxes.add(new Box(x, y + 1, x + side, y + 1 + side, t.edge));
                    page.boxes.add(new Box(x + 1, y + 2, x + side - 1, y + side, t.codeBack));
                    if (task.isChecked()) {
                        page.boxes.add(new Box(x + 2.5f, y + 3.5f, x + side - 2.5f, y + side - 1.5f, t.accent));
                    }
                } else {
                    float cy = y + 1 + lh / 2f;
                    page.boxes.add(new Box(x + 3, cy - 1.5f, x + 6, cy + 1.5f, t.dim));
                }
                float shift = task != null && !ordered ? lh + 3 : indent;
                y = blocks(task != null ? task.getNext() : first, x + shift, y, w - shift, colour, tight);
            }
            return y;
        }

        float code(String literal, float x, float y, float w) {
            String text = literal.stripTrailing().replace("\t", "    ");
            float inner = w - 8;
            float top = y;
            y += 3;
            for (String line : text.split("\n", -1)) {
                int start = 0;
                do {
                    int end = start;
                    while (end < line.length() && (end == start
                            || m.width(FormattedCharSequence.forward(line.substring(start, end + 1), Style.EMPTY)) <= inner)) {
                        end++;
                    }
                    String chunk = line.substring(start, end);
                    page.texts.add(new Span(x + 4, y, m.width(FormattedCharSequence.forward(chunk, Style.EMPTY)), 1,
                            FormattedCharSequence.forward(chunk, Style.EMPTY), t.codeText, false, false));
                    y += m.lineHeight() + 1;
                    start = end;
                } while (start < line.length());
            }
            y += 3;
            page.boxes.add(new Box(x, top, x + w, y, t.codeBack));
            page.boxes.add(new Box(x, top, x + 1, y, t.edge));
            return y;
        }

        // ------------------------------------------------------------ tables

        float table(TableBlock table, float x, float y, float w, int colour) {
            List<List<TableCell>> rows = new ArrayList<>();
            int head = 0;
            for (Node section = table.getFirstChild(); section != null; section = section.getNext()) {
                for (Node row = section.getFirstChild(); row instanceof TableRow; row = row.getNext()) {
                    List<TableCell> cells = new ArrayList<>();
                    for (Node cell = row.getFirstChild(); cell instanceof TableCell c; cell = cell.getNext()) {
                        cells.add(c);
                    }
                    rows.add(cells);
                    if (section instanceof TableHead) {
                        head++;
                    }
                }
            }
            int columns = 0;
            for (List<TableCell> row : rows) {
                columns = Math.max(columns, row.size());
            }
            if (columns == 0) {
                return y;
            }
            float[] min = new float[columns];
            float[] max = new float[columns];
            for (int r = 0; r < rows.size(); r++) {
                for (int c = 0; c < rows.get(r).size(); c++) {
                    List<Atom> atoms = collect(rows.get(r).get(c), 1, r < head);
                    float widest = 0;
                    float sum = 0;
                    for (Atom a : atoms) {
                        widest = Math.max(widest, a.w);
                        sum += a.w + (a.spaceBefore && sum > 0 ? space(a) : 0);
                    }
                    min[c] = Math.max(min[c], widest);
                    max[c] = Math.max(max[c], sum);
                }
            }
            float avail = Math.max(columns, w - (columns + 1) - 2 * CELL_PAD * columns);
            float[] col = columnWidths(min, max, avail);
            float[] left = new float[columns];
            float cursor = x + 1;
            for (int c = 0; c < columns; c++) {
                left[c] = cursor;
                cursor += col[c] + 2 * CELL_PAD + 1;
            }
            float right = cursor - 1;
            float top = y;
            List<Float> rules = new ArrayList<>();
            rules.add(y);
            y += 1;
            for (int r = 0; r < rows.size(); r++) {
                Page[] flowed = new Page[columns];
                float[] heights = new float[columns];
                float rowH = m.lineHeight();
                for (int c = 0; c < rows.get(r).size(); c++) {
                    TableCell cell = rows.get(r).get(c);
                    Page saved = page;
                    page = new Page();
                    heights[c] = flow(cell, 0, 0, col[c], 1, colour, align(cell), r < head);
                    flowed[c] = page;
                    page = saved;
                    rowH = Math.max(rowH, heights[c]);
                }
                rowH += 2 * CELL_PAD - LEADING + 1;
                if (r < head) {
                    page.boxes.add(new Box(x, y - 1, right + 1, y + rowH, t.headBack));
                }
                for (int c = 0; c < columns; c++) {
                    if (flowed[c] != null) {
                        page.add(flowed[c], left[c] + CELL_PAD, y + CELL_PAD);
                    }
                }
                y += rowH;
                rules.add(y);
                y += 1;
            }
            for (float rule : rules) {
                page.boxes.add(new Box(x, rule - 1, right + 1, rule, t.edge));
            }
            for (int c = 0; c <= columns; c++) {
                float vx = c == columns ? right : left[c] - 1;
                page.boxes.add(new Box(vx, top, vx + 1, y - 1, t.edge));
            }
            return y - 1;
        }

        static float[] columnWidths(float[] min, float[] max, float avail) {
            int n = min.length;
            float[] out = new float[n];
            float sumMax = 0;
            float sumMin = 0;
            for (int i = 0; i < n; i++) {
                sumMax += max[i];
                sumMin += min[i];
            }
            if (sumMax <= avail) {
                System.arraycopy(max, 0, out, 0, n);
                return out;
            }
            if (sumMin >= avail) {
                for (int i = 0; i < n; i++) {
                    out[i] = sumMin == 0 ? avail / n : min[i] * avail / sumMin;
                }
                return out;
            }
            float room = avail - sumMin;
            float wish = sumMax - sumMin;
            for (int i = 0; i < n; i++) {
                out[i] = min[i] + room * (max[i] - min[i]) / wish;
            }
            return out;
        }

        static float align(TableCell cell) {
            if (cell.getAlignment() == null) {
                return 0;
            }
            return switch (cell.getAlignment()) {
                case CENTER -> 0.5f;
                case RIGHT -> 1;
                default -> 0;
            };
        }

        // ------------------------------------------------------------ inline flow

        /** Lays the inline children of {@code parent} out in {@code w}; @return y after the last line. */
        float flow(Node parent, float x, float y, float w, float scale, int colour, float align, boolean bold) {
            List<Atom> atoms = collect(parent, scale, bold, colour);
            return place(atoms, x, y, w, scale, align);
        }

        private List<Atom> collect(Node parent, float scale, boolean bold) {
            return collect(parent, scale, bold, t.text);
        }

        private List<Atom> collect(Node parent, float scale, boolean bold, int colour) {
            List<Atom> out = new ArrayList<>();
            boolean[] pending = {false};
            walk(parent.getFirstChild(), new State(bold, false, false, false, null, colour), scale, out, pending);
            return out;
        }

        private void walk(Node first, State st, float scale, List<Atom> out, boolean[] pending) {
            for (Node n = first; n != null; n = n.getNext()) {
                if (n instanceof Text text) {
                    words(text.getLiteral(), st, scale, out, pending);
                } else if (n instanceof Code code) {
                    words(code.getLiteral(), new State(st.bold, st.italic, st.strike, true, st.link, st.colour), scale,
                            out, pending);
                } else if (n instanceof SoftLineBreak) {
                    pending[0] = true;
                } else if (n instanceof HardLineBreak) {
                    out.add(new Atom(Kind.BREAK, "", st, false));
                    pending[0] = false;
                } else if (n instanceof HtmlInline html) {
                    String tag = html.getLiteral().replace(" ", "").toLowerCase(Locale.ROOT);
                    if (tag.equals("<br>") || tag.equals("<br/>")) {
                        out.add(new Atom(Kind.BREAK, "", st, false));
                        pending[0] = false;
                    }
                } else if (n instanceof Emphasis) {
                    walk(n.getFirstChild(), new State(st.bold, true, st.strike, st.code, st.link, st.colour), scale, out,
                            pending);
                } else if (n instanceof StrongEmphasis) {
                    walk(n.getFirstChild(), new State(true, st.italic, st.strike, st.code, st.link, st.colour), scale,
                            out, pending);
                } else if (n instanceof Strikethrough) {
                    walk(n.getFirstChild(), new State(st.bold, st.italic, true, st.code, st.link, st.colour), scale,
                            out, pending);
                } else if (n instanceof Link link) {
                    Wiki.Target target = Wiki.resolveLink(article, link.getDestination());
                    walk(n.getFirstChild(), new State(st.bold, st.italic, st.strike, st.code, target, st.colour), scale,
                            out, pending);
                } else if (n instanceof Image image) {
                    image(image, st, scale, out, pending);
                } else if (n.getFirstChild() != null) {
                    walk(n.getFirstChild(), st, scale, out, pending);
                }
            }
        }

        private void words(String literal, State st, float scale, List<Atom> out, boolean[] pending) {
            Style style = st.style();
            int i = 0;
            int len = literal.length();
            while (i < len) {
                if (Character.isWhitespace(literal.charAt(i))) {
                    pending[0] = true;
                    i++;
                    continue;
                }
                int start = i;
                while (i < len && !Character.isWhitespace(literal.charAt(i))) {
                    i++;
                }
                String word = literal.substring(start, i);
                Atom a = new Atom(Kind.WORD, word, st, pending[0] && !out.isEmpty());
                a.w = m.width(FormattedCharSequence.forward(word, style)) * scale;
                a.h = m.lineHeight() * scale;
                out.add(a);
                pending[0] = false;
            }
        }

        private void image(Image image, State st, float scale, List<Atom> out, boolean[] pending) {
            String dest = image.getDestination().strip();
            Atom a = new Atom(Kind.PIC, "", st, pending[0] && !out.isEmpty());
            pending[0] = false;
            if (dest.startsWith("item:")) {
                ResourceLocation item = ResourceLocation.tryParse(dest.substring(5));
                if (item != null) {
                    a.item = item;
                    a.w = ITEM_ICON;
                    a.h = ITEM_ICON;
                    out.add(a);
                    return;
                }
            } else {
                ResourceLocation texture = Wiki.resolveAsset(article, dest);
                float[] size = texture == null ? null : m.textureSize(texture);
                if (size != null) {
                    a.texture = texture;
                    a.w = size[0];
                    a.h = size[1];
                    out.add(a);
                    return;
                }
            }
            String alt = ArticleParser.plain(image).strip();
            String label = "[" + (alt.isEmpty() ? dest : alt) + "]";
            State broken = new State(st.bold, st.italic, st.strike, false, null, t.broken);
            words(label, broken, scale, out, new boolean[]{a.spaceBefore});
        }

        private float space(Atom a) {
            return m.width(FormattedCharSequence.forward(" ", a.state.style()));
        }

        private float place(List<Atom> atoms, float x, float y, float w, float scale, float align) {
            List<Atom> line = new ArrayList<>();
            List<Float> xs = new ArrayList<>();
            float[] cursor = {0};
            float lh = m.lineHeight() * scale;
            for (int i = 0; i < atoms.size(); i++) {
                Atom a = atoms.get(i);
                if (a.kind == Kind.BREAK) {
                    y = flush(line, xs, x, y, w, scale, align, lh);
                    cursor[0] = 0;
                    continue;
                }
                if (a.kind == Kind.PIC && a.w > w) {
                    a.h = a.h * w / a.w;
                    a.w = w;
                }
                if (a.kind == Kind.WORD && a.w > w) {
                    if (!line.isEmpty()) {
                        y = flush(line, xs, x, y, w, scale, align, lh);
                        cursor[0] = 0;
                    }
                    for (Atom piece : split(a, w, scale)) {
                        if (cursor[0] > 0) {
                            y = flush(line, xs, x, y, w, scale, align, lh);
                            cursor[0] = 0;
                        }
                        line.add(piece);
                        xs.add(0f);
                        cursor[0] = piece.w;
                    }
                    continue;
                }
                float gap = a.spaceBefore && !line.isEmpty() ? space(a) * scale : 0;
                if (!line.isEmpty() && cursor[0] + gap + a.w > w) {
                    y = flush(line, xs, x, y, w, scale, align, lh);
                    cursor[0] = 0;
                    gap = 0;
                }
                line.add(a);
                xs.add(cursor[0] + gap);
                cursor[0] += gap + a.w;
            }
            if (!line.isEmpty() || y == 0 || atoms.isEmpty()) {
                y = flush(line, xs, x, y, w, scale, align, lh);
            }
            return y;
        }

        /** Word cut into pieces no wider than {@code w}; no spaces between pieces. */
        private List<Atom> split(Atom a, float w, float scale) {
            List<Atom> pieces = new ArrayList<>();
            Style style = a.state.style();
            int start = 0;
            while (start < a.raw.length()) {
                int end = start + 1;
                while (end < a.raw.length()
                        && m.width(FormattedCharSequence.forward(a.raw.substring(start, end + 1), style)) * scale <= w) {
                    end++;
                }
                Atom piece = new Atom(Kind.WORD, a.raw.substring(start, end), a.state, false);
                piece.w = m.width(FormattedCharSequence.forward(piece.raw, style)) * scale;
                piece.h = a.h;
                pieces.add(piece);
                start = end;
            }
            return pieces;
        }

        private float flush(List<Atom> line, List<Float> xs, float x, float y, float w, float scale, float align,
                            float lh) {
            float height = lh;
            float lineW = 0;
            for (int i = 0; i < line.size(); i++) {
                height = Math.max(height, line.get(i).h);
                lineW = Math.max(lineW, xs.get(i) + line.get(i).w);
            }
            float offset = x + (w - lineW) * align;
            int i = 0;
            while (i < line.size()) {
                Atom a = line.get(i);
                switch (a.kind) {
                    case WORD -> i = run(line, xs, i, offset, y, height, lh, scale);
                    case PIC -> {
                        float py = y + (height - a.h) / 2;
                        page.pics.add(new Pic(offset + xs.get(i), py, a.w, a.h, a.item, a.texture));
                        link(a.state, offset + xs.get(i), y, a.w, height);
                        i++;
                    }
                    default -> i++;
                }
            }
            line.clear();
            xs.clear();
            return y + height + LEADING;
        }

        /** Merges the consecutive words from {@code from} sharing one {@link State} into one text op. */
        private int run(List<Atom> line, List<Float> xs, int from, float offset, float y, float height, float lh,
                        float scale) {
            Atom first = line.get(from);
            StringBuilder joined = new StringBuilder(first.raw);
            int end = from + 1;
            while (end < line.size() && line.get(end).kind == Kind.WORD && line.get(end).state.equals(first.state)) {
                Atom next = line.get(end);
                float gap = xs.get(end) - (xs.get(end - 1) + line.get(end - 1).w);
                if (gap > 0.01f) {
                    joined.append(' ');
                }
                joined.append(next.raw);
                end++;
            }
            Atom last = line.get(end - 1);
            float x0 = offset + xs.get(from);
            float width = xs.get(end - 1) + last.w - xs.get(from);
            float ty = y + (height - lh) / 2;
            State st = first.state;
            int argb = st.code ? t.codeText : st.colour;
            boolean underline = false;
            if (st.link != null) {
                boolean live = st.link.article() != null || st.link.anchor() != null && st.link.external() == null;
                argb = st.link.isExternal() ? t.dim : live ? t.link : t.broken;
                underline = true;
                if (live) {
                    link(st, x0, y, width, height);
                }
            }
            if (st.code) {
                page.boxes.add(new Box(x0 - 1, ty, x0 + width + 1, ty + lh, t.codeBack));
            }
            page.texts.add(new Span(x0, ty, width, scale, FormattedCharSequence.forward(joined.toString(), st.style()),
                    argb, underline, st.strike));
            return end;
        }

        private void link(State st, float x, float y, float w, float h) {
            Wiki.Target target = st.link;
            if (target != null && !target.isExternal() && (target.article() != null || target.anchor() != null)) {
                page.hits.add(new Hit(x, y, x + w, y + h, target));
            }
        }
    }
}
