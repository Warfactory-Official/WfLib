package com.wf.wflib.corpse.api;

import com.mojang.authlib.GameProfile;
import com.mojang.authlib.properties.Property;
import com.wf.wflib.core.api.WFSystems;
import com.wf.wflib.corpse.CorpseConfig;
import com.wf.wflib.backpack.api.Backpacks;
import com.wf.wflib.carry.api.Carry;
import com.wf.wflib.ragdoll.api.Ragdolls;
import net.minecraft.core.NonNullList;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Containers;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.Leashable;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.player.PlayerModelPart;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.nbt.DoubleTag;

/**
 * Dead player's body: entries = stack + the slot it was worn in ({@link CorpseSlot}); null kind => loose drop. Body =
 * inert ragdoll, persisted as {@link Ragdolls#state}. Equipment drawn = what is still in the dead player's armour,
 * hand and offhand slots.
 */
public class CorpseEntity extends LivingEntity implements Leashable {
    private static final EntityDataAccessor<Optional<UUID>> OWNER =
            SynchedEntityData.defineId(CorpseEntity.class, EntityDataSerializers.OPTIONAL_UUID);
    private static final EntityDataAccessor<String> NAME =
            SynchedEntityData.defineId(CorpseEntity.class, EntityDataSerializers.STRING);
    private static final EntityDataAccessor<String> SKIN =
            SynchedEntityData.defineId(CorpseEntity.class, EntityDataSerializers.STRING);
    private static final EntityDataAccessor<String> SKIN_SIGNATURE =
            SynchedEntityData.defineId(CorpseEntity.class, EntityDataSerializers.STRING);
    private static final EntityDataAccessor<Byte> PARTS =
            SynchedEntityData.defineId(CorpseEntity.class, EntityDataSerializers.BYTE);
    /** Worn backpack, contents stripped. */
    private static final EntityDataAccessor<ItemStack> BACKPACK =
            SynchedEntityData.defineId(CorpseEntity.class, EntityDataSerializers.ITEM_STACK);

    /** Lit with flint and steel: burns for {@link CorpseConfig#BURN_TICKS}, then is destroyed. */
    private static final EntityDataAccessor<Boolean> BURNING =
            SynchedEntityData.defineId(CorpseEntity.class, EntityDataSerializers.BOOLEAN);

    /** Menu / view range, feet to feet. */
    public static final double REACH = 8;
    /** Inventory keys: armour FEET..HEAD = 36..39, offhand 40. */
    private static final int ARMOR_KEY = 36, OFFHAND_KEY = 40;

    public static final class Entry {
        public final @Nullable ResourceLocation kind;
        public final String key;
        ItemStack stack;

        Entry(@Nullable ResourceLocation kind, String key, ItemStack stack) {
            this.kind = kind;
            this.key = key;
            this.stack = stack;
        }

        public ItemStack stack() {
            return stack;
        }
    }

    private final List<Entry> entries = new ArrayList<>();
    private final NonNullList<ItemStack> armor = NonNullList.withSize(4, ItemStack.EMPTY);
    private final NonNullList<ItemStack> hands = NonNullList.withSize(2, ItemStack.EMPTY);
    private int selected;
    private int age;
    private int emptyAge = -1;
    private @Nullable double[] savedBody;
    private @Nullable GameProfile profile;
    private @Nullable LeashData leashData;
    private int burnAge;

    public CorpseEntity(EntityType<? extends CorpseEntity> type, Level level) {
        super(type, level);
        blocksBuilding = false;
    }

    /** Server, before spawning. */
    public void setup(GameProfile profile, byte parts, Collection<Entry> taken, int selectedHotbar) {
        entityData.set(OWNER, Optional.of(profile.getId()));
        entityData.set(NAME, profile.getName());
        Property textures = profile.getProperties().get("textures").stream().findFirst().orElse(null);
        if (textures != null) {
            entityData.set(SKIN, textures.value());
            entityData.set(SKIN_SIGNATURE, textures.signature() == null ? "" : textures.signature());
        }
        entityData.set(PARTS, parts);
        entries.addAll(taken);
        selected = selectedHotbar;
        changed();
    }

    public static Entry entry(@Nullable ResourceLocation kind, String key, ItemStack stack) {
        return new Entry(kind, key, stack);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(OWNER, Optional.empty());
        builder.define(NAME, "");
        builder.define(SKIN, "");
        builder.define(SKIN_SIGNATURE, "");
        builder.define(PARTS, (byte) 0);
        builder.define(BACKPACK, ItemStack.EMPTY);
        builder.define(BURNING, false);
    }

