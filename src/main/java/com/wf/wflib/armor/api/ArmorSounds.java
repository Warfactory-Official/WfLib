package com.wf.wflib.armor.api;

import com.wf.wflib.audio.api.AudioCategory;
import com.wf.wflib.audio.api.SoundSpec;
import com.wf.wflib.audio.api.WfAudio;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * Per-piece cues, played server-side at the wearer. Movement cues come from the outermost of
 * feet, legs, chest, head that defines them; they layer over the vanilla step/jump/fall sound.
 * Event ids need no registration: any id in a loaded {@code sounds.json}.
 */
public record ArmorSounds(Map<Cue, SoundSpec> cues) {

    public static final ArmorSounds NONE = new ArmorSounds(Map.of());

    /** Landings shorter than this stay silent: a jump lands from ~1.25, a stair does not. */
    public static final float MIN_LAND_FALL = 1.0F;

    private static final EquipmentSlot[] MOVEMENT_ORDER = {
            EquipmentSlot.FEET, EquipmentSlot.LEGS, EquipmentSlot.CHEST, EquipmentSlot.HEAD};

    public enum Cue {
        WALK("walk"), JUMP("jump"), LAND("land"),
        /** {@link ArmorOutcome#STOPPED}. */
        HIT_ABSORBED("hitAbsorbed"),
        /** {@link ArmorOutcome#REDUCED} or {@link ArmorOutcome#THROUGH} with armour absorbing part of the hit. */
        HIT_PENETRATED("hitPenetrated");

        public final String key;

        Cue(String key) {
            this.key = key;
        }
    }

    public ArmorSounds {
        cues = cues.isEmpty() ? Map.of() : Map.copyOf(new EnumMap<>(cues));
    }

    public boolean isEmpty() {
        return cues.isEmpty();
    }

    @Nullable
    public SoundSpec get(Cue cue) {
        return cues.get(cue);
    }

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {
        private final Map<Cue, SoundSpec> cues = new EnumMap<>(Cue.class);

        public Builder cue(Cue cue, SoundSpec spec) {
            cues.put(cue, spec);
            return this;
        }

        public ArmorSounds build() {
            return new ArmorSounds(cues);
        }
    }

    @Nullable
    private static ArmorSounds of(ItemStack stack) {
        return stack.getItem() instanceof ArmorPieceItem item && !item.preset().sounds().isEmpty()
                ? item.preset().sounds() : null;
    }

    public static void playMovement(LivingEntity wearer, Cue cue) {
        if (!(wearer.level() instanceof ServerLevel level) || wearer.isSilent()) {
            return;
        }
        for (EquipmentSlot slot : MOVEMENT_ORDER) {
            ArmorSounds sounds = of(wearer.getItemBySlot(slot));
            SoundSpec spec = sounds == null ? null : sounds.get(cue);
            if (spec != null) {
                WfAudio.at(level, wearer.position(), AudioCategory.ARMOR, List.of(spec));
                return;
            }
        }
    }

    /** First of {@code garments} defining the cue plays it at the body; no armour absorbing => no cue. */
    static void playHit(LivingEntity wearer, List<WornArmor> garments, ArmorResult result) {
        if (!(wearer.level() instanceof ServerLevel level) || result.absorbed() <= 0.0D || wearer.isSilent()) {
            return;
        }
        Cue cue = result.stopped() ? Cue.HIT_ABSORBED : Cue.HIT_PENETRATED;
        for (WornArmor piece : garments) {
            ArmorSounds sounds = of(piece.garment());
            SoundSpec spec = sounds == null ? null : sounds.get(cue);
            if (spec != null) {
                WfAudio.at(level, wearer.position().add(0, wearer.getBbHeight() * 0.6, 0), AudioCategory.ARMOR,
                        List.of(spec));
                return;
            }
        }
    }
}
