package com.wf.wflib.hazard.api;

import com.wf.wflib.hazard.HazardConfig;
import com.wf.wflib.radiation.api.RadiationConfig;
import com.wf.wflib.hazard.type.HazardTypeRadiation;
import com.wf.wflib.core.api.OrderedRegistry;
import net.neoforged.neoforge.server.ServerLifecycleHooks;
import net.minecraft.core.Holder;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerListener;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.block.Block;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.concurrent.*;
import com.wf.wflib.hazard.TickPhase;

public final class HazardRules {

    /** Built-in transformers in registration order, then dependents'; read off-thread, indexed so the hot resolve allocates no iterator. */
    public static final OrderedRegistry<IHazardTransformer> TRANSFORMERS =
            OrderedRegistry.create("hazard", "hazard transformers");

    /**
     * Published merged tables, swapped wholesale by {@link #publish}, which on a remote client runs beside tooltip folds
     */
    private static volatile Map<TagKey<Item>, HazardData> tagMap = Map.of();
    private static volatile Map<Item, HazardData> itemMap = Map.of();
    private static final Map<Item, HazardData> CODE_ITEMS = new ConcurrentHashMap<>();
    private static final Map<TagKey<Item>, HazardData> CODE_TAGS = new ConcurrentHashMap<>();
    private static volatile Map<Item, HazardData> dataItems = Map.of();
    private static volatile Map<TagKey<Item>, HazardData> dataTags = Map.of();
    private static final HashSet<Item> itemBlacklist = new HashSet<>();
    private static final HashSet<TagKey<Item>> tagBlacklist = new HashSet<>();

    /**
     * Floor on the dose before activation starts; below it the write would be rounded away anyway.
     */
    private static final double MIN_RAD_RATE = 0.000005D;

    private static final ConcurrentHashMap<Item, HazardData[]> chronologyCache = new ConcurrentHashMap<>();

    private static final HazardData[] NO_HAZARD_DATA = new HazardData[0];

    /**
     * Writes are banked to this cadence because every one rewrites a stack and ships it to the client.
     */
    public static final int ACTIVATION_WRITE_PERIOD = 20;

    private static final int TRACKED_SLOTS = Inventory.SLOT_OFFHAND + 1;

    private static final int OFFLINE_SWEEP_PERIOD = 100;

    private static final HazardEntry[] NO_ENTRIES = new HazardEntry[0];

    private static final Applicator[] EMPTY_MEMO = new Applicator[0];

    private static final EquipmentSlot[] EQUIPMENT_SLOTS = EquipmentSlot.values();

    private static final ConcurrentHashMap<UUID, PlayerHazardData> playerData = new ConcurrentHashMap<>();
    private static final Queue<InventoryDelta> inventoryDeltas = new ConcurrentLinkedQueue<>();
    private static volatile boolean cachesDirty;
    private static final Executor EXECUTOR = ForkJoinPool.commonPool();
    private static volatile CompletableFuture<Void> scanFuture = CompletableFuture.completedFuture(null);

    private HazardRules() {
    }

    /** Published item rows: code plane under datapack rows. */
    public static Map<Item, HazardData> itemData() {
        return Collections.unmodifiableMap(itemMap);
    }

    public static Map<TagKey<Item>, HazardData> tagData() {
        return Collections.unmodifiableMap(tagMap);
    }

    /**
     * Code plane + the shipped {@code wflib:hazard} registry. Server calls it at {@code serverStarting}, ahead
     * of a hazard block memoising its rate; a remote client calls it off the synced registry, else its
     * tooltips read nothing.
     */
    public static void applyDataPack(RegistryAccess registries) {
        Map<Item, HazardData> items = new HashMap<>();
        Map<TagKey<Item>, HazardData> tags = new HashMap<>();

        registries.registry(HazardAssignment.REGISTRY).ifPresent(registry -> {
            for (HazardAssignment assignment : registry.stream().toList()) {
                HazardData data = assignment.toData();
                // A named set keeps its tag, so a pack extending that tag still reaches the binding; a direct
                // set has no tag to key on and lands per member.
                assignment.targets().unwrap().ifLeft(tag -> tags.put(tag, data))
                        .ifRight(members -> {
                            for (Holder<Item> holder : members) items.put(holder.value(), data);
                        });
            }
        });
        dataItems = items;
        dataTags = tags;
        publish();
    }

