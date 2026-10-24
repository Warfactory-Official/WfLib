package com.wf.wflib.wiki;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FormattedCharSequence;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WikiTest {
    private static final ArticleLayout.Metrics METRICS = new ArticleLayout.Metrics() {
        @Override
        public float width(FormattedCharSequence text) {
            int[] n = {0};
            text.accept((i, s, cp) -> {
                n[0]++;
                return true;
            });
            return n[0] * 6;
        }

        @Override
        public int lineHeight() {
            return 9;
        }

        @Override
        public float[] textureSize(ResourceLocation texture) {
            return texture.getPath().endsWith("missing.png") ? null : new float[]{40, 20};
        }
    };

    private static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath("t", path);
    }

    private static Article parse(String path, String markdown) {
        return ArticleParser.parse(id(path), markdown);
    }

    @BeforeEach
    void reset() {
        Wiki.replace(Map.of());
    }

    @Test
    void frontMatter() {
        Article a = parse("a/b", """
                ---
                title: Hello
                summary: "Quoted summary"
                items: [t:gizmo, "#c:ingots"]
                keywords:
                  - Alpha
                  - beta
                priority: 7
                hidden: true
                ---
                # Heading

                Body text.
                """);
        assertEquals("Hello", a.title());
        assertEquals("Quoted summary", a.summary());
        assertEquals(List.of("alpha", "beta"), a.keywords());
        assertEquals(2, a.bindings().size());
        assertFalse(a.bindings().get(0).tag());
        assertTrue(a.bindings().get(1).tag());
        assertEquals(id("gizmo"), a.icon().item());
        assertEquals(7, a.priority());
        assertTrue(a.hidden());
        assertEquals("a", a.folder());
    }

    @Test
    void fallbacks() {
        Article a = parse("x/y", "# First heading\n\nFirst paragraph here.\n");
        assertEquals("First heading", a.title());
        assertEquals("First paragraph here.", a.summary());
        assertEquals("book", a.icon().item().getPath());
        assertEquals("y", parse("x/y", "plain").title());
    }

    @Test
    void pngIcon() {
        Article a = parse("a", "---\nicon: t:textures/gui/x.png\n---\nbody");
        assertNull(a.icon().item());
        assertEquals(id("textures/gui/x.png"), a.icon().texture());
    }

    @Test
    void searchRanksTitleOverBody() {
        Map<ResourceLocation, Article> all = new HashMap<>();
        all.put(id("body"), parse("body", "---\ntitle: Other\n---\nThis mentions drones deep inside."));
        all.put(id("title"), parse("title", "---\ntitle: Drones\n---\nShort."));
        all.put(id("key"), parse("key", "---\ntitle: Misc\nkeywords: [drone]\n---\nShort."));
        all.put(id("hid"), parse("hid", "---\ntitle: Drones hidden\nhidden: true\n---\nx"));
        Wiki.replace(all);
        List<Article> found = Wiki.search("drone");
        assertEquals(List.of(id("title"), id("key"), id("body")), found.stream().map(Article::id).toList());
        assertEquals(3, Wiki.search("").size());
        assertTrue(Wiki.search("drones nothingatall").isEmpty());
    }

    @Test
    void links() {
        Article from = parse("guides/a", "x");
        Map<ResourceLocation, Article> all = new HashMap<>();
        all.put(from.id(), from);
        Article sibling = parse("guides/b", "x");
        Article root = parse("pda", "x");
        all.put(sibling.id(), sibling);
        all.put(root.id(), root);
        Wiki.replace(all);
        assertSame(sibling, Wiki.resolveLink(from, "b.md").article());
        assertSame(sibling, Wiki.resolveLink(from, "b").article());
        assertSame(root, Wiki.resolveLink(from, "../pda.md#apps").article());
        assertEquals("apps", Wiki.resolveLink(from, "../pda.md#apps").anchor());
        assertSame(root, Wiki.resolveLink(from, "/pda").article());
        assertSame(root, Wiki.resolveLink(from, "t:pda").article());
        assertSame(from, Wiki.resolveLink(from, "#top").article());
        assertTrue(Wiki.resolveLink(from, "https://example.com").isExternal());
        Wiki.Target broken = Wiki.resolveLink(from, "nope");
        assertNull(broken.article());
        assertNull(broken.anchor());
        assertFalse(broken.isExternal());
        assertNull(Wiki.resolveLink(from, "../../../x").article());
        assertEquals(id("wiki/guides/img/a.png"), Wiki.resolveAsset(from, "img/a.png"));
        assertEquals(id("wiki/img/a.png"), Wiki.resolveAsset(from, "/img/a.png"));
        assertEquals(ResourceLocation.fromNamespaceAndPath("m", "textures/a.png"), Wiki.resolveAsset(from, "m:textures/a.png"));
    }

    private static void assertSame(Object expected, Object actual) {
        org.junit.jupiter.api.Assertions.assertSame(expected, actual);
    }

    private static ArticleLayout.Page layout(String markdown, float width) {
        Article a = parse("doc", markdown);
        Wiki.replace(Map.of(a.id(), a));
        return ArticleLayout.layout(a, width, METRICS, ArticleLayout.Theme.DEFAULT);
    }

    @Test
    void wrapsWords() {
        ArticleLayout.Page page = layout("aaaa bbbb cccc dddd", 60);
        assertEquals(2, page.texts.size());
        for (ArticleLayout.Span s : page.texts) {
            assertTrue(s.x() + s.w() <= 60.01f, "span within column");
        }
        assertTrue(page.texts.get(1).y() > page.texts.get(0).y());
    }

    @Test
    void longWordSplits() {
        ArticleLayout.Page page = layout("abcdefghijklmnopqrstuvwxyz", 60);
        assertTrue(page.texts.size() >= 3);
        for (ArticleLayout.Span s : page.texts) {
            assertTrue(s.w() <= 60.01f);
        }
    }

    @Test
    void headingAnchorsAndLinks() {
        ArticleLayout.Page page = layout("# Top\n\ntext\n\n## Second Part\n\nsee [top](#top) and [bad](nope)\n", 300);
        assertTrue(page.anchors.containsKey("top"));
        assertTrue(page.anchors.get("second-part") > page.anchors.get("top"));
        assertEquals(1, page.hits.size());
        assertEquals("top", page.hits.get(0).target().anchor());
        assertTrue(page.texts.stream().anyMatch(s -> s.underline() && s.argb() == ArticleLayout.Theme.DEFAULT.broken()));
    }

    @Test
    void taskListCheckboxes() {
        ArticleLayout.Page page = layout("- [x] done\n- [ ] todo\n- plain\n", 300);
        long accent = page.boxes.stream().filter(b -> b.argb() == ArticleLayout.Theme.DEFAULT.accent()).count();
        long edges = page.boxes.stream().filter(b -> b.argb() == ArticleLayout.Theme.DEFAULT.edge()).count();
        assertEquals(1, accent, "one checked box");
        assertEquals(2, edges, "two checkbox frames");
    }

    @Test
    void imagesAndTables() {
        ArticleLayout.Page page = layout("""
                ![i](img/a.png) ![x](item:minecraft:diamond) ![gone](missing.png)

                | a | b |
                |---|--:|
                | 1 | 2 |

                - [x] done
                - [ ] todo
                """, 300);
        assertEquals(2, page.pics.size());
        assertNotNull(page.pics.get(0).texture());
        assertNotNull(page.pics.get(1).item());
        assertTrue(page.texts.stream().anyMatch(s -> s.argb() == ArticleLayout.Theme.DEFAULT.broken()), "missing image => red label");
        assertTrue(page.boxes.size() > 6, "table rules + checkboxes");
        assertTrue(page.height > 40);
    }

    @Test
    void wideImageScalesToColumn() {
        ArticleLayout.Page page = layout("![i](a.png)", 20);
        assertEquals(20, page.pics.get(0).w(), 0.01);
        assertEquals(10, page.pics.get(0).h(), 0.01);
    }
}
