package com.wf.wflib.continuum;

import com.hbm.inventory.material.Mats;
import com.hbm.inventory.material.MaterialShapes;
import com.hbm.inventory.material.NTMMaterial;
import com.warfactory.ultimateweight.api.IWeightCompatProvider;
import com.warfactory.ultimateweight.api.WeightCompatRegistry;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import java.util.Map;
import java.util.OptionalDouble;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Masses for Continuum's material items (docs, 3.5), from physics rather than from a table of guesses: Continuum states what an item is made of (a material and
 * a shape, in 72nds of an ingot: nugget 8, ingot 72, block 648), and a material has a density. An ingot is taken as an eighth of a litre, so a steel ingot is about
 * 0.98 kg; every other shape is its share of that. Items that are not material items (machines, guns, ammunition) have no mass here and are left to whoever knows them.
 */
final class ContinuumMasses implements IWeightCompatProvider {
    /** Real densities, kg per litre. A material not listed has no mass. */
    private static final Map<String, Double> DENSITY = Map.ofEntries(
            Map.entry("wood", 0.6), Map.entry("iron", 7.87), Map.entry("gold", 19.3), Map.entry("titanium", 4.51), Map.entry("aluminium", 2.70),
            Map.entry("steel", 7.85), Map.entry("lead", 11.34), Map.entry("copper", 8.96), Map.entry("gunmetal", 8.7), Map.entry("weaponsteel", 7.85),
            Map.entry("combine_steel", 8.0), Map.entry("dura_steel", 7.9), Map.entry("saturnite", 10.0), Map.entry("schrabidium", 19.0), Map.entry("desh", 8.0),
            Map.entry("red_copper", 8.9), Map.entry("magnetized_tungsten", 19.25), Map.entry("tungsten", 19.25), Map.entry("beryllium", 1.85),
            Map.entry("cobalt", 8.9), Map.entry("niobium", 8.57), Map.entry("zirconium", 6.52), Map.entry("bismuth", 9.78), Map.entry("boron", 2.34),
            Map.entry("arsenic", 5.73), Map.entry("neodymium", 7.01), Map.entry("lanthanium", 6.15), Map.entry("cadmium", 8.65), Map.entry("asbestos", 2.5),
            Map.entry("uranium", 19.1), Map.entry("u233", 19.1), Map.entry("thorium", 11.7), Map.entry("plutonium", 19.8), Map.entry("neptunium", 20.45),
            Map.entry("polonium", 9.2), Map.entry("actinium", 10.07));

    /** One ingot's volume in litres. */
    static final double INGOT_LITRES = 0.125D;

    private final Map<Item, Double> memo = new ConcurrentHashMap<>();

    static void register() {
        WeightCompatRegistry.register(new ContinuumMasses());
    }

    /** Kilograms of one item, or 0 when it is not a material item. Tags must be bound. */
    double kg(Item item) {
        return memo.computeIfAbsent(item, i -> {
            ItemStack stack = new ItemStack(i);
            for (NTMMaterial material : Mats.orderedList) {
                Double density = DENSITY.get(material.name);
                if (density == null) continue;
                for (MaterialShapes shape : MaterialShapes.values()) {
                    if (shape.quantity <= 0) continue;
                    if (stack.is(material.tag(shape))) return shape.quantity / 72.0D * density * INGOT_LITRES;
                }
            }
            return 0.0D;
        });
    }

    @Override
    public OptionalDouble getUnitWeight(Object stack) {
        if (!(stack instanceof ItemStack s) || s.isEmpty()) return OptionalDouble.empty();
        double kg = kg(s.getItem());
        return kg > 0 ? OptionalDouble.of(kg) : OptionalDouble.empty();
    }

    @Override
    public int getPriority() {
        return 120;
    }
}
