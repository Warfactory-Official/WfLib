package com.wf.wflib.artifact.detect;

import com.wf.wflib.artifact.ArtifactItem;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

import java.util.function.Predicate;

/**
 * An artefact where the Zone grew it: hanging just off the ground, unseen and untouchable until a player's detector
 * comes near enough ({@link Detection#revealed}); then it shows to them (their client draws it) and is theirs to take
 * with a use. Never despawns, isn't pulled about by anomalies, can't be hurt.
 */
public class ZoneArtifact extends Entity {
    private static final EntityDataAccessor<ItemStack> ITEM = SynchedEntityData.defineId(ZoneArtifact.class,
            EntityDataSerializers.ITEM_STACK);

    /** Client: shown to the local player (set by the client side, which knows what it holds); server: always. */
    public static Predicate<ZoneArtifact> shownHere = a -> true;

    public ZoneArtifact(EntityType<? extends ZoneArtifact> type, Level level) {
        super(type, level);
        noPhysics = true;
        setNoGravity(true);
    }

    public ZoneArtifact(Level level, ItemStack stack, double x, double y, double z) {
        this(ArtifactEntities.ZONE_ARTIFACT.get(), level);
        setItem(stack);
        setPos(x, y, z);
    }

    public ItemStack item() {
        return entityData.get(ITEM);
    }

    public void setItem(ItemStack stack) {
        entityData.set(ITEM, stack.copy());
    }

    /** The lowest detector rank that finds it: STALKER's lists put the commoner ones on the Echo already. */
    public int detectRank() {
        return item().getItem() instanceof ArtifactItem a ? a.detectRank() : 1;
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        builder.define(ITEM, ItemStack.EMPTY);
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
        setItem(ItemStack.parseOptional(registryAccess(), tag.getCompound("item")));
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
        if (!item().isEmpty()) {
            tag.put("item", item().save(registryAccess()));
        }
    }

    @Override
    public void tick() {
        super.tick();
        if (!level().isClientSide && item().isEmpty()) {
            discard();
        }
    }

    /** Only what is shown can be aimed at: an unseen artefact can't be found by the crosshair. */
    @Override
    public boolean isPickable() {
        return !level().isClientSide || shownHere.test(this);
    }

    @Override
    public boolean hurt(DamageSource source, float amount) {
        return false;
    }

    @Override
    public boolean skipAttackInteraction(Entity attacker) {
        return true;
    }

    @Override
    public boolean isIgnoringBlockTriggers() {
        return true;
    }

    /** Taken with a use, by a player whose detector shows it. */
    @Override
    public InteractionResult interact(Player player, InteractionHand hand) {
        if (level().isClientSide) {
            return shownHere.test(this) ? InteractionResult.SUCCESS : InteractionResult.PASS;
        }
        if (!Detection.revealed(player, this) || item().isEmpty()) {
            return InteractionResult.PASS;
        }
        ItemStack stack = item().copy();
        if (!player.getInventory().add(stack)) {
            player.drop(stack, false);
        }
        level().playSound(null, getX(), getY(), getZ(), SoundEvents.ITEM_PICKUP, SoundSource.PLAYERS, 0.4F, 0.8F);
        discard();
        return InteractionResult.CONSUME;
    }
}