    public Optional<UUID> owner() {
        return entityData.get(OWNER);
    }

    public String ownerName() {
        return entityData.get(NAME);
    }

    /** Owner's profile with the skin captured at death. */
    public GameProfile profile() {
        if (profile == null) {
            profile = new GameProfile(owner().orElse(new UUID(0, 0)), ownerName());
            String skin = entityData.get(SKIN);
            if (!skin.isEmpty()) {
                String sig = entityData.get(SKIN_SIGNATURE);
                profile.getProperties().put("textures", new Property("textures", skin, sig.isEmpty() ? null : sig));
            }
        }
        return profile;
    }

    @Override
    public void onSyncedDataUpdated(EntityDataAccessor<?> key) {
        super.onSyncedDataUpdated(key);
        if (key == OWNER || key == NAME || key == SKIN || key == SKIN_SIGNATURE) {
            profile = null;
        }
    }

    public boolean partShown(PlayerModelPart part) {
        return (entityData.get(PARTS) & part.getMask()) != 0;
    }

    public ItemStack backpack() {
        return entityData.get(BACKPACK);
    }

    public List<Entry> entries() {
        return entries;
    }

    /** Entry worn in {@code (kind, key)}, else null. */
    public @Nullable Entry at(ResourceLocation kind, String key) {
        for (Entry e : entries) {
            if (kind.equals(e.kind) && key.equals(e.key)) {
                return e;
            }
        }
        return null;
    }

    /** Entries without a fixed menu position: {@link CorpseMenu#fixed}. */
    public List<Entry> extras() {
        List<Entry> out = new ArrayList<>();
        for (Entry e : entries) {
            if (!CorpseMenu.fixed(e)) {
                out.add(e);
            }
        }
        return out;
    }

    /** After any entry edit: empties pruned, drawn equipment and backpack refreshed. */
    public void changed() {
        entries.removeIf(e -> e.stack.isEmpty());
        for (EquipmentSlot slot : EquipmentSlot.values()) {
            int key = switch (slot.getType()) {
                case HUMANOID_ARMOR -> ARMOR_KEY + slot.getIndex();
                case HAND -> slot == EquipmentSlot.MAINHAND ? selected : OFFHAND_KEY;
                default -> -1;
            };
            Entry e = key < 0 ? null : at(Corpses.INVENTORY, Integer.toString(key));
            setItemSlot(slot, e == null ? ItemStack.EMPTY : e.stack.copy());
        }
        Entry pack = at(Corpses.BACKPACK, "");
        entityData.set(BACKPACK, pack == null || !WFSystems.enabled("backpack") ? ItemStack.EMPTY
                : Backpacks.shown(pack.stack));
    }

    /** Every entry back into its slot when free, else the inventory; the rest stays. */
    public void transfer(ServerPlayer to) {
        for (Entry e : entries) {
            CorpseSlot kind = e.kind == null ? null : Corpses.slot(e.kind);
            if (kind != null && kind.put(to, e.key, e.stack)) {
                e.stack = ItemStack.EMPTY;
            } else {
                to.getInventory().add(e.stack);
            }
        }
        changed();
    }

    public boolean mayOpen(Player player) {
        return !CorpseConfig.ONLY_OWNER.get() || owner().map(player.getUUID()::equals).orElse(true)
                || player.hasPermissions(2);
    }

    public boolean burning() {
        return entityData.get(BURNING);
    }

    /** Server: sets the body alight; false when already burning. */
    public boolean ignite() {
        if (burning() || level().isClientSide) {
            return false;
        }
        entityData.set(BURNING, true);
        burnAge = 0;
        if (isLeashed()) {
            dropLeash(true, false); // the lead burns with it
        }
        Ragdolls.carry(this, null);
        level().playSound(null, getX(), getY(), getZ(), SoundEvents.FIRECHARGE_USE, SoundSource.BLOCKS, 1.0F, 1.0F);
        return true;
    }

    @Override
    public InteractionResult interact(Player player, InteractionHand hand) {
        ItemStack held = player.getItemInHand(hand);
        if (held.is(Items.FLINT_AND_STEEL)) {
            if (!level().isClientSide && ignite()) {
                held.hurtAndBreak(1, player, LivingEntity.getSlotForHand(hand));
            }
            return InteractionResult.sidedSuccess(level().isClientSide);
        }
        // A lead goes on (or comes off) like on any animal, before the menu.
        if (held.is(Items.LEAD) || getLeashHolder() == player) {
            InteractionResult lead = super.interact(player, hand);
            if (lead != InteractionResult.PASS) {
                return lead;
            }
        }
        if (!(player instanceof ServerPlayer viewer)) {
            return InteractionResult.SUCCESS;
        }
        if (!mayOpen(viewer)) {
            viewer.displayClientMessage(Component.translatable("wflib.corpse.not_yours"), true);
            return InteractionResult.FAIL;
        }
        viewer.openMenu(new SimpleMenuProvider((id, inv, p) -> new CorpseMenu(id, inv, this), getDisplayName()),
                buf -> buf.writeVarInt(getId()));
        return InteractionResult.SUCCESS;
    }

