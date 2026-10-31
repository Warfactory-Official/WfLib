package com.wf.wflib.countermeasure;

import com.wf.wflib.core.api.WFSystem;
import com.wf.wflib.countermeasure.api.ApsGrenadeEntity;
import com.wf.wflib.core.api.WFLibContent;
import com.wf.wflib.countermeasure.api.Countermeasures;
import com.wf.wflib.countermeasure.api.FlareEntity;
import com.wf.wflib.countermeasure.api.SmokeGrenadeEntity;
import com.wf.wflib.countermeasure.client.CountermeasureClient;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.RegisterEvent;
import com.wf.wflib.core.api.WFLib;

/** Holders named through {@link com.wf.wflib.countermeasure.api.Countermeasures}. */
public final class CountermeasureSystem implements WFSystem {
    private static final DeferredRegister<EntityType<?>> ENTITIES =
            DeferredRegister.create(Registries.ENTITY_TYPE, WFLib.MODID);
    private static final DeferredRegister<SoundEvent> SOUNDS =
            DeferredRegister.create(Registries.SOUND_EVENT, WFLib.MODID);

    public static final DeferredHolder<SoundEvent, SoundEvent> SMOKE_POP = sound("countermeasure.smoke_pop");
    public static final DeferredHolder<SoundEvent, SoundEvent> FLARE_BURN = sound("countermeasure.flare_burn");

    private static DeferredHolder<SoundEvent, SoundEvent> sound(String path) {
        ResourceLocation id = ResourceLocation.fromNamespaceAndPath(WFLib.MODID, path);
        return SOUNDS.register(path, () -> SoundEvent.createVariableRangeEvent(id));
    }

    @Override
    public String id() {
        return "countermeasure";
    }

    @Override
    public java.util.Set<String> requires() {
        return java.util.Set.of("grenade", "missile", "round");
    }

    @Override
    public void register(IEventBus modBus, ModContainer container) {
        com.wf.wflib.core.api.CreativeTabs.contribute("countermeasure", 260, out -> com.wf.wflib.countermeasure.api.Countermeasures.live().forEach(id ->
                out.accept(net.minecraft.core.registries.BuiltInRegistries.ITEM.get(id))));
        ENTITIES.register("flare",
                () -> EntityType.Builder.<FlareEntity>of(FlareEntity::new, MobCategory.MISC).sized(0.25f, 0.25f)
                        .clientTrackingRange(16).updateInterval(1).noSave().build("flare"));
        ENTITIES.register("smoke_grenade", () -> EntityType.Builder.<SmokeGrenadeEntity>of(
                        SmokeGrenadeEntity::new, MobCategory.MISC).sized(0.3f, 0.3f).clientTrackingRange(16)
                .updateInterval(1).noSave().build("smoke_grenade"));
        ENTITIES.register("aps_grenade", () -> EntityType.Builder.<ApsGrenadeEntity>of(
                        ApsGrenadeEntity::new, MobCategory.MISC).sized(0.3f, 0.3f).clientTrackingRange(16)
                .updateInterval(1).noSave().build("aps_grenade"));
        ENTITIES.register(modBus);
        SOUNDS.register(modBus);
        modBus.addListener(CountermeasureSystem::onRegister);
    }

    private static void onRegister(RegisterEvent event) {
        if (!event.getRegistryKey().equals(Registries.ITEM)) {
            return;
        }
        WFLibContent.freeze();
        event.register(Registries.ITEM, helper -> Countermeasures.live().forEach(id ->
                helper.register(id, CountermeasureCatalogue.factory(id).get())));
    }

    @Override
    public void registerClient(IEventBus modBus) {
        CountermeasureClient.register(modBus);
    }
}