    /** Code plane over which the datapack plane rebuilds; both generations are published before the memo drops. */
    private static synchronized void publish() {
        Map<Item, HazardData> items = new HashMap<>(CODE_ITEMS);
        items.putAll(dataItems);
        Map<TagKey<Item>, HazardData> tags = new HashMap<>(CODE_TAGS);
        tags.putAll(dataTags);
        tagMap = tags;
        itemMap = items;
        clearCaches();
    }

    public static void register(TagKey<Item> tag, HazardData data) {
        CODE_TAGS.put(tag, data);
        publish();
    }

    public static void register(Item item, HazardData data) {
        CODE_ITEMS.put(item, data);
        publish();
    }

    public static void register(Block block, HazardData data) {
        register(block.asItem(), data);
    }

    public static void register(ItemStack stack, HazardData data) {
        if (!stack.isEmpty()) register(stack.getItem(), data);
    }

    /** Drops a code-plane row. */
    public static void unregister(Item item) {
        CODE_ITEMS.remove(item);
        publish();
    }

    public static void unregister(TagKey<Item> tag) {
        CODE_TAGS.remove(tag);
        publish();
    }

    public static void blacklist(Item item) {
        itemBlacklist.add(item);
    }

    public static void blacklist(TagKey<Item> tag) {
        tagBlacklist.add(tag);
    }

    public static void unblacklist(Item item) {
        itemBlacklist.remove(item);
    }

    public static boolean isItemBlacklisted(ItemStack stack) {
        if (stack.isEmpty()) return false;
        if (!itemBlacklist.isEmpty() && itemBlacklist.contains(stack.getItem())) return true;
        if (!tagBlacklist.isEmpty()) {
            Iterator<TagKey<Item>> it = BuiltInRegistries.ITEM.wrapAsHolder(stack.getItem()).tags().iterator();
            while (it.hasNext()) {
                if (tagBlacklist.contains(it.next())) return true;
            }
        }
        return false;
    }

    public static List<HazardEntry> getHazardsFromStack(ItemStack stack) {
        if (stack.isEmpty() || isItemBlacklisted(stack)) return Collections.emptyList();
        return computeHazards(stack, stack.getItem());
    }

    /**
     * The common case allocates nothing: the chronology is cached and the entry list waits for a hit.
     */
    private static List<HazardEntry> computeHazards(ItemStack stack, Item item) {
        HazardData[] chronological = chronologyCache.get(item);
        if (chronological == null) chronological = chronologyCache.computeIfAbsent(item, HazardRules::chronologyFor);
        List<HazardEntry> entries = null;

        if (chronological.length > 0) {
            entries = new ArrayList<>();
            int mutex = 0;
            for (HazardData data : chronological) {
                if ((data.getMutex() & mutex) == 0) {
                    entries.addAll(data.entries);
                    mutex |= data.getMutex();
                }
            }
        }

        List<IHazardTransformer> transformers = TRANSFORMERS.values();
        for (int i = 0, n = transformers.size(); i < n; i++) {
            IHazardTransformer t = transformers.get(i);
            if (!t.appliesTo(stack)) continue;
            if (entries == null) entries = new ArrayList<>(2);
            t.transform(stack, entries);
        }
        return entries == null ? Collections.emptyList() : entries;
    }

    /**
     * The order the fold reads, and it is TOTAL: priority ascending, then a tag before a direct binding, then
     * the binding's own name. An item's tag set is a {@code Set.copyOf} whose iteration order is salted per
     * JVM ({@code Holder.Reference.bindTags}), so taking it as the order answers differently between runs -
     * and the fold's mutex arm resolves on exactly that order, dropping whichever row it happens to see
     * second.
     */
    private static HazardData[] chronologyFor(Item item) {
        List<Sourced> data = new ArrayList<>();
        Iterator<TagKey<Item>> it = BuiltInRegistries.ITEM.wrapAsHolder(item).tags().iterator();
        while (it.hasNext()) {
            TagKey<Item> tag = it.next();
            HazardData d = tagMap.get(tag);
            if (d != null) data.add(new Sourced(d, false, tag.location().toString()));
        }
        HazardData itemData = itemMap.get(item);
        if (itemData != null) {
            data.add(new Sourced(itemData, true, BuiltInRegistries.ITEM.getKey(item).toString()));
        }
        if (data.size() > 1) data.sort(Sourced.ORDER);
        HazardData[] out = new HazardData[data.size()];
        for (int i = 0; i < out.length; i++) out[i] = data.get(i).data();
        return out;
    }

