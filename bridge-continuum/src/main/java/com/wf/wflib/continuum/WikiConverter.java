package com.wf.wflib.continuum;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Continuum's manual (QMAW: one JSON page per topic, {@code [[link]]} markup, {@code <br>} breaks, items that trigger it) as WFLib wiki articles (markdown with front
 * matter, items bound), so the PDA's Wiki app has them and opens the right page for an item. Pure: pages in, markdown out; the pack writer and the tests share it.
 *
 * <p>A page is named by its {@code name}. Links are {@code [[target|text]]} or {@code [[text|target]]} (the shipped pages use both, so the side that names a page wins)
 * or {@code [[target]]}; one that names no page becomes plain text. Languages other than {@code en_us} are not carried.
 */
final class WikiConverter {
    static final String NAMESPACE = ContinuumBridge.ID;
    private static final Pattern LINK = Pattern.compile("\\[\\[(.+?)]]");

    private WikiConverter() {
    }

    /** Page path (relative to the manual folder, no extension) to the article markdown. */
    static Map<String, String> convert(Map<String, JsonObject> pages) {
        Map<String, String> pathByName = new LinkedHashMap<>();
        for (Map.Entry<String, JsonObject> e : pages.entrySet()) {
            if (e.getValue().has("name")) pathByName.put(e.getValue().get("name").getAsString(), e.getKey());
        }
        Map<String, String> out = new LinkedHashMap<>();
        for (Map.Entry<String, JsonObject> e : pages.entrySet()) {
            String md = page(e.getValue(), pathByName);
            if (md != null) out.put(e.getKey(), md);
        }
        return out;
    }

    private static String page(JsonObject json, Map<String, String> pathByName) {
        String name = json.has("name") ? json.get("name").getAsString() : null;
        String body = english(json.get("content"));
        if (name == null || body == null) return null;
        String title = english(json.get("title"));
        if (title == null) title = name;
        // Templates are fragments the manual pastes into other pages, not pages to read; their text is full of colour codes.
        if (title.startsWith("Template:") || name.startsWith("Template:") || (json.has("noindex") && json.get("noindex").getAsBoolean())) return null;
        body = FORMAT_CODE.matcher(body).replaceAll("");

        Set<String> items = new LinkedHashSet<>();
        if (json.has("trigger") && json.get("trigger").isJsonArray()) {
            for (JsonElement t : json.getAsJsonArray("trigger")) {
                String id = itemId(t);
                if (id != null) items.add(id);
            }
        }
        String icon = json.has("icon") ? itemId(json.get("icon")) : null;
        if (icon == null && !items.isEmpty()) icon = items.iterator().next();

        StringBuilder md = new StringBuilder("---\n");
        md.append("title: ").append(quote(title)).append('\n');
        if (icon != null) md.append("icon: ").append(icon).append('\n');
        if (!items.isEmpty()) {
            md.append("items: [");
            boolean first = true;
            for (String id : items) {
                md.append(first ? "" : ", ").append(id);
                first = false;
            }
            md.append("]\n");
        }
        md.append("keywords: [continuum, ntm, manual]\n---\n# ").append(title).append("\n\n");

        Matcher m = LINK.matcher(body.replace("<br>", "  \n"));
        StringBuilder text = new StringBuilder();
        while (m.find()) {
            String inner = m.group(1);
            String target = inner, shown = inner;
            int pipe = inner.indexOf('|');
            if (pipe != -1) {
                String a = inner.substring(0, pipe), b = inner.substring(pipe + 1);
                if (pathByName.containsKey(a)) {
                    target = a;
                    shown = b;
                } else if (pathByName.containsKey(b)) {
                    target = b;
                    shown = a;
                } else {
                    target = a;
                    shown = b;
                }
            }
            String path = pathByName.get(target);
            m.appendReplacement(text, Matcher.quoteReplacement(path == null ? shown : "[" + shown + "](" + NAMESPACE + ":" + path + ")"));
        }
        m.appendTail(text);
        md.append(text).append('\n');
        return md.toString();
    }

    private static final Pattern FORMAT_CODE = Pattern.compile("§[0-9a-fk-or]");

    private static String english(JsonElement element) {
        if (element == null || !element.isJsonObject()) return null;
        JsonObject o = element.getAsJsonObject();
        return o.has("en_us") && o.get("en_us").isJsonPrimitive() ? o.get("en_us").getAsString() : null;
    }

    /** The item id a trigger or icon names: a string, or an array whose first entry (possibly nested) is one. Wildcards and prefixes are not ids. */
    static String itemId(JsonElement element) {
        if (element == null) return null;
        if (element.isJsonPrimitive() && element.getAsJsonPrimitive().isString()) {
            String s = element.getAsString();
            return s.matches("[a-z0-9_.-]+:[a-z0-9_./-]+") ? s : null;
        }
        if (element.isJsonArray()) {
            JsonArray a = element.getAsJsonArray();
            return a.isEmpty() ? null : itemId(a.get(0));
        }
        return null;
    }

    private static String quote(String s) {
        return "\"" + s.replace("\\", "\\\\").replace("\"", "\\\"") + "\"";
    }
}
