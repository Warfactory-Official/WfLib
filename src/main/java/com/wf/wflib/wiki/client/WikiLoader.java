package com.wf.wflib.wiki.client;

import com.mojang.logging.LogUtils;
import com.wf.wflib.wiki.api.Article;
import com.wf.wflib.wiki.ArticleParser;
import com.wf.wflib.wiki.api.Wiki;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimplePreparableReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;
import org.slf4j.Logger;

import java.util.HashMap;
import java.util.Map;
import java.util.TreeMap;

/**
 * {@code assets/<ns>/wiki/<path>.md} -> article {@code <ns>:<path>}. Unreadable / unparsable file => logged, skipped.
 */
public final class WikiLoader extends SimplePreparableReloadListener<Map<ResourceLocation, Article>> {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final String ROOT = "wiki";

    @Override
    protected Map<ResourceLocation, Article> prepare(ResourceManager manager, ProfilerFiller profiler) {
        Map<ResourceLocation, Article> out = new HashMap<>();
        Map<ResourceLocation, Resource> files = new TreeMap<>(manager.listResources(ROOT, rl -> rl.getPath().endsWith(".md")));
        for (Map.Entry<ResourceLocation, Resource> e : files.entrySet()) {
            ResourceLocation file = e.getKey();
            String path = file.getPath();
            ResourceLocation id = ResourceLocation.tryBuild(file.getNamespace(),
                    path.substring(ROOT.length() + 1, path.length() - 3));
            if (id == null) {
                LOGGER.warn("Wiki {}: invalid article id: skipped", file);
                continue;
            }
            try (java.io.InputStream in = e.getValue().open()) {
                out.put(id, ArticleParser.parse(id, new String(in.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8)));
            } catch (Exception ex) {
                LOGGER.warn("Wiki {}: {}: skipped", file, ex.toString());
            }
        }
        return out;
    }

    @Override
    protected void apply(Map<ResourceLocation, Article> loaded, ResourceManager manager, ProfilerFiller profiler) {
        Wiki.replace(loaded);
        WikiIcons.clear();
        WikiApp.reloaded();
        LOGGER.info("Wiki: {} articles", loaded.size());
    }
}
