package com.wf.wflib.armor.api;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.wf.wflib.core.api.WFLib;
import net.minecraft.core.HolderSet;
import net.minecraft.core.Registry;
import net.minecraft.core.RegistryCodecs;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;

import java.util.EnumSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * One armour profile as a datapack registry element ({@code data/<ns>/wflib/armor_profile/<name>.json}): what a named item, a list of
 * items or an item tag protects against, in the same rows an authored {@link ArmorSpec} has. This is how a pack (or another mod's
 * bridge) gives any mod's armour WFLib values without code. Data wins over code-authored specs; of two matching elements the higher
 * {@code priority} wins, a tie going to the later one. Condition is not stated: it is always rebased onto the item's own durability.
 *
 * <pre>{@code
 * {
 *   "targets": "#some:armor_tag",
 *   "rows": { "kinetic": {"dt": 3.0, "dr": 0.35}, "radiation": {"dr": 0.2, "tier": 4.0} },
 *   "worn_floor": 0.1,
 *   "inserts": {"slots": 2, "accepts": ["kinetic"]},
 *   "priority": 0
 * }
 * }</pre>
 *
 * Contents load with the world (not re-read by {@code /reload}) and sync to clients, whose tooltips read the same table.
 */
public record ArmorAssignment(HolderSet<Item> targets, Map<ProtectionType, ProtectionRow> rows, double wornFloor, int insertSlots,
                              Set<ProtectionType> insertFilter, int priority) {

    public static final ResourceKey<Registry<ArmorAssignment>> REGISTRY =
            ResourceKey.createRegistryKey(ResourceLocation.fromNamespaceAndPath(WFLib.MODID, "armor_profile"));

    private static final Codec<ProtectionType> TYPE = Codec.STRING.comapFlatMap(name -> {
        try {
            return DataResult.success(ProtectionType.valueOf(name.toUpperCase(Locale.ROOT)));
        } catch (IllegalArgumentException e) {
            return DataResult.error(() -> "unknown protection type '" + name + "'");
        }
    }, type -> type.name().toLowerCase(Locale.ROOT));

    private record Row(double dt, double dr, double hardness, double tier) {
    }

    private static final Codec<ProtectionRow> ROW = RecordCodecBuilder.<Row>create(i -> i.group(
            Codec.DOUBLE.optionalFieldOf("dt", 0.0D).forGetter(Row::dt),
            Codec.DOUBLE.optionalFieldOf("dr", 0.0D).forGetter(Row::dr),
            Codec.DOUBLE.optionalFieldOf("hardness", 0.0D).forGetter(Row::hardness),
            Codec.DOUBLE.optionalFieldOf("tier", 0.0D).forGetter(Row::tier)).apply(i, Row::new))
            .flatXmap(r -> {
                if (r.dt < 0 || r.dr < 0 || r.hardness < 0 || r.tier < 0) return DataResult.error(() -> "armour rows are non-negative");
                return DataResult.success(new ProtectionRow(r.dt, r.dr, r.hardness, r.tier));
            }, p -> DataResult.success(new Row(p.dt(), p.dr(), p.hardness(), p.tier())));

    private record Inserts(int slots, Set<ProtectionType> accepts) {
    }

    private static final Codec<Inserts> INSERTS = RecordCodecBuilder.create(i -> i.group(
            Codec.INT.fieldOf("slots").forGetter(Inserts::slots),
            TYPE.listOf().optionalFieldOf("accepts", java.util.List.of()).xmap(l -> (Set<ProtectionType>) (l.isEmpty()
                    ? EnumSet.noneOf(ProtectionType.class) : EnumSet.copyOf(l)), java.util.List::copyOf).forGetter(Inserts::accepts)
    ).apply(i, Inserts::new));

    public static final Codec<ArmorAssignment> CODEC = RecordCodecBuilder.create(i -> i.group(
            RegistryCodecs.homogeneousList(Registries.ITEM).fieldOf("targets").forGetter(ArmorAssignment::targets),
            Codec.unboundedMap(TYPE, ROW).fieldOf("rows").forGetter(ArmorAssignment::rows),
            Codec.DOUBLE.optionalFieldOf("worn_floor", 0.0D).forGetter(ArmorAssignment::wornFloor),
            INSERTS.optionalFieldOf("inserts", new Inserts(0, Set.of())).forGetter(a -> new Inserts(a.insertSlots(), a.insertFilter())),
            Codec.INT.optionalFieldOf("priority", 0).forGetter(ArmorAssignment::priority)
    ).apply(i, (targets, rows, floor, ins, priority) -> new ArmorAssignment(targets, rows, floor, ins.slots(), ins.accepts(), priority)));

    /** The spec this element describes for {@code itemId}. */
    public ArmorSpec toSpec(ResourceLocation itemId, int maxCondition) {
        ArmorSpec.Builder b = ArmorSpec.builder(itemId.toString(), maxCondition).wornFloor(wornFloor);
        rows.forEach(b::row);
        if (insertSlots > 0) b.inserts(insertSlots, insertFilter.toArray(ProtectionType[]::new));
        return b.build();
    }
}
