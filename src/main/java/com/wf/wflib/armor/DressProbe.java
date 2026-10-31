package com.wf.wflib.armor;

import com.wf.wflib.armor.api.Dress;
import com.wf.wflib.probe.api.ProbeAction;
import com.wf.wflib.probe.api.ProbeActions;
import com.wf.wflib.probe.api.ProbeProviders;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

/** Probe rows and handlers for {@link Dress}. */
final class DressProbe {
    private DressProbe() {
    }

    static void registerServer() {
        ProbeActions.registerTimedEntity(Dress.PUT_ON, new ProbeActions.TimedEntity() {
            @Override
            public int ticks(ServerPlayer player, Entity entity, int arg) {
                if (entity instanceof Player target && Dress.putOnSlot(player, target) != null) {
                    return ArmorConfig.get().assistTicks();
                }
                Dress.refuse(player, "fit");
                return 0;
            }

            @Override
            public boolean holds(ServerPlayer player, Entity entity, int arg) {
                return entity instanceof Player target && Dress.putOnSlot(player, target) != null;
            }

            @Override
            public boolean perform(ServerPlayer player, Entity entity, int arg) {
                if (entity instanceof Player target) {
                    Dress.putOn(player, target);
                }
                return true;
            }
        });
        ProbeActions.registerTimedEntity(Dress.TAKE_OFF, new ProbeActions.TimedEntity() {
            @Override
            public int ticks(ServerPlayer player, Entity entity, int arg) {
                if (entity instanceof Player target && holds(player, entity, arg)) {
                    return ArmorConfig.get().assistTicks();
                }
                Dress.refuse(player, "take_off");
                return 0;
            }

            @Override
            public boolean holds(ServerPlayer player, Entity entity, int arg) {
                return entity instanceof Player target && arg >= 0 && arg < Dress.SLOTS.length
                        && Dress.canTakeOff(player, target, Dress.SLOTS[arg]);
            }

            @Override
            public boolean perform(ServerPlayer player, Entity entity, int arg) {
                if (holds(player, entity, arg)) {
                    Dress.takeOff(player, (Player) entity, Dress.SLOTS[arg]);
                }
                return true;
            }
        });
        ProbeActions.registerTimedEntity(Dress.TO_STAND, new ProbeActions.TimedEntity() {
            @Override
            public int ticks(ServerPlayer player, Entity entity, int arg) {
                if (holds(player, entity, arg)) {
                    return ArmorConfig.get().standTicks();
                }
                Dress.refuse(player, "stand");
                return 0;
            }

            @Override
            public boolean holds(ServerPlayer player, Entity entity, int arg) {
                return entity instanceof ArmorStand stand && arg >= 0 && arg < Dress.SLOTS.length
                        && Dress.canToStand(player, stand, Dress.SLOTS[arg]);
            }

            @Override
            public boolean perform(ServerPlayer player, Entity entity, int arg) {
                if (holds(player, entity, arg)) {
                    Dress.toStand(player, (ArmorStand) entity, Dress.SLOTS[arg]);
                }
                return true;
            }
        });
    }

    static void registerClient() {
        ProbeProviders.addEntities(com.wf.wflib.armor.api.ArmorPreset.rl("dress"), e -> e instanceof Player || e instanceof ArmorStand,
                (info, ctx, entity) -> {
                    Player self = ctx.player();
                    if (entity instanceof ArmorStand stand) {
                        for (int i = 0; i < Dress.SLOTS.length; i++) {
                            if (Dress.canToStand(self, stand, Dress.SLOTS[i])) {
                                info.action(row(Dress.TO_STAND, i, "wflib.dress.to_stand",
                                        self.getItemBySlot(Dress.SLOTS[i]), ArmorConfig.get().standTicks()));
                            }
                        }
                        return;
                    }
                    Player target = (Player) entity;
                    EquipmentSlot put = Dress.putOnSlot(self, target);
                    if (put != null) {
                        info.action(row(Dress.PUT_ON, 0, "wflib.dress.put_on", self.getMainHandItem(),
                                ArmorConfig.get().assistTicks()));
                    }
                    for (int i = 0; i < Dress.SLOTS.length; i++) {
                        if (Dress.canTakeOff(self, target, Dress.SLOTS[i])) {
                            info.action(row(Dress.TAKE_OFF, i, "wflib.dress.take_off",
                                    target.getItemBySlot(Dress.SLOTS[i]), ArmorConfig.get().assistTicks()));
                        }
                    }
                }, ProbeProviders.CAPABILITY_PRIORITY);
    }

    private static ProbeAction row(ResourceLocation id, int arg, String key, ItemStack stack, int ticks) {
        return ProbeAction.of(id, arg, Component.translatable(key, stack.getHoverName())).timed(ticks);
    }
}
