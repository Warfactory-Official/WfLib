package com.wf.wflib.mine;

import com.wf.wflib.mine.api.MineEntity;
import com.wf.wflib.mine.api.MineModels;
import com.wf.wflib.mine.api.MinePreset;
import com.wf.wflib.mine.api.MinePresets;
import com.wf.wflib.mine.api.MineTriggers;
import com.wf.wflib.missile.api.DefuseMethod;
import com.wf.wflib.missile.api.MineTags;

import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/** The shipped mines, registered with the system; items iff requested ({@code WFLibContent.mines}). */
public final class MineCatalogue {
    /** Ticks in a minute, so the self-destruct lives below read as the times they are. */
    private static final int MINUTES = 20 * 60;
    /** Fragments in a bounding mine's sleeve, and balls in a claymore. */
    private static final int SLEEVE_FRAGMENTS = 600;
    private static final int CLAYMORE_BALLS = 700;

    private static final List<MinePreset> DEFINITIONS = build();
    private static final Set<ResourceLocation> IDS = DEFINITIONS.stream().map(MinePreset::id)
            .collect(Collectors.toCollection(LinkedHashSet::new));

    private MineCatalogue() {
    }

    public static List<MinePreset> definitions() {
        return DEFINITIONS;
    }

    public static boolean defined(ResourceLocation id) {
        return IDS.contains(id);
    }

    public static Collection<ResourceLocation> ids() {
        return IDS;
    }

    /** One per fuse so each of the three shipped detection types has something that uses it. */
    private static List<MinePreset> build() {
        List<MinePreset> out = new ArrayList<>();
        out.add(MinePreset.builder(MinePresets.rl("ap"), MineModels.rl("ap"), MineWarheads.BLAST)
                .trigger(MineTriggers.rl("living"))
                .triggerRange(2.5)
                .sneakTriggerRange(0.9)
                .buriable()
                .tumbles()
                .armDelay(40)
                .defusable(DefuseMethod.HAND)
                .defuseTicks(40)
                .mass(0.4)
                .build());

        out.add(MinePreset.builder(MinePresets.rl("bounding"), MineModels.rl("bounding"), MineWarheads.FRAG)
                .trigger(MineTriggers.rl("living"))
                .triggerRange(3.5)
                .sneakTriggerRange(1.2)
                .buriable()
                .bounceHeight(MineEntity.BOUNDING_HEIGHT)
                .fragmentCount(SLEEVE_FRAGMENTS)
                .armDelay(60)
                .defusable(DefuseMethod.TOOL, MineTags.DEFUSER)
                .defuseTicks(80)
                .defuseFailChance(0.15f)
                .mass(3.5)
                .build());

        out.add(MinePreset.builder(MinePresets.rl("claymore"), MineModels.rl("claymore"), MineWarheads.DIRECTIONAL)
                .trigger(MineTriggers.rl("living"))
                .triggerRange(4.0)
                .sneakTriggerRange(1.5)
                .arc(60.0)
                .blastAlongFacing()
                .blastHalfAngle(35.0f)
                .fragmentCount(CLAYMORE_BALLS)
                .armDelay(40)
                .defusable(DefuseMethod.HAND)
                .defuseTicks(60)
                .mass(1.6)
                .build());

        out.add(MinePreset.builder(MinePresets.rl("heat"), MineModels.rl("heat"), MineWarheads.HEAT)
                .trigger(MineTriggers.rl("heavy"))
                .buriable()
                .triggerRange(1.5)
                .sneakTriggerRange(1.5)
                .armDelay(60)
                .defusable(DefuseMethod.TOOL, MineTags.DEFUSER)
                .defuseTicks(100)
                .defuseFailChance(0.1f)
                .mass(10.2)
                .build());

        out.add(MinePreset.builder(MinePresets.rl("naval"), MineModels.rl("naval"), MineWarheads.NAVAL)
                .trigger(MineTriggers.rl("living"))
                .floats(1.5)
                .armsOnlyInWater()
                .triggerRange(6.0)
                .sneakTriggerRange(6.0)
                .scanInterval(6)
                .armDelay(100)
                .defusable(DefuseMethod.TOOL, MineTags.DEFUSER)
                .defuseTicks(120)
                .defuseFailChance(0.25f)
                .mass(300)
                .build());

        sownMines(out);
        return out;
    }

    /** The mines that can only be sown: dispensed from a rack or a drone, never laid by hand. */
    private static void sownMines(List<MinePreset> out) {
        out.add(MinePreset.builder(MinePresets.rl("scatter_ap"), MineModels.rl("scatter_ap"), MineWarheads.BLAST)
                .sownOnly()
                .trigger(MineTriggers.rl("living"))
                .triggerRange(2.0)
                .sneakTriggerRange(0.8)
                .tumbles()
                .armDelay(80)
                .selfDestructs(10 * MINUTES)
                .undefusable()
                .mass(0.08)
                .build());

        out.add(MinePreset.builder(MinePresets.rl("scatter_at"), MineModels.rl("scatter_at"), MineWarheads.HEAT)
                .sownOnly()
                .trigger(MineTriggers.rl("heavy"))
                .triggerRange(1.8)
                .sneakTriggerRange(1.8)
                .tumbles()
                .armDelay(100)
                .selfDestructs(20 * MINUTES)
                .undefusable()
                .mass(1.6)
                .build());

        dispensers(out);
    }

    /** The racks that sow the scatter mines. */
    private static void dispensers(List<MinePreset> out) {
        out.add(MinePreset.builder(MinePresets.rl("dispenser_ap"), MineModels.rl("dispenser_ap"),
                        MineWarheads.DISPENSE_AP)
                .trigger(MineTriggers.rl("living"))
                .triggerRange(10.0)
                .sneakTriggerRange(5.0)
                .arc(MineWarheads.DISPENSER_ARC)
                .canisters(MineWarheads.DISPENSER_CANISTERS, MinePresets.rl("scatter_ap"))
                .requiresActivation()
                .armDelay(60)
                .defusable(DefuseMethod.HAND)
                .defuseTicks(60)
                .mass(35)
                .build());

        out.add(MinePreset.builder(MinePresets.rl("dispenser_at"), MineModels.rl("dispenser_at"),
                        MineWarheads.DISPENSE_AT)
                .trigger(MineTriggers.rl("heavy"))
                .triggerRange(14.0)
                .sneakTriggerRange(14.0)
                .arc(MineWarheads.DISPENSER_ARC)
                .canisters(MineWarheads.DISPENSER_CANISTERS, MinePresets.rl("scatter_at"))
                .requiresActivation()
                .armDelay(60)
                .defusable(DefuseMethod.HAND)
                .defuseTicks(60)
                .mass(60)
                .build());
    }
}
