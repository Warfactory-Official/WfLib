package com.wf.wflib.wiki.client;

import com.wf.gemrender.ui.Canvas;
import com.wf.wflib.WFLib;
import com.wf.wflib.pda.client.PdaApp;
import com.wf.wflib.pda.client.PdaApps;
import com.wf.wflib.pda.client.PdaShell;
import com.wf.wflib.ui.Binder;
import com.wf.wflib.ui.UiEval;
import com.wf.wflib.ui.Widget;
import com.wf.wflib.ui.WidgetDef;
import com.wf.wflib.ui.input.Interactive;
import com.wf.wflib.ui.input.TextLine;
import com.wf.wflib.ui.input.UiInput;
import com.wf.wflib.ui.widget.TextField;
import com.wf.wflib.wiki.Article;
import com.wf.wflib.wiki.ArticleLayout;
import com.wf.wflib.wiki.Wiki;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FormattedCharSequence;

import java.util.ArrayDeque;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

/**
 * Searchable article list and markdown reader over {@link Wiki}. State is static (one PDA screen): current article
 * (null = list), back stack, scrolls.
 */
public final class WikiApp implements PdaApp {
    public static final ResourceLocation ID = ResourceLocation.fromNamespaceAndPath(WFLib.MODID, "wiki");

    private static final WikiApp instance = new WikiApp();
    private static final TextLine QUERY = new TextLine(48, WikiApp::submit);

    private static final int PANEL = 0xFF454A45;
    private static final int BACK = 0xFF3A3E3A;
    private static final int EDGE = 0xFF787F76;
    private static final int HOVER = 0xFF545B53;
    private static final int PRESS = 0xFF626B5F;
    private static final int TEXT = 0xFFD2D6CC;
    private static final int DIM = 0xFF8D9488;
    private static final int ACCENT = 0xFFA9D46C;
    private static final int LINK_HOVER = 0x507FC4E8;

    private static final float MARGIN = 12;
    private static final float ROW = 22;
    private static final float ROW_GAP = 1;
    private static final float ICON = 16;
    private static final float FIELD = 13;
    private static final float BAR = 2;
    private static final float WHEEL_ROWS = 2;
    private static final float WHEEL_ARTICLE = 24;

    private record Frame(Article article, float scroll) {
    }

    private record Row(String title, String summary) {
    }

    private static final ArrayDeque<Frame> HISTORY = new ArrayDeque<>();
    private static final Map<Article, Row> ROWS = new IdentityHashMap<>();
    private static Article current;
    private static float articleScroll;
    private static float listScroll;
    private static String anchor;
    private static String query = "";
    private static List<Article> results = List.of();
    private static boolean resultsStale = true;

    private static Article laidOut;
    private static float laidOutWidth;
    private static int laidOutFont;
    private static int rowsFont = -1;
    private static float rowsWidth = -1;
    private static ArticleLayout.Page page;

    private WikiApp() {
    }

    public static void register() {
        PdaApps.register(instance);
    }

    public static WikiApp instance() {
        return instance;
    }

    /** Open {@code article} at {@code anchor} (null = top); the current page goes on the back stack. */
    public static void show(Article article, String anchor) {
        if (article != current) {
            HISTORY.push(new Frame(current, articleScroll));
            current = article;
            articleScroll = 0;
        }
        WikiApp.anchor = anchor == null || anchor.isEmpty() ? null : anchor;
        if (WikiApp.anchor == null && article == current) {
            articleScroll = 0;
        }
    }

    static void reloaded() {
        current = null;
        HISTORY.clear();
        ROWS.clear();
        page = null;
        laidOut = null;
        anchor = null;
        resultsStale = true;
    }

    private static void back() {
        Frame frame = HISTORY.poll();
        if (frame != null) {
            current = frame.article;
            articleScroll = frame.scroll;
            anchor = null;
        }
    }

    private static void submit(String line) {
        QUERY.set(line);
        List<Article> found = Wiki.search(line);
        if (!found.isEmpty()) {
            show(found.getFirst(), null);
        }
    }

