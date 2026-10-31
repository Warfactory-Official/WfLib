package com.wf.wflib.hazard.api;

import net.minecraft.resources.ResourceLocation;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.HolderSet;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.core.RegistryCodecs;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.Item;

import java.util.List;
import com.wf.wflib.core.api.WFLib;

/**
 * One hazard binding, as a datapack registry element. A pack replaces a binding by shipping a file at the
 * same path; an empty {@code entries} list is how it states that something carries no hazard.
 *
 * <p>Contents load with the world and are not re-read by {@code /reload}. Synced, the hazard tooltip being a
 * client-side read of the same table.
 */
public record HazardAssignment(HolderSet<Item> targets, List<Entry> entries, int mutex, int priority) {

    public static final ResourceKey<Registry<HazardAssignment>> REGISTRY =
            ResourceKey.createRegistryKey(ResourceLocation.fromNamespaceAndPath(WFLib.MODID, "hazard"));

    public static final Codec<HazardAssignment> CODEC = RecordCodecBuilder.create(
            instance -> instance.group(
                    RegistryCodecs.homogeneousList(Registries.ITEM).fieldOf("targets")
                            .forGetter(HazardAssignment::targets),
                    Entry.CODEC.listOf().fieldOf("entries").forGetter(HazardAssignment::entries),
                    Codec.INT.optionalFieldOf("mutex", 0).forGetter(HazardAssignment::mutex),
                    Codec.INT.optionalFieldOf("priority", HazardData.DEFAULT_PRIORITY)
                            .forGetter(HazardAssignment::priority)
            ).apply(instance, HazardAssignment::new));

    public static HazardAssignment of(HolderSet<Item> targets, HazardData data) {
        return new HazardAssignment(targets, entriesOf(data), data.getMutex(), data.priority);
    }

    private static List<Entry> entriesOf(HazardData data) {
        return data.entries.stream().map(e -> {
            if (!e.getMods().isEmpty()) {
                throw new IllegalArgumentException(
                        "hazard entry for " + e.type + " carries modifiers, which this record cannot project");
            }
            return new Entry(e.type, e.baseLevel);
        }).toList();
    }

    public HazardData toData() {
        HazardData data = new HazardData();
        for (Entry entry : entries) data.addEntry(entry.type(), entry.level());
        return data.setMutex(mutex).setPriority(priority);
    }

    public record Entry(IHazardType type, double level) {

        public static final Codec<Entry> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                Hazards.TYPE_CODEC.fieldOf("type").forGetter(Entry::type),
                Codec.DOUBLE.fieldOf("level").forGetter(Entry::level)
        ).apply(instance, Entry::new));
    }
}
