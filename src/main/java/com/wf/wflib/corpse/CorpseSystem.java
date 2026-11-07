package com.wf.wflib.corpse;

import com.wf.wflib.core.api.WFLib;
import com.wf.wflib.core.api.WFSystems;
import com.wf.wflib.core.api.WFSystem;
import com.wf.wflib.corpse.api.CorpseEntity;
import com.wf.wflib.corpse.api.CorpseMenu;
import com.wf.wflib.corpse.api.Corpses;
import com.wf.wflib.corpse.client.CorpseClient;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.inventory.MenuType;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.ModList;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.common.extensions.IMenuTypeExtension;
import net.neoforged.neoforge.event.entity.EntityAttributeCreationEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.living.LivingDropsEvent;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.Set;

/** Player corpses (Corpse mod behaviour) on the death ragdoll. */
public final class CorpseSystem implements WFSystem {

    @Override
    public String id() {
        return "corpse";
    }

    @Override
    public Set<String> requires() {
        return Set.of("ragdoll");
    }

    @Override
    public void register(IEventBus modBus, ModContainer container) {
        DeferredRegister<EntityType<?>> entities = DeferredRegister.create(Registries.ENTITY_TYPE, WFLib.MODID);
        DeferredRegister<MenuType<?>> menus = DeferredRegister.create(Registries.MENU, WFLib.MODID);
        entities.register("corpse", () -> EntityType.Builder.<CorpseEntity>of(CorpseEntity::new, MobCategory.MISC)
                .sized(1.8F, 0.5F).fireImmune().clientTrackingRange(10).updateInterval(2).build("corpse"));
        menus.register("corpse", () -> IMenuTypeExtension.create(CorpseMenu::client));
        entities.register(modBus);
        menus.register(modBus);
        modBus.addListener(EntityAttributeCreationEvent.class,
                e -> e.put(Corpses.ENTITY.get(), LivingEntity.createLivingAttributes().build()));
        container.registerConfig(ModConfig.Type.COMMON, CorpseConfig.SPEC, "wflib-corpse.toml");

        Corpses.slot(CorpseSlots.INVENTORY);
        if (WFSystems.enabled("backpack")) {
            Corpses.slot(CorpseSlots.BACKPACK);
        }
        if (WFSystems.enabled("carry")) {
            Corpses.slot(CorpseSlots.CARRY);
        }
        if (ModList.get().isLoaded("curios")) {
            Corpses.slot(CorpseSlots.CURIOS);
        }
        NeoForge.EVENT_BUS.addListener(EventPriority.LOWEST, LivingDeathEvent.class, CorpseDeaths::onDeath);
        NeoForge.EVENT_BUS.addListener(EventPriority.LOWEST, LivingDropsEvent.class, CorpseDeaths::onDrops);
    }

    @Override
    public void registerClient(IEventBus modBus) {
        CorpseClient.register(modBus);
    }
}