    private record Sourced(HazardData data, boolean direct, String name) {

        static final Comparator<Sourced> ORDER = Comparator
                .comparingInt((Sourced s) -> s.data().priority)
                .thenComparing(Sourced::direct)
                .thenComparing(Sourced::name);
    }

    public static boolean isStackHazardous(ItemStack stack) {
        return !stack.isEmpty() && !getHazardsFromStack(stack).isEmpty();
    }

    public static double getHazardLevelFromStack(ItemStack stack, IHazardType hazard) {
        for (HazardEntry e : getHazardsFromStack(stack)) {
            if (e.type == hazard) return IHazardModifier.evalAllModifiers(stack, null, e.baseLevel, e.getMods());
        }
        return 0D;
    }

    // 1.12.2: sums every radiation entry; getHazardLevelFromStack reads 1.7's first.
    public static double getRawRadsFromStack(ItemStack stack) {
        double total = 0D;
        for (HazardEntry e : getHazardsFromStack(stack)) {
            if (e.type == Hazards.RADIATION) {
                total += IHazardModifier.evalAllModifiers(stack, null, e.baseLevel, e.getMods());
            }
        }
        return total;
    }

    // 1.12.2: client tooltip aggregator.
    public static void addHazardInfo(ItemStack stack, @Nullable Player player, List<Component> list, TooltipFlag flag) {
        for (HazardEntry hazard : getHazardsFromStack(stack)) {
            hazard.type.addHazardInformation(player, list, hazard.baseLevel, stack, hazard.getMods());
        }
        float activation = ContaminationUtil.getNeutronRads(stack);
        if (activation > 0F) {
            HazardTypeRadiation.appendRadiationLines(list, activation / stack.getCount(), stack.getCount());
        }
    }

    // 1.12.2: runtime; ItemStack throws pre-freeze.
    public static double getRawRadsFromBlock(Block block) {
        return getHazardLevelFromStack(new ItemStack(block.asItem()), Hazards.RADIATION);
    }

    public static void applyHazards(ItemStack stack, LivingEntity entity) {
        if (stack.isEmpty()) return;
        for (HazardEntry e : getHazardsFromStack(stack)) e.applyHazard(stack, entity);
    }

    public static void applyHazards(Block block, LivingEntity entity) {
        applyHazards(new ItemStack(block.asItem()), entity);
    }

    public static void updateDroppedItem(ItemEntity entity) {
        if (entity.level().isClientSide() || entity.isRemoved()) return;
        ItemStack stack = entity.getItem();
        if (stack.isEmpty()) return;
        for (HazardEntry e : getHazardsFromStack(stack)) {
            e.type.updateEntity(entity, IHazardModifier.evalAllModifiers(stack, null, e.baseLevel, e.getMods()));
        }
    }

    /**
     * the clear is deferred to {@link #dispatchScan}, the one point no resolve is in flight:
     *
     * <p>A remote client has neither a scan nor a tick to drain the flag
     */
    public static void clearCaches() {
        if (ServerLifecycleHooks.getCurrentServer() == null) {
            chronologyCache.clear();
            return;
        }
        cachesDirty = true;
    }

    /**
     * The handed-over stack is vanilla's lastSlots copy, immutable after broadcast - safe to read off-thread.
     */
    public static void onSlotChanged(ServerPlayer player, Slot slot, ItemStack stack) {
        // 1.7: the crafting grid and its result are not hazard sources.
        if (slot.container != player.getInventory()) return;
        enqueue(player, slot.getContainerSlot(), stack);
    }

    private static void enqueue(ServerPlayer player, int slot, ItemStack stack) {
        if (slot < 0 || slot >= TRACKED_SLOTS) return;
        UUID id = player.getUUID();
        // get() first: computeIfAbsent's lambda captures the player, so it allocates on every call.
        PlayerHazardData phd = playerData.get(id);
        if (phd == null) phd = playerData.computeIfAbsent(id, u -> new PlayerHazardData(player));
        phd.player = player;
        inventoryDeltas.add(new InventoryDelta(id, slot, stack, phd));
    }