    private static void follow(Wiki.Target target) {
        if (target.article() != null) {
            show(target.article(), target.anchor());
        }
    }

    @Override
    public ResourceLocation id() {
        return ID;
    }

    @Override
    public Component title() {
        return Component.translatable("wflib.pda.app.wiki");
    }

    @Override
    public WidgetDef build() {
        return new Def();
    }

    private record Def() implements WidgetDef {
        @Override
        public Widget create(Binder binder) {
            float inner = PdaShell.APP_WIDTH - 2 * MARGIN;
            return new Node(binder.child(new TextField(QUERY, "SEARCH> ", inner, FIELD, 0xFF101210, EDGE, ACCENT, TEXT,
                    DIM)));
        }
    }

    private static final class Click implements Interactive {
        private final Runnable action;

        Click(Runnable action) {
            this.action = action;
        }

        @Override
        public void release(Object item, float x, float y, boolean inside) {
            if (inside) {
                action.run();
            }
        }
    }

    private static final Interactive BACK_BUTTON = new Click(WikiApp::back);
    private static final Interactive INDEX_BUTTON = new Click(() -> {
        if (current != null) {
            show(null, null);
        }
    });

    private static final Interactive RESULT = new Interactive() {
        @Override
        public void release(Object item, float x, float y, boolean inside) {
            if (inside) {
                show((Article) item, null);
            }
        }
    };

    private static final Interactive LINK = new Interactive() {
        @Override
        public void release(Object item, float x, float y, boolean inside) {
            if (inside) {
                follow((Wiki.Target) item);
            }
        }
    };

    private static final Interactive LIST_WHEEL = new Interactive() {
        @Override
        public boolean wheel(Object item, float amount) {
            listScroll -= amount * (ROW + ROW_GAP) * WHEEL_ROWS;
            return true;
        }
    };

    private static final Interactive ARTICLE_WHEEL = new Interactive() {
        @Override
        public boolean wheel(Object item, float amount) {
            articleScroll -= amount * WHEEL_ARTICLE;
            return true;
        }
    };

    private static final class Node extends Widget {
        private final Widget search;

        Node(Widget search) {
            this.search = search;
        }

        @Override
        public void measure(UiEval ev) {
            width = PdaShell.APP_WIDTH;
            height = PdaShell.APP_HEIGHT;
            search.measure(ev);
        }

        @Override
        public void draw(UiEval ev, float x, float y, float w, float h) {
            if (current != null && Wiki.get(current.id()) != current) {
                reloaded();
            }
            Canvas canvas = ev.canvas;
            canvas.fill(x + 8, y + 8, x + width - 8, y + height - 8, PANEL);
            canvas.frame(x + 8, y + 8, x + width - 8, y + height - 8, 1, EDGE);
            float left = x + MARGIN;
            float top = y + MARGIN;
            float inner = width - 2 * MARGIN;
            float bottom = y + height - MARGIN;
            if (current == null) {
                list(ev, left, top, inner, bottom);
            } else {
                article(ev, left, top, inner, bottom);
            }
        }

