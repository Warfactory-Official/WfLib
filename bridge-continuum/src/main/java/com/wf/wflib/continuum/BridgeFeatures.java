package com.wf.wflib.continuum;

import com.wf.wflib.core.api.FeatureSource;

import java.util.List;
import java.util.Map;

/** The bridge's switches on WFLib's switchboard screen (group "NTM: Continuum"), kept in {@code wflib_continuum.toml}. All read at launch. */
final class BridgeFeatures implements FeatureSource {
    private static final List<Box> BOXES = List.of(
            new Box("armour", "WFLib armour on Continuum gear", "WFLib's armour system reduces damage for Continuum's armour, with values seeded from Continuum's own; Continuum's own reduction stands down."),
            new Box("doors", "WFLib doors", "Continuum's door recipes make the WFLib doors, and Continuum's door items leave the creative tabs."),
            new Box("glyphids", "WFLib glyphids", "Continuum's natural glyphid spawns and hives are off; WFLib's colonies replace them and drop Continuum's meat."),
            new Box("radiation", "Radiation through WFLib", "Continuum's radiation field is the one WFLib reads and feeds; players take WFLib's dose; Continuum's hazard rows become WFLib's."),
            new Box("nukes", "WFLib nuclear ray", "Continuum's nuclear blasts run WFLib's parallel crater ray."),
            new Box("masses", "Masses for Continuum items", "Ultimate Weight gets a mass for every Continuum material item, from its material's density."),
            new Box("probe", "Continuum readouts in the probe", "Continuum's look-at readouts show in WFLib's probe panel instead of beside the crosshair."),
            new Box("wiki", "Continuum manual in the PDA wiki", "Continuum's manual pages are WFLib wiki articles, opened for the items they describe."));
    private final Map<String, Boolean> started;
    private final boolean startedMaster;

    BridgeFeatures() {
        startedMaster = BridgeConfig.master().get();
        started = Map.of("armour", BridgeConfig.value("armour").get(), "doors", BridgeConfig.value("doors").get(), "glyphids", BridgeConfig.value("glyphids").get(),
                "radiation", BridgeConfig.value("radiation").get(), "nukes", BridgeConfig.value("nukes").get(), "masses", BridgeConfig.value("masses").get(), "probe", BridgeConfig.value("probe").get(), "wiki", BridgeConfig.value("wiki").get());
    }

    @Override
    public String id() {
        return "ntm_continuum";
    }

    @Override
    public String title() {
        return "NTM: Continuum";
    }

    @Override
    public String blurb() {
        return "Runs NTM: Continuum's gear and world on WFLib's systems. Needs the bridge and Continuum.";
    }

    @Override
    public List<Box> boxes() {
        return BOXES;
    }

    @Override
    public boolean master() {
        return BridgeConfig.master().get();
    }

    @Override
    public boolean get(String box) {
        return BridgeConfig.value(box).get();
    }

    @Override
    public boolean startup(String box) {
        return started.get(box);
    }

    @Override
    public boolean startupMaster() {
        return startedMaster;
    }

    @Override
    public void set(String box, boolean on) {
        BridgeConfig.value(box).set(on);
    }

    @Override
    public void master(boolean on) {
        BridgeConfig.master().set(on);
    }

    @Override
    public void save() {
        BridgeConfig.SPEC.save();
    }
}