    public static void onServerTick(MinecraftServer server) {
        List<ServerPlayer> players = server.getPlayerList().getPlayers();
        // A leave offset by a join in the same tick leaves the counts equal, so the sweep cannot rest on them;
        // a retained entry pins the departed player's entity.
        if (playerData.size() > players.size() || server.getTickCount() % OFFLINE_SWEEP_PERIOD == 0) {
            forgetOfflinePlayers(server);
        }

        for (ServerPlayer player : players) {
            if (!TickPhase.every(player, HazardConfig.get().hazardRate()) || player.isRemoved()) continue;
            PlayerHazardData phd = playerData.get(player.getUUID());
            if (phd == null) continue;
            phd.player = player;
            phd.applyActiveHazards();
        }

        if (!scanFuture.isDone()) return;
        if (!cachesDirty && inventoryDeltas.isEmpty()) return;
        dispatchScan(server);
    }

    private static void forgetOfflinePlayers(MinecraftServer server) {
        Iterator<UUID> it = playerData.keySet().iterator();
        while (it.hasNext()) {
            if (server.getPlayerList().getPlayer(it.next()) == null) it.remove();
        }
    }

    /**
     * Item-entity tick, not the 1.7 world sweep (ModEventHandler:608-623).
     */
    public static void tickDroppedItem(ItemEntity entity) {
        if (!TickPhase.every(entity, HazardConfig.get().itemHazardDropTickrate())) return;
        updateDroppedItem(entity);
    }

    /**
     * 1.12.2: cadence divergence from 1.7 updateLivingInventory - CE scales by hazardRate. Any future
     * cadence change keeps that multiplier.
     */
    public static void tickMobEquipment(LivingEntity entity) {
        if (entity.level().isClientSide() || entity instanceof Player) return;
        if (!TickPhase.every(entity, HazardConfig.get().hazardRate())) return;
        for (EquipmentSlot slot : EQUIPMENT_SLOTS) {
            applyHazards(entity.getItemBySlot(slot), entity);
        }
    }

    private static void dispatchScan(MinecraftServer server) {
        if (cachesDirty) {
            cachesDirty = false;
            chronologyCache.clear();
            for (ServerPlayer player : server.getPlayerList().getPlayers()) requeueWholeInventory(player);
        }
        List<InventoryDelta> deltas = new ArrayList<>();
        InventoryDelta delta;
        while ((delta = inventoryDeltas.poll()) != null) deltas.add(delta);
        if (deltas.isEmpty()) return;

        scanFuture = CompletableFuture.runAsync(() -> resolve(deltas), EXECUTOR);
    }

    /**
     * Empty slots included so their memo entries drop; stacks are copied because these are live.
     */
    private static void requeueWholeInventory(ServerPlayer player) {
        PlayerHazardData phd = playerData.get(player.getUUID());
        if (phd == null) return;
        Inventory inv = player.getInventory();
        for (int i = 0; i <= Inventory.SLOT_OFFHAND; i++) {
            inventoryDeltas.add(new InventoryDelta(player.getUUID(), i, inv.getItem(i).copy(), phd));
        }
    }

    private static void resolve(List<InventoryDelta> deltas) {
        for (InventoryDelta d : deltas) {
            PlayerHazardData phd = playerData.get(d.uuid());
            if (phd == null || phd != d.phd()) continue; // gone or recreated -> skip
            ItemStack ns = d.newStack();
            List<HazardEntry> h = getHazardsFromStack(ns);
            float activation = activationOf(ns, h);
            if (h.isEmpty() && activation == 0F) phd.removeApplicator(d.slot());
            else phd.setApplicator(d.slot(), new Applicator(ns.getItem(), h.toArray(NO_ENTRIES), activation));
        }
    }

    /**
     * Activation alone; a stack the registry makes radioactive reports its configured dose, never both.
     */
    private static float activationOf(ItemStack stack, List<HazardEntry> hazards) {
        Float stored = stack.get(HazardContent.NEUTRON_ACTIVATION.get());
        if (stored == null || stored <= 0F) return 0F;
        for (HazardEntry e : hazards) {
            if (e.type == Hazards.RADIATION
                    && IHazardModifier.evalAllModifiers(stack, null, e.baseLevel, e.getMods()) > 0D) {
                return 0F;
            }
        }
        return stored * stack.getCount();
    }