        private void list(UiEval ev, float left, float top, float inner, float bottom) {
            Canvas canvas = ev.canvas;
            UiInput input = ev.input;
            if (resultsStale || !query.contentEquals(QUERY.text())) {
                query = QUERY.text().toString();
                results = Wiki.search(query);
                resultsStale = false;
                listScroll = 0;
            }
            search.draw(ev, left, top, inner, FIELD);
            float ly = top + FIELD + 4;
            float lh = bottom - ly;
            float stride = ROW + ROW_GAP;
            int count = results.size();
            float content = count * stride - ROW_GAP;
            listScroll = Math.max(0, Math.min(listScroll, content - lh));
            if (count == 0) {
                canvas.text(Wiki.size() == 0 ? "NO ARTICLES LOADED" : "NO MATCHES", left + 2, ly + 3, DIM, false);
                return;
            }
            if (input != null) {
                input.hit(left, ly, left + inner, ly + lh, LIST_WHEEL, null);
            }
            canvas.pushClip(left, ly, left + inner, ly + lh);
            if (input != null) {
                input.pushClip(left, ly, left + inner, ly + lh);
            }
            boolean bar = content > lh;
            float rowW = bar ? inner - BAR - 1 : inner;
            if (rowsFont != canvas.fontVersion() || rowsWidth != rowW) {
                ROWS.clear(); rowsFont = canvas.fontVersion(); rowsWidth = rowW;
            }
            for (int i = (int) (listScroll / stride); i < count; i++) {
                float ry = ly + i * stride - listScroll;
                if (ry >= ly + lh) {
                    break;
                }
                Article a = results.get(i);
                boolean hover = input != null && input.hovered(RESULT, a);
                canvas.fill(left, ry, left + rowW, ry + ROW, hover ? input.captured(RESULT, a) ? PRESS : HOVER : BACK);
                WikiIcons.draw(canvas, a.icon(), left + 3, ry + 3, ICON);
                Row row = ROWS.get(a);
                if (row == null) {
                    row = row(canvas, a, rowW - 28);
                    ROWS.put(a, row);
                }
                canvas.text(row.title, left + 24, ry + 3, ACCENT, false);
                canvas.text(row.summary, left + 24, ry + 12, DIM, false);
                if ((!row.title.equals(a.title()) || !row.summary.equals(a.summary())) && input != null
                        && input.over(left + 24, ry, left + rowW, ry + ROW))
                    com.wf.wflib.pda.client.theme.PdaPopup.show("ARTICLE", "", a.title() + "\n" + a.summary());
                if (input != null) {
                    input.hit(left, ry, left + rowW, ry + ROW, RESULT, a);
                }
            }
            if (bar) {
                float size = Math.max(4, lh * lh / content);
                float at = ly + (lh - size) * listScroll / (content - lh);
                canvas.fill(left + inner - BAR, at, left + inner, at + size, EDGE);
            }
            canvas.popClip();
            if (input != null) {
                input.popClip();
            }
        }

        private static Row row(Canvas canvas, Article a, float width) {
            return new Row(clip(canvas, a.title(), width), clip(canvas, a.summary(), width));
        }

        private static String clip(Canvas canvas, String s, float width) {
            if (canvas.width(s) <= width) {
                return s;
            }
            int end = s.length();
            while (end > 0 && canvas.width(s, 0, end) + canvas.width("...") > width) {
                end--;
            }
            return s.substring(0, end).stripTrailing() + "...";
        }

