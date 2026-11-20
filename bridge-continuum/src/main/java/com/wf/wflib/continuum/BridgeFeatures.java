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
            new Box("wiki", "Continuum manual in the PDA wiki", "Continuum's manual pages are WFLib wiki articles, opened for the items they describe."),
            new Box("soldiers", "Soldiers with Continuum guns", "WFLib soldiers can be issued and fire Continuum's magazine-fed guns."),
            new Box("warheads", "Continuum warheads on WFLib missiles", "Continuum's high-explosive and nuclear blasts are WFLib warheads."),
            new Box("penetration", "Penetration values", "Continuum's bullets carry a bore and a penetration in mm of steel to armour, plates and hulls."),
            new Box("bullets", "Bullets as WFLib rounds", "Plain Continuum bullets are flown as WFLib rounds: lag compensation, terrain, tracers, ricochet."),
            new Box("recon", "Continuum on the radar", "Continuum's missiles and rockets are contacts on WFLib's radar and scopes; its explosions are heard by seismic sensors."),
            new Box("orbit", "Continuum satellites in orbit", "Continuum's satellites appear in WFLib's orbital catalogue and the PDA satellites app."),
            new Box("explosionfx", "WFLib explosion effects (experimental)", "Continuum's small, standard and large explosions are drawn with GemRender's shockwave, cloud and thrown ground."),
            new Box("pda", "Continuum alerts on the PDA", "Missile launches and impacts, large blasts, satellites and radiation doses show as PDA alerts."));
    private final Map<String, Boolean> started;
    private final boolean startedMaster;

    BridgeFeatures() {
        startedMaster = BridgeConfig.master().get();
        started = new java.util.HashMap<>();
        for (Box box : BOXES) started.put(box.id(), BridgeConfig.value(box.id()).get());
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