    private record Applicator(Item item, HazardEntry[] entries, float activation) {
    }

    private record InventoryDelta(UUID uuid, int slot, ItemStack newStack, @Nullable PlayerHazardData phd) {
    }

    /**
     * Equality is the player, so a re-initialised menu does not stack a second listener.
     */
    public record MenuWatcher(ServerPlayer player) implements ContainerListener {

        @Override
        public void slotChanged(AbstractContainerMenu menu, int slotIndex, ItemStack stack) {
            onSlotChanged(player, menu.getSlot(slotIndex), stack);
        }

        @Override
        public void dataChanged(AbstractContainerMenu menu, int id, int value) {
        }
    }

    private static final class PlayerHazardData {
        Player player;
        /**
         * Slot-indexed, copy-on-write; an array so a rebuild is one copyOf and the pass allocates no iterator.
         */
        private volatile @Nullable Applicator[] applicators = EMPTY_MEMO;
        private volatile float carriedActivation;
        private double owedActivation;

        PlayerHazardData(Player player) {
            this.player = player;
        }

        void setApplicator(int slot, Applicator app) {
            Applicator[] next = Arrays.copyOf(applicators, TRACKED_SLOTS);
            next[slot] = app;
            publish(next);
        }

        void removeApplicator(int slot) {
            Applicator[] current = applicators;
            if (slot >= current.length || current[slot] == null) return;
            Applicator[] next = Arrays.copyOf(current, TRACKED_SLOTS);
            next[slot] = null;
            publish(next);
        }

        /**
         * Summed rather than adjusted by delta, which would let a running float drift.
         */
        private void publish(Applicator[] next) {
            float carried = 0F;
            for (Applicator a : next) if (a != null) carried += a.activation();
            applicators = next;
            carriedActivation = carried;
        }

        void applyActiveHazards() {
            if (player.isRemoved()) return;
            pollWhileForeignMenuOpen();
            Applicator[] memo = applicators;
            Inventory inv = player.getInventory();
            for (int slot = 0; slot < memo.length; slot++) {
                Applicator app = memo[slot];
                if (app == null || app.entries().length == 0) continue;
                ItemStack live = inv.getItem(slot);
                if (live.isEmpty() || live.getItem() != app.item())
                    continue; // memo lags a swap - skip until re-resolved
                for (HazardEntry entry : app.entries()) entry.applyHazard(live, player);
            }
            applyNeutronActivation();
        }

        /**
         * Only the open menu broadcasts, and no menu but the player's own carries armour and offhand.
         */
        private void pollWhileForeignMenuOpen() {
            if (!(player instanceof ServerPlayer sp) || sp.containerMenu == sp.inventoryMenu) return;
            sp.inventoryMenu.suppressRemoteUpdates();
            try {
                sp.inventoryMenu.broadcastChanges();
            } finally {
                sp.inventoryMenu.resumeRemoteUpdates();
            }
        }

        private void applyNeutronActivation() {
            if (!RadiationConfig.get().neutronActivation()) return;

            float carried = carriedActivation;
            if (carried > 0F) {
                ContaminationUtil.contaminate(player, ContaminationUtil.HazardType.NEUTRON,
                        ContaminationUtil.ContaminationType.CREATIVE,
                        carried * 0.05D * HazardConfig.get().hazardRate());
            } else {
                LivingHazardProps.setNeutron(player, 0D);
            }

            double rate = ContaminationUtil.getNoNeutronPlayerRads(player) * 0.00004D
                    - 0.00004D * RadiationConfig.get().neutronActivationThreshold();
            if (rate > MIN_RAD_RATE) owedActivation += rate * HazardConfig.get().hazardRate();
            if (owedActivation <= 0D) return;

            // One pass per window, phase-spread per player, so a crowd does not rewrite together.
            long time = player.level().getGameTime();
            if (Math.floorMod(time + player.getId(), ACTIVATION_WRITE_PERIOD) >= HazardConfig.get().hazardRate()) return;

            float owed = (float) owedActivation;
            // Under the floor nothing would be recorded, so keep banking rather than throwing it away.
            if (owed < ContaminationUtil.ACTIVATION_FLOOR) return;
            owedActivation = 0D;
            ContaminationUtil.neutronActivateInventory(player, owed, 1.0F);
        }
    }
}