    @Override
    public @Nullable LeashData getLeashData() {
        return leashData;
    }

    @Override
    public void setLeashData(@Nullable LeashData data) {
        leashData = data;
    }

    @Override
    public boolean canBeLeashed() {
        return !burning();
    }

    /** The body is lying down: the lead ties at the torso, not at standing eye height. */
    @Override
    public Vec3 getLeashOffset() {
        return new Vec3(0, 0.25, 0);
    }

    /** Past the slack the holder drags the body along the ground. */
    @Override
    public void elasticRangeLeashBehaviour(Entity holder, float distance) {
        Ragdolls.tow(this, holder.position(), 0.3, CorpseConfig.DRAG_SPEED.get());
    }

    /** A dragged corpse trails far behind a sprinter: pull harder instead of snapping at the vanilla 10 blocks. */
    @Override
    public void leashTooFarBehaviour() {
        Entity holder = getLeashHolder();
        if (holder != null && distanceTo(holder) < TOW_LIMIT) {
            Ragdolls.tow(this, holder.position(), 0.5, CorpseConfig.DRAG_SPEED.get() * 1.5);
        } else {
            Leashable.super.leashTooFarBehaviour();
        }
    }

    /** Blocks between holder and body at which a lead finally breaks. */
    private static final double TOW_LIMIT = 24;

    /** Burning is drawn even though the body is fire immune to the world. */
    @Override
    public boolean displayFireAnimation() {
        return burning();
    }

    @Override
    public void tick() {
        super.tick();
        if (level().isClientSide) {
            return;
        }
        if (!Ragdolls.active(this)) {
            if (savedBody != null) {
                Ragdolls.restore(this, savedBody);
                savedBody = null;
            } else {
                Ragdolls.handOff(this, this);
            }
        }
        if (burning()) {
            burnTick();
            if (isRemoved()) {
                return;
            }
        }
        age++;
        int force = CorpseConfig.FORCE_DESPAWN_TICKS.get();
        if (force >= 0 && age > force) {
            discard();
            return;
        }
        if (!entries.isEmpty()) {
            emptyAge = -1;
        } else if (emptyAge < 0) {
            emptyAge = age;
        } else if (age - emptyAge >= CorpseConfig.EMPTY_DESPAWN_TICKS.get()) {
            discard();
        }
    }

    private void burnTick() {
        burnAge++;
        if (isInWaterOrRain()) {
            entityData.set(BURNING, false);
            level().playSound(null, getX(), getY(), getZ(), SoundEvents.FIRE_EXTINGUISH, SoundSource.BLOCKS, 0.7F, 1.0F);
            return;
        }
        if (level() instanceof net.minecraft.server.level.ServerLevel server) {
            if (burnAge % 4 == 0) {
                server.sendParticles(ParticleTypes.FLAME, getX(), getY() + 0.3, getZ(), 3, 0.5, 0.15, 0.5, 0.01);
            }
            if (burnAge % 10 == 0) {
                server.sendParticles(ParticleTypes.LARGE_SMOKE, getX(), getY() + 0.5, getZ(), 2, 0.4, 0.2, 0.4, 0.02);
            }
        }
        if (burnAge % 40 == 1) {
            level().playSound(null, getX(), getY(), getZ(), SoundEvents.FIRE_AMBIENT, SoundSource.BLOCKS, 0.8F, 1.0F);
        }
        if (burnAge >= CorpseConfig.BURN_TICKS.get()) {
            level().playSound(null, getX(), getY(), getZ(), SoundEvents.GENERIC_BURN, SoundSource.BLOCKS, 1.0F, 1.0F);
            discard(); // what it held drops where it burned
        }
    }

    @Override
    public void remove(RemovalReason reason) {
        if (reason.shouldDestroy() && !level().isClientSide) {
            if (isLeashed()) {
                dropLeash(true, true);
            }
            for (Entry e : entries) {
                Containers.dropItemStack(level(), getX(), getY() + 0.3, getZ(), e.stack);
            }
            entries.clear();
        }
        super.remove(reason);
    }

