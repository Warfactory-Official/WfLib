package com.wf.wflib.radiation;

import com.wf.wflib.hazard.api.HazardRules;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.advancements.critereon.StatePropertiesPredicate;
import net.minecraft.core.HolderSet;
import net.minecraft.core.Registry;
import net.minecraft.core.RegistryCodecs;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

import java.util.Map;
import java.util.Optional;
import com.wf.wflib.core.api.WFLib;

/**
 * One continuous radiation source, as a datapack registry element: the {@code (emission, saturation)} pair
 * {@link RadiationSystemNT#markRadSource} resolves per state at the palette walk.
 *
 * <p>{@code state} narrows the rule to the states matching it, which is how a rate that varies with a
 * property is written - one row per value. Omitted, the rule takes every state of every target.
 *
 * <p>Contents load with the world and are not re-read by {@code /reload}. Server-side only: no client reads
 * a source rule.
 */
public record RadSourceRule(HolderSet<Block> targets, Optional<StatePropertiesPredicate> state, Rate rate) {

    public static final ResourceKey<Registry<RadSourceRule>> REGISTRY =
            ResourceKey.createRegistryKey(ResourceLocation.fromNamespaceAndPath(WFLib.MODID, "rad_source"));

    public static final Codec<RadSourceRule> CODEC = RecordCodecBuilder.create(
            instance -> instance.group(
                    RegistryCodecs.homogeneousList(Registries.BLOCK).fieldOf("targets")
                            .forGetter(RadSourceRule::targets),
                    StatePropertiesPredicate.CODEC.optionalFieldOf("state").forGetter(RadSourceRule::state),
                    Rate.CODEC.fieldOf("rate").forGetter(RadSourceRule::rate)
            ).apply(instance, RadSourceRule::new));

    public boolean matches(BlockState candidate) {
        return state.map(p -> p.matches(candidate)).orElse(true);
    }

    /**
     * Where the pair comes from. {@link ItemHazard} exists so a block whose rate IS its own item's RADIATION
     * registration says so, rather than shipping a copy that stops tracking the moment a pack edits it.
     */
    public sealed interface Rate {

        Map<String, MapCodec<? extends Rate>> TYPES = Map.of(
                "fixed", Fixed.MAP_CODEC,
                "item_hazard", ItemHazard.MAP_CODEC);

        Codec<Rate> CODEC = Codec.STRING
                .comapFlatMap(name -> TYPES.containsKey(name)
                        ? DataResult.success(name)
                        : DataResult.error(() -> "unknown rad source rate " + name + ", known: " + TYPES.keySet()),
                        name -> name)
                .dispatch("type", Rate::typeName, TYPES::get);

        String typeName();

        double emission(Block block);

        double saturation(Block block);

        record Fixed(double emission, double saturation) implements Rate {

            static final MapCodec<Fixed> MAP_CODEC = RecordCodecBuilder.mapCodec(
                    instance -> instance.group(
                            Codec.DOUBLE.fieldOf("emission").forGetter(Fixed::emission),
                            Codec.DOUBLE.optionalFieldOf("saturation", 0.0D).forGetter(Fixed::saturation)
                    ).apply(instance, Fixed::new));

            @Override
            public String typeName() {
                return "fixed";
            }

            @Override
            public double emission(Block block) {
                return emission;
            }

            @Override
            public double saturation(Block block) {
                return saturation;
            }
        }

        /**
         * 1.7 bleeds a tenth of the block's own dose per tick, up to that dose: the emission is the rate per
         * tick and the saturation is the dose itself.
         */
        record ItemHazard() implements Rate {

            static final MapCodec<ItemHazard> MAP_CODEC = MapCodec.unit(ItemHazard::new);

            @Override
            public String typeName() {
                return "item_hazard";
            }

            @Override
            public double emission(Block block) {
                return rawRads(block) * 0.1F / 20.0D;
            }

            @Override
            public double saturation(Block block) {
                return rawRads(block);
            }

            // 1.7 stores the block's dose as a float, so the narrowing is part of the value.
            private static float rawRads(Block block) {
                return com.wf.wflib.core.api.WFSystems.enabled("hazard") ? (float) HazardRules.getRawRadsFromBlock(block) : 0.0F;
            }
        }
    }
}
