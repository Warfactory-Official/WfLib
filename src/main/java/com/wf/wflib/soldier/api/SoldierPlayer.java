package com.wf.wflib.soldier.api;

import com.mojang.authlib.GameProfile;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ClientInformation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.portal.DimensionTransition;
import net.minecraft.core.BlockPos;

/**
 * A server player with no client: it is in the player list, so every mod that handles players (guns, medical, stance,
 * factions) treats it as one, and {@link SoldierAi} supplies the input a keyboard would. Its connection discards
 * packets ({@code mixin.soldier.ServerCommonPacketListenerImplMixin}).
 */
public final class SoldierPlayer extends ServerPlayer {
    /** Chat lines about soldiers (joining, dying) are dropped while this is set. */
    private static boolean quiet;

    private float forward, strafe;
    private boolean sprint, sneak;

    static boolean quiet() {
        return quiet;
    }

    static void quiet(boolean on) {
        quiet = on;
    }

    SoldierPlayer(MinecraftServer server, ServerLevel level, GameProfile profile) {
        super(server, level, profile, ClientInformation.createDefault());
    }

    /** The movement keys held this tick (forward/strafe in -1..1). */
    public void drive(float forward, float strafe, boolean sprint, boolean sneak) {
        this.forward = forward;
        this.strafe = strafe;
        this.sprint = sprint;
        this.sneak = sneak;
    }

    /** A menu nobody can open: a soldier has no client, so syncing its inventory to one is pure waste each tick. */
    private static final class InertMenu extends AbstractContainerMenu {
        InertMenu() {
            super(null, 0);
        }

        @Override
        public ItemStack quickMoveStack(Player player, int index) {
            return ItemStack.EMPTY;
        }

        @Override
        public boolean stillValid(Player player) {
            return true;
        }

        @Override
        public void broadcastChanges() {
        }
    }

    /** Joined and positioned: clear the removed flag a dimension change can leave behind, and stop container syncing. */
    void markPlaced() {
        unsetRemoved();
        containerMenu = new InertMenu();
    }

    /** Show every skin layer, as a client that never changed its skin options would. */
    void showSkinLayers() {
        getEntityData().set(Player.DATA_PLAYER_MODE_CUSTOMISATION, (byte) 0x7f);
    }

    @Override
    public void tick() {
        if (getServer().getTickCount() % 10 == 0) {      // a client's movement packets would do this
            connection.resetPosition();
            serverLevel().getChunkSource().move(this);
        }
        if (tickCount % 20 == 0) getFoodData().setFoodLevel(20);
        zza = forward * (sneak ? 0.3f : 1);
        xxa = strafe * (sneak ? 0.3f : 1);
        setSprinting(sprint);
        setShiftKeyDown(sneak);
        long start = System.nanoTime();
        super.tick();
        doTick();                                          // the connection that would tick it does not exist
        com.wf.wflib.soldier.SoldierProfiler.record("body_player_tick", System.nanoTime() - start);
    }

    @Override
    public void die(DamageSource source) {
        quiet = true;
        try {
            super.die(source);
        } finally {
            quiet = false;
        }
        getServer().tell(new net.minecraft.server.TickTask(getServer().getTickCount(), () -> Soldiers.remove(this)));
    }

    @Override
    protected void checkFallDamage(double y, boolean onGround, BlockState state, BlockPos pos) {
        doCheckFallDamage(getX(), y, getZ(), onGround);    // server players normally take this from a movement packet
    }

    @Override
    public Entity changeDimension(DimensionTransition transition) {
        Entity moved = super.changeDimension(transition);
        if (moved instanceof ServerPlayer p && p.isChangingDimension()) p.hasChangedDimension(); // no client to confirm
        return moved;
    }

    @Override
    public String getIpAddress() {
        return "127.0.0.1";
    }
}