    @Override
    public boolean hurt(DamageSource source, float amount) {
        if (!level().isClientSide) {
            Ragdolls.hit(this, source, amount);
        }
        return false;
    }

    @Override
    public void kill() {
        remove(RemovalReason.KILLED);
    }

    @Override
    public void travel(Vec3 input) {
    }

    @Override
    public boolean canChangeDimensions(Level from, Level to) {
        return false;
    }

    @Override
    protected void pushEntities() {
    }

    /** Carried: the crosshair passes through to what the carrier looks at. */
    @Override
    public boolean isPickable() {
        return super.isPickable() && !(WFSystems.enabled("carry") && Carry.carrierOf(this) != null);
    }

    @Override
    public boolean isPushable() {
        return false;
    }

    @Override
    public boolean isAttackable() {
        return false;
    }

    @Override
    public boolean isAffectedByPotions() {
        return false;
    }

    @Override
    protected boolean shouldDropLoot() {
        return false;
    }

    @Override
    public boolean shouldShowName() {
        return false;
    }

    @Override
    public Component getName() {
        return Component.translatable("wflib.corpse.of", ownerName());
    }

    @Override
    public Iterable<ItemStack> getArmorSlots() {
        return armor;
    }

    @Override
    public ItemStack getItemBySlot(EquipmentSlot slot) {
        return switch (slot.getType()) {
            case HAND -> hands.get(slot.getIndex());
            case HUMANOID_ARMOR -> armor.get(slot.getIndex());
            default -> ItemStack.EMPTY;
        };
    }

    @Override
    public void setItemSlot(EquipmentSlot slot, ItemStack stack) {
        switch (slot.getType()) {
            case HAND -> hands.set(slot.getIndex(), stack);
            case HUMANOID_ARMOR -> armor.set(slot.getIndex(), stack);
            default -> {
            }
        }
    }

    @Override
    public HumanoidArm getMainArm() {
        return HumanoidArm.RIGHT;
    }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        GameProfile profile = profile();
        tag.putUUID("Owner", profile.getId());
        tag.putString("OwnerName", profile.getName());
        tag.putString("Skin", entityData.get(SKIN));
        tag.putString("SkinSignature", entityData.get(SKIN_SIGNATURE));
        tag.putByte("Parts", entityData.get(PARTS));
        tag.putInt("Selected", selected);
        tag.putInt("Age", age);
        tag.putInt("EmptyAge", emptyAge);
        tag.putBoolean("Burning", burning());
        tag.putInt("BurnAge", burnAge);
        writeLeashData(tag, leashData);
        ListTag list = new ListTag();
        for (Entry e : entries) {
            CompoundTag t = new CompoundTag();
            if (e.kind != null) {
                t.putString("Kind", e.kind.toString());
            }
            t.putString("Key", e.key);
            t.put("Item", e.stack.saveOptional(registryAccess()));
            list.add(t);
        }
        tag.put("Entries", list);
        double[] body = level().isClientSide ? null : Ragdolls.state(this);
        if (body == null) {
            body = savedBody;
        }
        if (body != null) {
            ListTag b = new ListTag();
            for (double d : body) {
                b.add(DoubleTag.valueOf(d));
            }
            tag.put("Body", b);
        }
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        entityData.set(OWNER, Optional.of(tag.getUUID("Owner")));
        entityData.set(NAME, tag.getString("OwnerName"));
        entityData.set(SKIN, tag.getString("Skin"));
        entityData.set(SKIN_SIGNATURE, tag.getString("SkinSignature"));
        entityData.set(PARTS, tag.getByte("Parts"));
        selected = tag.getInt("Selected");
        age = tag.getInt("Age");
        emptyAge = tag.getInt("EmptyAge");
        entityData.set(BURNING, tag.getBoolean("Burning"));
        burnAge = tag.getInt("BurnAge");
        leashData = readLeashData(tag);
        entries.clear();
        ListTag list = tag.getList("Entries", Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            CompoundTag t = list.getCompound(i);
            ItemStack stack = ItemStack.parseOptional(registryAccess(), t.getCompound("Item"));
            entries.add(new Entry(t.contains("Kind") ? ResourceLocation.parse(t.getString("Kind")) : null,
                    t.getString("Key"), stack));
        }
        ListTag b = tag.getList("Body", Tag.TAG_DOUBLE);
        savedBody = b.size() == Ragdolls.STATE ? new double[Ragdolls.STATE] : null;
        if (savedBody != null) {
            for (int i = 0; i < savedBody.length; i++) {
                savedBody[i] = b.getDouble(i);
            }
        }
        changed();
    }
}
