package com.wf.wflib.corpse.api;

import com.mojang.authlib.GameProfile;
import com.wf.wflib.core.api.WFLib;
import com.wf.wflib.core.api.WFSystems;
import com.wf.wflib.corpse.CorpseDeaths;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.inventory.MenuType;
import net.neoforged.neoforge.registries.DeferredHolder;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Player death => {@link CorpseEntity} holding every drop (any mod's {@code LivingDropsEvent} additions), each stack
 * tagged with the {@link CorpseSlot} it was worn in; the body keeps the death ragdoll. Holders are registered by
 * {@code CorpseSystem}.
 */
public final class Corpses {
    public static final ResourceLocation INVENTORY = id("inventory");
    public static final ResourceLocation BACKPACK = id("backpack");
    public static final ResourceLocation CARRY = id("carry");
    public static final ResourceLocation CURIOS = id("curios");

    public static final DeferredHolder<EntityType<?>, EntityType<CorpseEntity>> ENTITY =
            DeferredHolder.create(Registries.ENTITY_TYPE, id("corpse"));
    public static final DeferredHolder<MenuType<?>, MenuType<CorpseMenu>> MENU =
            DeferredHolder.create(Registries.MENU, id("corpse"));

    private static final Map<ResourceLocation, CorpseSlot> SLOTS = new LinkedHashMap<>();

    private Corpses() {
    }

    private static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(WFLib.MODID, path);
    }

    /** Mod construction; snapshot + restore order = registration order. */
    public static synchronized void slot(CorpseSlot kind) {
        if (SLOTS.putIfAbsent(kind.id(), kind) != null) {
            throw new IllegalStateException("corpse slot " + kind.id() + " registered twice");
        }
    }

    public static @Nullable CorpseSlot slot(ResourceLocation id) {
        return SLOTS.get(id);
    }

    public static Collection<CorpseSlot> slots() {
        return SLOTS.values();
    }

    /**
     * A dead stand-in of an offline player ({@code owner}: its loaded save, written back by the caller): every slot
     * emptied into a corpse on {@code body}, ragdoll started from its rig. False with the system off.
     */
    public static boolean claim(ServerPlayer owner, GameProfile profile, LivingEntity body) {
        if (!WFSystems.enabled("corpse")) {
            return false;
        }
        CorpseDeaths.claim(owner, profile, body);
        return true;
    }
}
