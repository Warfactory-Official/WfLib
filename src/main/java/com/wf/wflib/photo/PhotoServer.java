package com.wf.wflib.photo;

import com.mojang.logging.LogUtils;
import com.wf.wflib.photo.api.PhotoStoredEvent;
import com.wf.wflib.photo.api.PhotoTakenEvent;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.ChatFormatting;
import net.minecraft.Util;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.network.PacketDistributor;
import org.slf4j.Logger;

/**
 * Server end of the photo system. A shot is a server-issued token (so the pose in {@link PhotoTakenEvent} is the one the
 * picture is rendered from); the client uploads JPEG fragments tagged with it; the server checks size and header only,
 * never decodes. Files live in {@link PhotoFiles}; downloads stream {@link #CHUNKS_PER_TICK} chunks per tick per player.
 */
public final class PhotoServer {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final int CHUNKS_PER_TICK = 4;
    private static final long LIST_GAP_MS = 500;

    private static final class Pending {
        final long token;
        final long expires;
        final String dimension;
        final Vec3 eye;
        final float yaw;
        final float pitch;
        Upload upload;

        Pending(long token, long expires, String dimension, Vec3 eye, float yaw, float pitch) {
            this.token = token;
            this.expires = expires;
            this.dimension = dimension;
            this.eye = eye;
            this.yaw = yaw;
            this.pitch = pitch;
        }
    }

    private static final class Transfer {
        final int id;
        final byte[] data;
        final int total;
        int next;

        Transfer(int id, byte[] data) {
            this.id = id;
            this.data = data;
            this.total = (data.length + PhotoLimits.DOWN_CHUNK - 1) / PhotoLimits.DOWN_CHUNK;
        }
    }

    /** Server thread only. */
    private static final Map<UUID, Pending> PENDING = new HashMap<>();
    private static final Map<UUID, Transfer> DOWN = new HashMap<>();
    private static final Map<UUID, Integer> LOADING = new HashMap<>();
    private static final Map<UUID, Long> LAST_LIST = new HashMap<>();

    private PhotoServer() {
    }

    // ---- shutter -----------------------------------------------------------------------------------------

    /** The camera was used: {@code fov} = the vertical field of view of the picture, degrees. @return false when refused (gallery full, no film). */
    public static boolean shutter(ServerPlayer player, float fov) {
        float lens = Math.max(PhotoLimits.MIN_FOV, Math.min(PhotoLimits.MAX_FOV, fov));
        PhotoBook book = PhotoBook.get(player.server);
        if (book.count(player.getUUID()) >= PhotoConfig.MAX_PER_PLAYER.get()) {
            player.displayClientMessage(Component.translatable("wflib.photo.full").withStyle(ChatFormatting.RED), true);
            return false;
        }
        if (!pay(player, PhotoConfig.FILM_ITEM.get())) {
            player.displayClientMessage(Component.translatable("wflib.photo.no_film").withStyle(ChatFormatting.RED), true);
            return false;
        }
        long token = player.getRandom().nextLong();
        Vec3 eye = player.getEyePosition();
        float yaw = player.getYRot();
        float pitch = player.getXRot();
        PENDING.put(player.getUUID(), new Pending(token, player.server.getTickCount() + PhotoLimits.TOKEN_TICKS,
                player.level().dimension().location().toString(), eye, yaw, pitch));
        player.level().playSound(null, player.blockPosition(), SoundEvents.TRIPWIRE_CLICK_ON, SoundSource.PLAYERS, 0.8f,
                1.6f);
        NeoForge.EVENT_BUS.post(new PhotoTakenEvent(player, player.serverLevel(), eye, yaw, pitch, lens));
        PacketDistributor.sendToPlayer(player, new PhotoShutterPacket(token, eye.x, eye.y, eye.z, yaw, pitch, lens));
        return true;
    }

    // ---- upload ------------------------------------------------------------------------------------------

