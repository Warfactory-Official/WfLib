package com.wf.wflib.wiki.api;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import org.commonmark.node.Document;

import java.util.List;

/**
 * One wiki article: {@code assets/<ns>/wiki/<path>.md} => id {@code <ns>:<path>}. {@code document} is the parsed body
 * (front matter removed); {@code searchText} is its lowercase plain text.
 */
public record Article(ResourceLocation id, String title, String summary, Icon icon, List<String> keywords,
                      List<Binding> bindings, int priority, boolean hidden, Document document, String searchText) {

    /** {@code item}: registry id of an item whose inventory sprite is drawn; {@code texture}: any PNG. */
    public record Icon(ResourceLocation item, ResourceLocation texture) {
    }

    /** Item id or, with {@code tag}, an item tag. */
    public record Binding(ResourceLocation id, boolean tag) {
        public static Binding parse(String value) {
            String v = value.strip();
            return v.startsWith("#") ? new Binding(ResourceLocation.parse(v.substring(1)), true)
                    : new Binding(ResourceLocation.parse(v), false);
        }

        public boolean matches(Item item) {
            return tag ? item.builtInRegistryHolder().is(TagKey.create(Registries.ITEM, id))
                    : BuiltInRegistries.ITEM.getKey(item).equals(id);
        }
    }

    /** Folder of the article within {@code wiki/}, no trailing slash; empty at the root. */
    public String folder() {
        String path = id.getPath();
        int slash = path.lastIndexOf('/');
        return slash < 0 ? "" : path.substring(0, slash);
    }
}
