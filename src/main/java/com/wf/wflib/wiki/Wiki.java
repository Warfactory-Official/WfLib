package com.wf.wflib.wiki;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Loaded articles (client resources, replaced whole on reload), search, item bindings and link resolution. */
public final class Wiki {
    /** Where a link leads; {@code article} null = external or same-article anchor. */
    public record Target(Article article, String anchor, String external) {
        public boolean isExternal() {
            return external != null;
        }
    }

    private static final Article NONE = new Article(ResourceLocation.withDefaultNamespace("none"), "", "", null,
            List.of(), List.of(), 0, true, null, "");
    private static final Comparator<Article> BY_PRIORITY = Comparator.comparingInt(Article::priority).reversed()
            .thenComparing(a -> a.title().toLowerCase(Locale.ROOT)).thenComparing(Article::id);

    private static volatile Map<ResourceLocation, Article> articles = Map.of();
    private static volatile List<Article> ordered = List.of();
    private static volatile List<Article> listed = List.of();
    private static final Map<Item, Article> BOUND = new HashMap<>();

    private Wiki() {
    }

    /** Reload: replaces every article. */
    public static synchronized void replace(Map<ResourceLocation, Article> loaded) {
        List<Article> sorted = new ArrayList<>(loaded.values());
        sorted.sort(BY_PRIORITY);
        articles = Map.copyOf(loaded);
        ordered = List.copyOf(sorted);
        listed = List.copyOf(sorted.stream().filter(a -> !a.hidden()).toList());
        invalidateBindings();
    }

    /** Item tags changed (world join / datapack reload). */
    public static synchronized void invalidateBindings() {
        BOUND.clear();
    }

    public static Article get(ResourceLocation id) {
        return articles.get(id);
    }

    public static int size() {
        return articles.size();
    }

    /** Non-hidden articles, priority then title. */
    public static List<Article> all() {
        return listed;
    }

    /** Article bound to {@code stack}'s item (highest priority), or null. */
    public static Article forStack(ItemStack stack) {
        if (stack.isEmpty()) {
            return null;
        }
        Item item = stack.getItem();
        synchronized (Wiki.class) {
            Article found = BOUND.get(item);
            if (found == null) {
                found = NONE;
                search:
                for (Article article : ordered) {
                    for (Article.Binding binding : article.bindings()) {
                        if (binding.matches(item)) {
                            found = article;
                            break search;
                        }
                    }
                }
                BOUND.put(item, found);
            }
            return found == NONE ? null : found;
        }
    }

    /**
     * Whitespace-separated terms, all required. Per term the best of: title prefix, title word start, title
     * substring, keyword, summary, body. Empty query => {@link #all}.
     */
    public static List<Article> search(String query) {
        String[] terms = query.toLowerCase(Locale.ROOT).trim().split("\\s+");
        if (terms.length == 1 && terms[0].isEmpty()) {
            return listed;
        }
        record Hit(Article article, int score) {
        }
        List<Hit> hits = new ArrayList<>();
        for (Article article : listed) {
            int total = 0;
            for (String term : terms) {
                int score = score(article, term);
                if (score == 0) {
                    total = 0;
                    break;
                }
                total += score;
            }
            if (total > 0) {
                hits.add(new Hit(article, total));
            }
        }
        hits.sort(Comparator.comparingInt(Hit::score).reversed().thenComparing(h -> h.article(), BY_PRIORITY));
        List<Article> out = new ArrayList<>(hits.size());
        for (Hit hit : hits) {
            out.add(hit.article());
        }
        return out;
    }

    private static int score(Article article, String term) {
        String title = article.title().toLowerCase(Locale.ROOT);
        if (title.startsWith(term)) {
            return 100;
        }
        if (title.contains(" " + term)) {
            return 80;
        }
        if (title.contains(term)) {
            return 60;
        }
        for (String keyword : article.keywords()) {
            if (keyword.equals(term)) {
                return 50;
            }
            if (keyword.contains(term)) {
                return 40;
            }
        }
        if (article.summary().toLowerCase(Locale.ROOT).contains(term)) {
            return 20;
        }
        return article.searchText().contains(term) ? 10 : 0;
    }

    /**
     * {@code dest} of a markdown link written in {@code from}: {@code http(s)://} / {@code mailto:} external;
     * {@code #anchor} same article; {@code ns:path} absolute; {@code /path} namespace root; else relative to
     * {@code from}'s folder ({@code ../} allowed); a trailing {@code .md} is optional. Unknown article => null
     * article and non-null {@code anchor == null}, {@code external == null}: broken.
     */
    public static Target resolveLink(Article from, String dest) {
        String d = dest.strip();
        int hash = d.indexOf('#');
        String anchor = hash < 0 ? null : d.substring(hash + 1);
        String path = hash < 0 ? d : d.substring(0, hash);
        if (path.isEmpty()) {
            return new Target(from, anchor, null);
        }
        String lower = path.toLowerCase(Locale.ROOT);
        if (lower.startsWith("http://") || lower.startsWith("https://") || lower.startsWith("mailto:")) {
            return new Target(null, null, d);
        }
        ResourceLocation id = resolveId(from, path);
        Article target = id == null ? null : articles.get(id);
        return new Target(target, target == null ? null : anchor, null);
    }

    /** Resource id for {@code path} relative to {@code from}, under {@code wiki/}; null if it escapes the root. */
    public static ResourceLocation resolveId(Article from, String path) {
        String p = path.endsWith(".md") ? path.substring(0, path.length() - 3) : path;
        String namespace = from.id().getNamespace();
        String base = from.folder();
        int colon = p.indexOf(':');
        if (colon >= 0) {
            namespace = p.substring(0, colon);
            p = p.substring(colon + 1);
            base = "";
        } else if (p.startsWith("/")) {
            p = p.substring(1);
            base = "";
        }
        String joined = normalize(base.isEmpty() ? p : base + "/" + p);
        return joined == null || !ResourceLocation.isValidNamespace(namespace) || !ResourceLocation.isValidPath(joined)
                ? null : ResourceLocation.fromNamespaceAndPath(namespace, joined);
    }

    /**
     * Image / asset {@code dest} written in {@code from}: {@code ns:path} as is; {@code /path} under
     * {@code ns:wiki/}; else relative to the article's own folder under {@code wiki/}. Null if malformed.
     */
    public static ResourceLocation resolveAsset(Article from, String dest) {
        String d = dest.strip();
        int colon = d.indexOf(':');
        if (colon >= 0) {
            return ResourceLocation.tryParse(d);
        }
        String namespace = from.id().getNamespace();
        String base = "wiki" + (from.folder().isEmpty() ? "" : "/" + from.folder());
        String joined = normalize(d.startsWith("/") ? "wiki/" + d.substring(1) : base + "/" + d);
        return joined == null || !ResourceLocation.isValidPath(joined) ? null
                : ResourceLocation.fromNamespaceAndPath(namespace, joined);
    }

    /** Collapses {@code .} / {@code ..}; null if the path climbs above its root. */
    static String normalize(String path) {
        List<String> out = new ArrayList<>();
        for (String part : path.split("/")) {
            if (part.isEmpty() || part.equals(".")) {
                continue;
            }
            if (part.equals("..")) {
                if (out.isEmpty()) {
                    return null;
                }
                out.removeLast();
            } else {
                out.add(part);
            }
        }
        return String.join("/", out);
    }

    /** Item for an {@code item:<id>} image reference. */
    public static Item item(ResourceLocation id) {
        return BuiltInRegistries.ITEM.getOptional(id).orElse(null);
    }
}