    static void upload(ServerPlayer player, PhotoUploadPacket packet) {
        Pending pending = PENDING.get(player.getUUID());
        if (pending == null || pending.token != packet.token() || player.server.getTickCount() > pending.expires) {
            PENDING.remove(player.getUUID());
            return;
        }
        if (pending.upload == null) {
            pending.upload = Upload.start(packet.token(), packet.total());
            if (pending.upload == null) {
                reject(player);
                return;
            }
        }
        byte[] whole = pending.upload.accept(packet.index(), packet.total(), packet.data());
        if (pending.upload.dead()) {
            reject(player);
            return;
        }
        if (whole == null) {
            return;
        }
        PENDING.remove(player.getUUID());
        int[] size = Jpeg.size(whole);
        if (size == null || size[0] > PhotoLimits.MAX_WIDTH || size[1] > PhotoLimits.MAX_HEIGHT
                || size[0] < PhotoLimits.MIN_SIDE || size[1] < PhotoLimits.MIN_SIDE) {
            player.displayClientMessage(Component.translatable("wflib.photo.rejected").withStyle(ChatFormatting.RED), true);
            return;
        }
        store(player, pending, whole);
    }

    private static void reject(ServerPlayer player) {
        PENDING.remove(player.getUUID());
        player.displayClientMessage(Component.translatable("wflib.photo.rejected").withStyle(ChatFormatting.RED), true);
    }

    private static void store(ServerPlayer player, Pending shot, byte[] bytes) {
        MinecraftServer server = player.server;
        UUID id = UUID.randomUUID();
        Path root = PhotoFiles.root(server);
        Util.backgroundExecutor().execute(() -> {
            try {
                PhotoFiles.write(root, id, bytes);
            } catch (java.io.IOException e) {
                LOGGER.warn("photo {} not saved: {}", id, e.toString());
                server.execute(() -> player.displayClientMessage(
                        Component.translatable("wflib.photo.rejected").withStyle(ChatFormatting.RED), true));
                return;
            }
            server.execute(() -> {
                PhotoEntry entry = new PhotoEntry(id, player.getUUID(), player.getGameProfile().getName(),
                        System.currentTimeMillis(), shot.dimension, (int) Math.floor(shot.eye.x),
                        (int) Math.floor(shot.eye.y), (int) Math.floor(shot.eye.z), shot.yaw, shot.pitch, bytes.length,
                        false);
                if (!PhotoBook.get(server).add(entry, PhotoConfig.MAX_PER_PLAYER.get())) {
                    PhotoFiles.delete(root, id);
                    return;
                }
                NeoForge.EVENT_BUS.post(new PhotoStoredEvent(player, id));
                if (!player.hasDisconnected()) {
                    player.displayClientMessage(Component.translatable("wflib.photo.saved")
                            .withStyle(ChatFormatting.GREEN), true);
                    pushList(player);
                }
            });
        });
    }

    // ---- list --------------------------------------------------------------------------------------------

    static void list(ServerPlayer player) {
        long now = Util.getMillis();
        Long last = LAST_LIST.put(player.getUUID(), now);
        if (last != null && now - last < LIST_GAP_MS) {
            return;
        }
        pushList(player);
    }

    private static void pushList(ServerPlayer player) {
        List<PhotoEntry> mine = PhotoBook.get(player.server).of(player.getUUID());
        List<PhotoListPacket.Row> rows = mine.stream().limit(PhotoListPacket.MAX_ROWS).map(PhotoListPacket.Row::of)
                .toList();
        PacketDistributor.sendToPlayer(player, new PhotoListPacket(rows));
    }

    // ---- download ----------------------------------------------------------------------------------------

    static void fetch(ServerPlayer player, int transfer, UUID id) {
        PhotoEntry entry = PhotoBook.get(player.server).get(id);
        if (entry == null || !entry.shared() && !entry.owner().equals(player.getUUID())
                || LOADING.getOrDefault(player.getUUID(), 0) >= 2) {
            PacketDistributor.sendToPlayer(player, new PhotoChunkPacket(transfer, 0, 0, new byte[0]));
            return;
        }
        MinecraftServer server = player.server;
        Path root = PhotoFiles.root(server);
        LOADING.merge(player.getUUID(), 1, Integer::sum);
        Util.backgroundExecutor().execute(() -> {
            byte[] bytes = PhotoFiles.read(root, id);
            server.execute(() -> {
                LOADING.merge(player.getUUID(), -1, Integer::sum);
                if (player.hasDisconnected()) {
                    return;
                }
                if (bytes == null) {
                    PacketDistributor.sendToPlayer(player, new PhotoChunkPacket(transfer, 0, 0, new byte[0]));
                } else {
                    DOWN.put(player.getUUID(), new Transfer(transfer, bytes));
                }
            });
        });
    }

