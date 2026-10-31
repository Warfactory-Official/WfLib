package com.wf.wflib.armor.api.exo;

import com.wf.wflib.armor.ArmorConfig;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.EnumMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Per-player exo runtime. Energy is prepaid in {@link #PREPAY_SECONDS} chunks: debiting the battery every interval
 * would be an equipment-sync packet per interval.
 */
public final class ExoServer {
    public static final double PREPAY_SECONDS = 5;
    private static final EquipmentSlot[] SLOTS = {EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET};

    private static final class Piece {
        double budget;
        boolean powered = true;
    }

    private static final class Runtime {
        final Map<EquipmentSlot, Piece> pieces = new EnumMap<>(EquipmentSlot.class);
        ExoEffect effect = ExoEffect.NONE;
        boolean sent;
        long nextLifeUse;
    }

    private static final Map<UUID, Runtime> RUNTIMES = new ConcurrentHashMap<>();

    private ExoServer() {
    }

    public static ExoEffect effect(Player player) {
        Runtime r = RUNTIMES.get(player.getUUID());
        return r == null ? ExoEffect.NONE : r.effect;
    }

    public static void tick(MinecraftServer server) {
        int interval = Math.max(1, ArmorConfig.get().exposureIntervalTicks());
        if (server.getTickCount() % interval != 0) {
            return;
        }
        for (ServerPlayer p : server.getPlayerList().getPlayers()) {
            recompute(p, true);
            lifeSupport(p);
        }
    }

    public static void lifeSupport(ServerPlayer player) {
        Runtime r = RUNTIMES.get(player.getUUID());
        long now = player.level().getGameTime();
        if (r == null || !r.effect.lifeSupport() || now < r.nextLifeUse || !player.isAlive()
                || !LifeSupport.inDanger(player)) {
            return;
        }
        double cost = ExoConfig.get().energyEnabled() ? ExoConfig.get().lifeSupportDraw() : 0;
        if (cost > 0 && !burst(player, cost, true)) {
            return;
        }
        if (LifeSupport.use(player)) {
            if (cost > 0) {
                burst(player, cost, false);
            }
            r.nextLifeUse = now + ExoConfig.get().lifeSupportCooldownTicks();
        }
    }

    /** One-off energy from the first worn exo battery that has it. */
    private static boolean burst(ServerPlayer player, double units, boolean simulate) {
        long fe = (long) Math.ceil(ExoConfig.get().toFe(units));
        for (EquipmentSlot slot : SLOTS) {
            ItemStack garment = player.getItemBySlot(slot);
            ExoSpec spec = ExoProfiles.of(garment);
            if (spec == null || !spec.battery()) {
                continue;
            }
            ItemStack battery = ExoStacks.battery(garment);
            if (battery.isEmpty() || ArmorBattery.extract(battery, fe, true) < fe) {
                continue;
            }
            if (!simulate) {
                ArmorBattery.extract(battery, fe, false);
                ExoStacks.setBattery(garment, battery);
            }
            return true;
        }
        return false;
    }

    /** @param spend true on the interval tick: debits energy; false: re-derive from current stacks and last power state */
    public static void recompute(ServerPlayer player, boolean spend) {
        Runtime r = RUNTIMES.computeIfAbsent(player.getUUID(), u -> new Runtime());
        ExoEffect effect = ExoEffect.NONE;
        boolean any = false;
        ExoConfig.Values cfg = ExoConfig.get();
        int interval = Math.max(1, ArmorConfig.get().exposureIntervalTicks());
        for (EquipmentSlot slot : SLOTS) {
            ItemStack stack = player.getItemBySlot(slot);
            ExoSpec spec = ExoProfiles.of(stack);
            if (spec == null) {
                r.pieces.remove(slot);
                continue;
            }
            any = true;
            Piece piece = r.pieces.computeIfAbsent(slot, s -> new Piece());
            PartState[] states = ExoStacks.states(stack, spec);
            if (!cfg.energyEnabled() || !spec.battery()) {
                piece.powered = true;
            } else if (spend) {
                piece.powered = pay(stack, piece, spec.drawPerSecond() * ExoResolver.drawMultiplier(spec, states)
                        * interval / 20.0);
            }
            effect = effect.combine(ExoResolver.effective(spec, piece.powered, states));
        }
        if (!any) {
            effect = ExoEffect.NONE;
        }
        if (!effect.equals(r.effect) || !r.sent) {
            r.effect = effect;
            r.sent = true;
            PacketDistributor.sendToPlayer(player, new ExoPackets.State(effect));
        }
    }

    /** Takes {@code need} from the prepaid budget, refilling from the battery when short. */
    private static boolean pay(ItemStack garment, Piece piece, double need) {
        if (need <= 0) {
            return true;
        }
        if (piece.budget >= need) {
            piece.budget -= need;
            return true;
        }
        ItemStack battery = ExoStacks.battery(garment);
        if (battery.isEmpty()) {
            return false;
        }
        double chunk = Math.max(need, need * PREPAY_SECONDS * 20.0 / Math.max(1, ArmorConfig.get().exposureIntervalTicks()));
        double taken = ArmorBattery.draw(battery, chunk) ? chunk : ArmorBattery.draw(battery, need) ? need : 0;
        if (taken <= 0) {
            return false;
        }
        ExoStacks.setBattery(garment, battery);
        piece.budget += taken - need;
        return true;
    }

    public static void forget(UUID player) {
        RUNTIMES.remove(player);
    }

    /** Test seam. */
    public static void reset() {
        RUNTIMES.clear();
    }
}
