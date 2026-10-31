package com.wf.wflib.mixin.physics;

import com.wf.wflib.physics.PhysicsSystem;
import com.wf.wflib.physics.api.DeckAttachment;
import com.wf.wflib.physics.api.DeckBody;
import com.wf.wflib.physics.api.DeckRejoin;
import com.wf.wflib.physics.api.DeckRider;
import com.wf.wflib.physics.api.PhysicsBody;
import com.wf.wflib.physics.api.VehicleParenting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Entity.class)
public abstract class MixinEntityDeck implements DeckRider {

    @Shadow
    public abstract boolean isPassengerOfSameVehicle(Entity entity);

    @Unique
    @Nullable
    private DeckAttachment wflib$deckAttachment;

    @Unique
    @Nullable
    private DeckRejoin wflib$deckRejoin;

    @Override
    @Nullable
    public DeckRejoin wflib$deckRejoin() {
        return this.wflib$deckRejoin;
    }

    @Override
    public void wflib$setDeckRejoin(@Nullable DeckRejoin rejoin) {
        this.wflib$deckRejoin = rejoin;
    }

    @Override
    @Nullable
    public DeckAttachment wflib$deckAttachment() {
        return this.wflib$deckAttachment;
    }

    @Override
    public void wflib$setDeckAttachment(@Nullable DeckAttachment attachment) {
        this.wflib$deckAttachment = attachment;
    }

    @Inject(method = "saveWithoutId", at = @At("RETURN"))
    private void wflib$saveDeck(CompoundTag compound, CallbackInfoReturnable<CompoundTag> cir) {
        if (PhysicsSystem.active()) {
            DeckRejoin.save((Entity) (Object) this, cir.getReturnValue());
        }
    }

    @Inject(method = "load", at = @At("TAIL"))
    private void wflib$loadDeck(CompoundTag compound, CallbackInfo ci) {
        if (PhysicsSystem.active()) {
            DeckRejoin.load((Entity) (Object) this, compound);
        }
    }

    @Inject(method = "push(Lnet/minecraft/world/entity/Entity;)V", at = @At("HEAD"), cancellable = true)
    private void wflib$pushedByDeck(Entity other, CallbackInfo ci) {
        // Mixin typing: keep the (Object) casts.
        if (PhysicsSystem.active() && !((Object) this instanceof PhysicsBody) && other instanceof DeckBody body) {
            if (!this.isPassengerOfSameVehicle(other)) {
                body.support((Entity) (Object) this);
            }
            ci.cancel();
        }
    }

    @Inject(method = "tick", at = @At("TAIL"))
    private void wflib$tickDeckCarry(CallbackInfo ci) {
        if (this.wflib$deckRejoin != null) {
            DeckRejoin.tick((Entity) (Object) this, this.wflib$deckRejoin);
        }
        if (this.wflib$deckAttachment != null) {
            VehicleParenting.onEntityTicked((Entity) (Object) this);
        }
    }

    /** Clips vanilla's move result against the deck, in the carrier's frame. */
    @Inject(method = "collide(Lnet/minecraft/world/phys/Vec3;)Lnet/minecraft/world/phys/Vec3;", at = @At("RETURN"),
            cancellable = true)
    private void wflib$collideWithDeck(Vec3 wanted, CallbackInfoReturnable<Vec3> cir) {
        if (this.wflib$deckAttachment == null) {
            return;
        }
        Vec3 allowed = cir.getReturnValue();
        Vec3 clipped = VehicleParenting.clipMovement((Entity) (Object) this, allowed);
        if (clipped != allowed) {
            cir.setReturnValue(clipped);
        }
    }
}