    // ---- delete / print ----------------------------------------------------------------------------------

    static void action(ServerPlayer player, int op, UUID id) {
        PhotoBook book = PhotoBook.get(player.server);
        if (op == PhotoActionPacket.DELETE) {
            if (book.delete(id, player.getUUID()) != null) {
                Path root = PhotoFiles.root(player.server);
                Util.backgroundExecutor().execute(() -> PhotoFiles.delete(root, id));
                pushList(player);
            }
        } else if (op == PhotoActionPacket.PRINT) {
            PhotoEntry entry = book.get(id);
            if (entry == null || !entry.owner().equals(player.getUUID())) {
                return;
            }
            if (!pay(player, PhotoConfig.PRINT_ITEM.get())) {
                player.displayClientMessage(Component.translatable("wflib.photo.no_paper")
                        .withStyle(ChatFormatting.RED), true);
                return;
            }
            book.share(id, player.getUUID());
            ItemStack stack = new ItemStack(PhotoSystem.PHOTO_ITEM.get());
            stack.set(PhotoSystem.PHOTO_REF.get(), new PhotoRef(id, entry.ownerName()));
            if (!player.getInventory().add(stack)) {
                ItemEntity drop = player.drop(stack, false);
                if (drop != null) {
                    drop.setNoPickUpDelay();
                    drop.setTarget(player.getUUID());
                }
            }
            pushList(player);
        }
    }

    /** Takes one of {@code itemId} from the player's inventory; true when free (empty id, unknown id) or creative. */
    private static boolean pay(ServerPlayer player, String itemId) {
        if (itemId.isBlank() || player.isCreative()) {
            return true;
        }
        ResourceLocation key = ResourceLocation.tryParse(itemId.strip());
        Item item = key == null ? null : BuiltInRegistries.ITEM.getOptional(key).orElse(null);
        if (item == null) {
            return true;
        }
        var inv = player.getInventory();
        for (int i = 0; i < inv.getContainerSize(); i++) {
            ItemStack stack = inv.getItem(i);
            if (stack.is(item)) {
                stack.shrink(1);
                return true;
            }
        }
        return false;
    }

    // ---- tick / lifecycle --------------------------------------------------------------------------------

    /** Once per server tick. */
    public static void tick(MinecraftServer server) {
        if (!DOWN.isEmpty()) {
            var it = DOWN.entrySet().iterator();
            while (it.hasNext()) {
                var e = it.next();
                ServerPlayer player = server.getPlayerList().getPlayer(e.getKey());
                Transfer t = e.getValue();
                if (player == null) {
                    it.remove();
                    continue;
                }
                for (int n = 0; n < CHUNKS_PER_TICK && t.next < t.total; n++, t.next++) {
                    int from = t.next * PhotoLimits.DOWN_CHUNK;
                    int to = Math.min(t.data.length, from + PhotoLimits.DOWN_CHUNK);
                    PacketDistributor.sendToPlayer(player,
                            new PhotoChunkPacket(t.id, t.next, t.total, Arrays.copyOfRange(t.data, from, to)));
                }
                if (t.next >= t.total) {
                    it.remove();
                }
            }
        }
        if (!PENDING.isEmpty()) {
            int now = server.getTickCount();
            PENDING.values().removeIf(p -> now > p.expires);
        }
    }

    public static void forget(UUID player) {
        PENDING.remove(player);
        DOWN.remove(player);
        LOADING.remove(player);
        LAST_LIST.remove(player);
    }

    public static void clear() {
        PENDING.clear();
        DOWN.clear();
        LOADING.clear();
        LAST_LIST.clear();
    }
}
