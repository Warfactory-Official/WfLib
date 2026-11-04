package com.wf.wflib.round.lab;

import com.wf.wflib.round.pen.PenMaterial;
import com.wf.wflib.round.pen.PenTable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.EmptyBlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.Shapes;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Cover materials: a named core set plus one full cube per distinct (resistance, toughness, ricochet) triple. */
final class CoverBlocks {
    static final List<String> NAMED = List.of("dirt", "sand", "gravel", "clay", "snow_block", "hay_block", "white_wool",
            "oak_planks", "oak_log", "glass", "ice", "stone", "cobblestone", "stone_bricks", "bricks", "sandstone",
            "terracotta", "white_concrete", "deepslate", "iron_block", "copper_block", "obsidian", "netherite_block",
            "bedrock");

    record Entry(String id, BlockState state, PenMaterial material, float resistance, float toughness,
                 float ricochet, int equivalents, List<String> examples) {
    }

    private CoverBlocks() {
    }

    static List<Entry> all() {
        Map<String, List<String>> groups = new LinkedHashMap<>();
        Map<String, Block> first = new LinkedHashMap<>();
        List<Block> blocks = new ArrayList<>();
        BuiltInRegistries.BLOCK.forEach(blocks::add);
        blocks.sort(java.util.Comparator.comparing(b -> BuiltInRegistries.BLOCK.getKey(b).toString()));
        for (Block b : blocks) {
            BlockState s = b.defaultBlockState();
            if (s.isAir() || s.getCollisionShape(EmptyBlockGetter.INSTANCE, BlockPos.ZERO) != Shapes.block()) {
                continue;
            }
            String key = PenTable.resistance(s) + "/" + PenTable.toughness(s) + "/" + PenTable.ricochet(s);
            groups.computeIfAbsent(key, k -> new ArrayList<>()).add(BuiltInRegistries.BLOCK.getKey(b).toString());
            first.putIfAbsent(key, b);
        }
        Map<String, Entry> out = new LinkedHashMap<>();
        Set<String> named = Set.copyOf(NAMED);
        for (Block b : blocks) {
            ResourceLocation id = BuiltInRegistries.BLOCK.getKey(b);
            if (id.getNamespace().equals("minecraft") && named.contains(id.getPath())) {
                BlockState s = b.defaultBlockState();
                String key = PenTable.resistance(s) + "/" + PenTable.toughness(s) + "/" + PenTable.ricochet(s);
                List<String> g = groups.getOrDefault(key, List.of(id.toString()));
                out.put(id.toString(), entry(id.toString(), s, g));
            }
        }
        for (Map.Entry<String, Block> e : first.entrySet()) {
            List<String> g = groups.get(e.getKey());
            boolean covered = g.stream().anyMatch(out::containsKey);
            if (!covered) {
                String id = BuiltInRegistries.BLOCK.getKey(e.getValue()).toString();
                out.put(id, entry(id, e.getValue().defaultBlockState(), g));
            }
        }
        return List.copyOf(out.values());
    }

    private static Entry entry(String id, BlockState s, List<String> group) {
        return new Entry(id, s, PenMaterial.resolve(s), PenTable.resistance(s), PenTable.toughness(s),
                PenTable.ricochet(s), group.size(), group.size() > 8 ? group.subList(0, 8) : group);
    }
}
