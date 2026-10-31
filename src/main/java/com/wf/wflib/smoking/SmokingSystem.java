package com.wf.wflib.smoking;

import com.norwood.ahf.hook.AhfHooks;
import com.wf.wflib.core.api.WFSystem;
import com.wf.wflib.smoking.api.SmokingContent;
import com.wf.wflib.smoking.client.CigaretteItemClient;
import com.wf.wflib.smoking.client.SmokingClient;
import com.wf.wflib.smoking.client.SmokingEffects;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.item.Item;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.client.extensions.common.RegisterClientExtensionsEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;
import com.wf.wflib.core.api.WFLib;

/** Cigarette: one per tap, hand animation, smoke and ember lights; posts {@code CigaretteSmokedEvent}. */
public final class SmokingSystem implements WFSystem {
    /** GAMMA cigarette use sound (third-party, internal only), whole {@link SmokingTimeline}. */
    public static final DeferredHolder<SoundEvent, SoundEvent> CIGARETTE_SOUND = DeferredHolder.create(
            Registries.SOUND_EVENT, ResourceLocation.fromNamespaceAndPath(WFLib.MODID, "smoking.cigarette"));

    @Override
    public String id() {
        return "smoking";
    }

    @Override
    public void register(IEventBus modBus, ModContainer container) {
        com.wf.wflib.core.api.CreativeTabs.contribute("smoking", 400, out -> out.accept(SmokingContent.CIGARETTE.get()));
        DeferredRegister<AttachmentType<?>> attachments =
                DeferredRegister.create(NeoForgeRegistries.Keys.ATTACHMENT_TYPES, WFLib.MODID);
        attachments.register("smoking", () -> AttachmentType.<Smoking.Session>builder(() -> {
            throw new IllegalStateException("smoking session read without hasData");
        }).sync(Smoking.Session.STREAM_CODEC).build());
        attachments.register(modBus);
        DeferredRegister<Item> items = DeferredRegister.create(Registries.ITEM, WFLib.MODID);
        items.register("cigarette", () -> new CigaretteItem(new Item.Properties().stacksTo(20)));
        items.register(modBus);
        DeferredRegister<SoundEvent> sounds = DeferredRegister.create(Registries.SOUND_EVENT, WFLib.MODID);
        sounds.register("smoking.cigarette", () -> SoundEvent.createVariableRangeEvent(CIGARETTE_SOUND.getId()));
        sounds.register(modBus);
        NeoForge.EVENT_BUS.addListener(PlayerTickEvent.Post.class, Smoking::onTick);
        modBus.addListener(FMLCommonSetupEvent.class, event -> event.enqueueWork(() ->
                AhfHooks.addHandTargets(Smoking::targets)));
    }

    @Override
    public void registerClient(IEventBus modBus) {
        NeoForge.EVENT_BUS.register(SmokingClient.class);
        NeoForge.EVENT_BUS.register(SmokingEffects.class);
        modBus.addListener(FMLClientSetupEvent.class, event -> event.enqueueWork(SmokingEffects::register));
        modBus.addListener(RegisterClientExtensionsEvent.class, event ->
                event.registerItem(CigaretteItemClient.INSTANCE, SmokingContent.CIGARETTE.get()));
    }
}
