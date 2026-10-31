package com.wf.wflib.mine;

import com.wf.wflib.mine.api.MineContent;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import com.wf.wflib.mine.debug.MineCommands;
import com.wf.wflib.core.api.WFLibContent;
import com.wf.wflib.core.api.WFSystem;
import com.wf.wflib.core.api.WFSystems;
import com.wf.wflib.explosion.api.BlockChangeWatchers;
import com.wf.wflib.mine.api.MineEntity;
import com.wf.wflib.mine.api.MineModels;
import com.wf.wflib.mine.api.MinePreset;
import com.wf.wflib.mine.api.MinePresets;
import com.wf.wflib.mine.api.MineTriggers;
import com.wf.wflib.mine.client.MineClient;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.ExtraCodecs;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.Level;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.RegisterEvent;
import net.minecraft.core.BlockPos;

import java.util.Set;
import com.wf.wflib.core.api.WFLib;
import com.wf.wflib.mine.api.MineItem;

/** Land and naval mines: presets, triggers, models, the entity, and the section revisions they sleep on. */
public final class MineSystem implements WFSystem {
    private static volatile boolean active;

    /** Mixin guard: true once this system registered. */
    public static boolean active() {
        return active;
    }

    @Override
    public String id() {
        return "mine";
    }

    @Override
    public Set<String> requires() {
        return Set.of("demolition");
    }

    @Override
    public void register(IEventBus modBus, ModContainer container) {
        com.wf.wflib.core.api.CreativeTabs.contribute("mine", 250, out -> MineContent.items().forEach(item -> out.accept(item.get())));
        active = true;
        MineWarheads.register();
        MineModels.register("ap", 0.06);
        MineModels.register("bounding", 0.15);
        MineModels.register("claymore", 0.0);
        MineModels.register("heat", 0.09);
        MineModels.register("naval", 0.0);
        MineModels.register("scatter_ap", 0.0);
        MineModels.register("scatter_at", 0.0);
        MineModels.register("dispenser_ap", 0.0);
        MineModels.register("dispenser_at", 0.0);
        MineCatalogue.definitions().forEach(MinePresets::register);
        MineTriggers.register(MineTriggers.rl("any"), MineTriggers.ANY);
        MineTriggers.register(MineTriggers.defaultId(), MineTriggers.LIVING);
        MineTriggers.register(MineTriggers.rl("players"), MineTriggers.PLAYERS);
        MineTriggers.register(MineTriggers.rl("mobs"), MineTriggers.MOBS);
        MineTriggers.register(MineTriggers.rl("heavy"), MineTriggers.HEAVY);

        DeferredRegister<EntityType<?>> entities = DeferredRegister.create(Registries.ENTITY_TYPE, WFLib.MODID);
        entities.register("mine", () -> EntityType.Builder.<MineEntity>of(MineEntity::new, MobCategory.MISC)
                .sized(0.75F, 0.25F)
                .clientTrackingRange(8)
                .updateInterval(3)
                .build("mine"));
        entities.register(modBus);
        DeferredRegister<DataComponentType<?>> components =
                DeferredRegister.create(Registries.DATA_COMPONENT_TYPE, WFLib.MODID);
        components.register("mine_canisters", () -> DataComponentType.<Integer>builder()
                .persistent(ExtraCodecs.NON_NEGATIVE_INT)
                .networkSynchronized(ByteBufCodecs.VAR_INT)
                .build());
        components.register(modBus);
        modBus.addListener(RegisterEvent.class, MineSystem::onRegister);

        BlockChangeWatchers.WATCHERS.register(ResourceLocation.fromNamespaceAndPath(WFLib.MODID, "mine_sections"), 0,
                new BlockChangeWatchers.Watcher() {
                    @Override
                    public boolean isWatching() {
                        return MineSectionWatch.isWatching();
                    }

                    @Override
                    public void onBlockChanged(Level level, BlockPos pos) {
                        MineSectionWatch.onBlockChanged(level, pos);
                    }
                });
        NeoForge.EVENT_BUS.register(MineTargets.class);
        NeoForge.EVENT_BUS.register(MineSectionWatch.class);
        NeoForge.EVENT_BUS.addListener(RegisterCommandsEvent.class, MineCommands::onRegisterCommands);
        if (WFSystems.enabled("probe")) {
            MineProbeActions.register();
        }
    }

    @Override
    public void registerClient(IEventBus modBus) {
        MineClient.register(modBus);
    }

    private static void onRegister(RegisterEvent event) {
        if (!event.getRegistryKey().equals(Registries.ITEM)) {
            return;
        }
        WFLibContent.freeze();
        event.register(Registries.ITEM, helper -> {
            for (ResourceLocation id : MinePresets.live()) {
                MinePreset preset = MinePresets.get(id);
                helper.register(MinePresets.itemId(id), (Item) new MineItem(preset, new Item.Properties().stacksTo(16)));
            }
        });
    }
}
