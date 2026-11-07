package com.wf.wflib.soldier.api;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import org.jetbrains.annotations.Nullable;

/** First aid for the soldier AI, over whatever medical mod is installed. The default does nothing. */
public interface SoldierMedical {

    record Condition(boolean downed, boolean hands, boolean legs, boolean sprint, double blood, double bleeding, float pain) {
        public static final Condition HEALTHY = new Condition(false, false, false, false, 1, 0, 0);

        public boolean needsHelp() {
            return downed || hands || legs || bleeding > 0.01 || blood < 0.85;
        }

        public boolean critical() {
            return downed || blood < 0.75 || bleeding > 0.5;
        }

        public double urgency() {
            return (downed ? 100 : 0) + bleeding * 10 + (1 - blood) * 20 + (hands || legs ? 10 : 0);
        }
    }

    /** An item to use, on a limb (null = whole body), carried in inventory {@code slot}, worth {@code priority}. */
    record Plan(ResourceLocation item, @Nullable String limb, int slot, double priority) {
    }

    boolean present();

    Condition condition(ServerPlayer p);

    /** A treatment is being carried out by this player. */
    boolean active(ServerPlayer p);

    void cancel(ServerPlayer p);

    boolean begin(ServerPlayer actor, ServerPlayer patient, Plan plan);

    boolean revive(ServerPlayer actor, ServerPlayer patient);

    /** The most useful treatment the actor carries for the patient, or null. */
    @Nullable Plan plan(ServerPlayer actor, ServerPlayer patient);

    /** The medical items carried, by item id, for telemetry. */
    default java.util.Map<String, Integer> supplies(ServerPlayer p) {
        return java.util.Map.of();
    }

    /** Make a downed soldier give up (what a downed player's held key sends); nothing when not downed. */
    default void giveUp(ServerPlayer p) {
    }

    /** Put a medical kit in the inventory (more for a medic). */
    void kit(ServerPlayer p, boolean medic);

    /** Does nothing: no medical mod. */
    SoldierMedical NONE = new SoldierMedical() {
        @Override
        public boolean present() {
            return false;
        }

        @Override
        public Condition condition(ServerPlayer p) {
            return Condition.HEALTHY;
        }

        @Override
        public boolean active(ServerPlayer p) {
            return false;
        }

        @Override
        public void cancel(ServerPlayer p) {
        }

        @Override
        public boolean begin(ServerPlayer actor, ServerPlayer patient, Plan plan) {
            return false;
        }

        @Override
        public boolean revive(ServerPlayer actor, ServerPlayer patient) {
            return false;
        }

        @Override
        public Plan plan(ServerPlayer actor, ServerPlayer patient) {
            return null;
        }

        @Override
        public void kit(ServerPlayer p, boolean medic) {
        }
    };
}