        private void article(UiEval ev, float left, float top, float inner, float bottom) {
            Canvas canvas = ev.canvas;
            UiInput input = ev.input;
            boolean canBack = !HISTORY.isEmpty();
            float bx = left;
            bx += button(ev, bx, top, 16, "<", BACK_BUTTON, canBack) + 3;
            bx += button(ev, bx, top, 44, "INDEX", INDEX_BUTTON, true) + 6;
            canvas.pushClip(bx, top, left + inner, top + FIELD);
            com.wf.wflib.pda.client.PdaText.line(ev, current.title(), bx, top + 3, left + inner - bx, ACCENT);
            canvas.popClip();

            float by = top + FIELD + 4;
            float bh = bottom - by;
            float colW = inner - BAR - 3;
            if (current != laidOut || colW != laidOutWidth || canvas.fontVersion() != laidOutFont) {
                page = ArticleLayout.layout(current, colW, new CanvasMetrics(canvas), ArticleLayout.Theme.DEFAULT);
                laidOut = current;
                laidOutWidth = colW;
                laidOutFont = canvas.fontVersion();
            }
            float max = Math.max(0, page.height - bh);
            if (anchor != null) {
                Float at = page.anchors.get(anchor);
                articleScroll = at == null ? 0 : at;
                anchor = null;
            }
            articleScroll = Math.max(0, Math.min(articleScroll, max));
            if (input != null) {
                input.hit(left, by, left + inner, by + bh, ARTICLE_WHEEL, null);
            }
            canvas.pushClip(left, by, left + inner, by + bh);
            if (input != null) {
                input.pushClip(left, by, left + inner, by + bh);
            }
            float oy = by - articleScroll;
            float lo = articleScroll - 64;
            float hi = articleScroll + bh;
            for (int i = 0, n = page.boxes.size(); i < n; i++) {
                ArticleLayout.Box b = page.boxes.get(i);
                if (b.y1() >= lo && b.y0() <= hi) {
                    canvas.fill(left + b.x0(), oy + b.y0(), left + b.x1(), oy + b.y1(), b.argb());
                }
            }
            for (int i = 0, n = page.hits.size(); i < n; i++) {
                ArticleLayout.Hit h = page.hits.get(i);
                if (h.y1() >= lo && h.y0() <= hi) {
                    if (input != null) {
                        if (input.hovered(LINK, h.target())) {
                            canvas.fill(left + h.x0(), oy + h.y0(), left + h.x1(), oy + h.y1(), LINK_HOVER);
                        }
                        input.hit(left + h.x0(), oy + h.y0(), left + h.x1(), oy + h.y1(), LINK, h.target());
                    }
                }
            }
            for (int i = 0, n = page.pics.size(); i < n; i++) {
                ArticleLayout.Pic p = page.pics.get(i);
                if (p.y() + p.h() >= lo && p.y() <= hi) {
                    if (p.item() != null) {
                        WikiIcons.item(canvas, p.item(), left + p.x(), oy + p.y(), p.w());
                    } else {
                        canvas.sprite(p.texture(), left + p.x(), oy + p.y(), left + p.x() + p.w(), oy + p.y() + p.h(), -1);
                    }
                }
            }
            int lineHeight = canvas.lineHeight();
            for (int i = 0, n = page.texts.size(); i < n; i++) {
                ArticleLayout.Span s = page.texts.get(i);
                float sh = lineHeight * s.scale();
                if (s.y() + sh < lo || s.y() > hi) {
                    continue;
                }
                float sx = left + s.x();
                float sy = oy + s.y();
                if (s.scale() == 1) {
                    canvas.text(s.sequence(), sx, sy, s.argb(), false);
                } else {
                    canvas.push().translate(sx, sy).scale(s.scale());
                    canvas.text(s.sequence(), 0, 0, s.argb(), false);
                    canvas.pop();
                }
                if (s.underline()) {
                    canvas.fill(sx, sy + sh - 1, sx + s.w(), sy + sh, s.argb());
                }
                if (s.strike()) {
                    canvas.fill(sx, sy + sh / 2 - 0.5f, sx + s.w(), sy + sh / 2 + 0.5f, s.argb());
                }
            }
            if (max > 0) {
                float size = Math.max(4, bh * bh / page.height);
                float at = by + (bh - size) * articleScroll / max;
                canvas.fill(left + inner - BAR, at, left + inner, at + size, EDGE);
            }
            canvas.popClip();
            if (input != null) {
                input.popClip();
            }
        }

        private static float button(UiEval ev, float x, float y, float w, String label, Interactive owner,
                                    boolean enabled) {
            Canvas canvas = ev.canvas;
            UiInput input = ev.input;
            int back = BACK;
            if (enabled && input != null) {
                input.hit(x, y, x + w, y + FIELD, owner, null);
                if (input.hovered(owner, null)) {
                    back = input.captured(owner, null) ? PRESS : HOVER;
                }
            }
            canvas.fill(x, y, x + w, y + FIELD, back);
            canvas.frame(x, y, x + w, y + FIELD, 1, EDGE);
            canvas.textCentred(label, x + w / 2, y + 3, enabled ? TEXT : DIM, false);
            return w;
        }
    }

    private record CanvasMetrics(Canvas canvas) implements ArticleLayout.Metrics {
        @Override
        public float width(FormattedCharSequence text) {
            return canvas.width(text);
        }

        @Override
        public int lineHeight() {
            return canvas.lineHeight();
        }

        @Override
        public float[] textureSize(ResourceLocation texture) {
            return WikiIcons.size(texture);
        }
    }
}
