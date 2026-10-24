package com.wf.wflib.wiki;

import net.minecraft.resources.ResourceLocation;
import org.commonmark.Extension;
import org.commonmark.ext.front.matter.YamlFrontMatterBlock;
import org.commonmark.ext.front.matter.YamlFrontMatterExtension;
import org.commonmark.ext.front.matter.YamlFrontMatterVisitor;
import org.commonmark.ext.gfm.strikethrough.StrikethroughExtension;
import org.commonmark.ext.gfm.tables.TableCell;
import org.commonmark.ext.gfm.tables.TablesExtension;
import org.commonmark.ext.task.list.items.TaskListItemsExtension;
import org.commonmark.node.AbstractVisitor;
import org.commonmark.node.Code;
import org.commonmark.node.CustomNode;
import org.commonmark.node.Document;
import org.commonmark.node.FencedCodeBlock;
import org.commonmark.node.HardLineBreak;
import org.commonmark.node.Heading;
import org.commonmark.node.Node;
import org.commonmark.node.IndentedCodeBlock;
import org.commonmark.node.Paragraph;
import org.commonmark.node.SoftLineBreak;
import org.commonmark.node.Text;
import org.commonmark.parser.Parser;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Markdown (CommonMark + GFM tables / strikethrough / task lists) with YAML front matter:
 * {@code title}, {@code summary}, {@code icon} ({@code ns:item} or {@code ns:path.png}), {@code items}
 * (ids / {@code #tags}), {@code keywords}, {@code priority}, {@code hidden}.
 */
public final class ArticleParser {
    private static final int SUMMARY_MAX = 90;
    private static final List<Extension> EXTENSIONS = List.of(TablesExtension.create(),
            StrikethroughExtension.create(), TaskListItemsExtension.create(), YamlFrontMatterExtension.create());
    private static final Parser PARSER = Parser.builder().extensions(EXTENSIONS).build();

    private ArticleParser() {
    }

    /** @throws IllegalArgumentException on a malformed front matter value. */
    public static Article parse(ResourceLocation id, String markdown) {
        Document document = (Document) PARSER.parse(markdown);
        Map<String, List<String>> meta = Map.of();
        if (document.getFirstChild() instanceof YamlFrontMatterBlock block) {
            YamlFrontMatterVisitor visitor = new YamlFrontMatterVisitor();
            document.accept(visitor);
            meta = visitor.getData();
            block.unlink();
        }
        String title = single(meta, "title");
        if (title == null) {
            title = firstHeading(document);
        }
        if (title == null) {
            String path = id.getPath();
            title = path.substring(path.lastIndexOf('/') + 1);
        }
        String summary = single(meta, "summary");
        if (summary == null) {
            summary = firstParagraph(document);
        }
        List<Article.Binding> bindings = new ArrayList<>();
        for (String value : many(meta, "items")) {
            bindings.add(Article.Binding.parse(value));
        }
        List<String> keywords = new ArrayList<>();
        for (String value : many(meta, "keywords")) {
            keywords.add(value.strip().toLowerCase(Locale.ROOT));
        }
        String priority = single(meta, "priority");
        String hidden = single(meta, "hidden");
        return new Article(id, title, summary, icon(single(meta, "icon"), bindings), List.copyOf(keywords),
                List.copyOf(bindings), priority == null ? 0 : Integer.parseInt(priority.strip()),
                hidden != null && Boolean.parseBoolean(hidden.strip()), document,
                plain(document).toLowerCase(Locale.ROOT));
    }

    /** Explicit {@code icon}, else the first bound item id, else a book. */
    private static Article.Icon icon(String value, List<Article.Binding> bindings) {
        if (value != null) {
            ResourceLocation rl = ResourceLocation.parse(value.strip());
            return rl.getPath().endsWith(".png") ? new Article.Icon(null, rl) : new Article.Icon(rl, null);
        }
        for (Article.Binding binding : bindings) {
            if (!binding.tag()) {
                return new Article.Icon(binding.id(), null);
            }
        }
        return new Article.Icon(ResourceLocation.withDefaultNamespace("book"), null);
    }

    private static String single(Map<String, List<String>> meta, String key) {
        List<String> values = meta.get(key);
        if (values == null || values.isEmpty()) {
            return null;
        }
        String v = values.getFirst().strip();
        return v.isEmpty() ? null : unquote(v);
    }

    /** List value, or one comma-separated / bracketed scalar. */
    private static List<String> many(Map<String, List<String>> meta, String key) {
        List<String> values = meta.get(key);
        if (values == null) {
            return List.of();
        }
        List<String> out = new ArrayList<>();
        for (String value : values) {
            String v = value.strip();
            if (v.startsWith("[") && v.endsWith("]")) {
                v = v.substring(1, v.length() - 1);
            }
            for (String part : v.split(",")) {
                String p = unquote(part.strip());
                if (!p.isEmpty()) {
                    out.add(p);
                }
            }
        }
        return out;
    }

    private static String unquote(String v) {
        if (v.length() >= 2 && (v.charAt(0) == '"' && v.endsWith("\"") || v.charAt(0) == '\'' && v.endsWith("'"))) {
            return v.substring(1, v.length() - 1);
        }
        return v;
    }

    private static String firstHeading(Document document) {
        String[] found = new String[1];
        document.accept(new AbstractVisitor() {
            @Override
            public void visit(Heading heading) {
                if (found[0] == null) {
                    found[0] = plain(heading).strip();
                }
            }
        });
        return found[0] == null || found[0].isEmpty() ? null : found[0];
    }

    private static String firstParagraph(Document document) {
        for (Node n = document.getFirstChild(); n != null; n = n.getNext()) {
            if (n instanceof Paragraph) {
                String text = plain(n).strip().replace('\n', ' ');
                if (!text.isEmpty()) {
                    return text.length() > SUMMARY_MAX ? text.substring(0, SUMMARY_MAX - 3) + "..." : text;
                }
            }
        }
        return "";
    }

    /** Text, code and breaks only: link targets and markup dropped; blocks separated by newlines. */
    public static String plain(Node node) {
        StringBuilder out = new StringBuilder();
        node.accept(new AbstractVisitor() {
            @Override
            public void visit(Text text) {
                out.append(text.getLiteral());
            }

            @Override
            public void visit(Code code) {
                out.append(code.getLiteral());
            }

            @Override
            public void visit(SoftLineBreak softLineBreak) {
                out.append(' ');
            }

            @Override
            public void visit(HardLineBreak hardLineBreak) {
                out.append(' ');
            }

            @Override
            public void visit(FencedCodeBlock block) {
                out.append(block.getLiteral()).append('\n');
            }

            @Override
            public void visit(IndentedCodeBlock block) {
                out.append(block.getLiteral()).append('\n');
            }

            @Override
            public void visit(Paragraph paragraph) {
                visitChildren(paragraph);
                out.append('\n');
            }

            @Override
            public void visit(Heading heading) {
                visitChildren(heading);
                out.append('\n');
            }

            @Override
            public void visit(CustomNode node) {
                visitChildren(node);
                if (node instanceof TableCell) {
                    out.append(' ');
                }
            }
        });
        return out.toString();
    }
}
