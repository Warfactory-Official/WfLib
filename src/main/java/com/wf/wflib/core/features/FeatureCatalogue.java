package com.wf.wflib.core.features;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Every switch a player can flip, in the order the screen and the config file show them: a few mainline {@link Group}s, each with
 * the {@link Feature}s you can keep or drop on their own. This list is the one place that says how wflib is split up and what each
 * piece is on by default; the config file, the screen, the PDA and the systems all read it.
 *
 * <p>A feature is one of:
 * <ul>
 * <li>a <b>system</b> ({@code system} set): the module's registries and content exist only when it is on, which needs a restart;</li>
 * <li>a <b>flag</b> ({@code needsSystem} maybe set): a part of a system that is gated where it runs, such as heat and hunter
 * teams inside contracts;</li>
 * <li>an <b>app</b> ({@code apps} set): hides PDA apps while off. Systems list the apps they own, so an off system never shows
 * them; an app-only feature hides the app of a system that stays on for other reasons (the map app, the radar).</li>
 * </ul>
 * Ids are the config keys: lower snake case, unique across the whole list. A system feature's id is its system id.
 */
public final class FeatureCatalogue {
    private FeatureCatalogue() {
    }

    /** A mainline feature: a master checkmark over its {@link #features}. Off means every feature in it is off. */
    public record Group(String id, String title, String blurb, List<Feature> features) {
        public Group {
            features = List.copyOf(features);
        }
    }

    /**
     * One smaller checkmark.
     *
     * @param system the wflib system this switches, or null
     * @param needsSystem a system a flag cannot work without (it is off whenever that system is), or null
     * @param apps PDA app ids ({@code wflib:radar}) that are hidden while this is off
     */
    public record Feature(String id, String title, String blurb, boolean defaultOn, String system, String needsSystem, List<String> apps) {
        public Feature {
            apps = List.copyOf(apps);
        }
    }

    private static Feature system(String id, String title, String blurb, String... apps) {
        return new Feature(id, title, blurb, true, id, null, List.of(apps));
    }

    private static Feature flag(String id, String title, String blurb, String needsSystem) {
        return new Feature(id, title, blurb, true, null, needsSystem, List.of());
    }

    private static Feature app(String id, String title, String blurb, String app) {
        return new Feature(id, title, blurb, true, null, null, List.of(app));
    }

    private static Feature off(Feature f) {
        return new Feature(f.id(), f.title(), f.blurb(), false, f.system(), f.needsSystem(), f.apps());
    }

    public static final List<Group> GROUPS = List.of(
            new Group("military", "Private Military", "AI soldiers you can hire and command, and the contracts that send you after them.", List.of(
                    system("soldier", "Soldiers", "Squads with orders, patrol posts, depots and fire teams; hiring and the squad wheel.", "wflib:reinforcements"),
                    system("contract", "Contracts", "The job board, reputation and ranks, built enemy bases, payouts.", "wflib:contracts"),
                    flag("contract_heat", "Heat and hunters", "Kills raise heat; enough of it sends hunter teams after you and closes factions to you.", "contract"),
                    flag("contract_blacksite", "Black Site", "The roaming site that can be seized and defended in waves.", "contract"),
                    flag("contract_engagements", "Engagements", "Friendly and enemy squads that clash in the open and move the war.", "contract"),
                    flag("contract_daily", "Daily board", "The generated daily contracts.", "contract"))),
            new Group("weapons", "Weapons and Optics", "What a gun mod hangs on a weapon, and what the shooter feels.", List.of(
                    system("magazine", "Magazines", "Magazine items and reload state."),
                    system("barrel", "Barrels", "Barrel and muzzle attachments."),
                    system("sight", "Optics", "Scopes, zoom and picture-in-picture lenses."),
                    system("laser", "Lasers", "Laser sights and designators."),
                    system("nvg", "Night vision", "Night-vision goggles."),
                    system("sway", "Sway", "Pain wobble and the shared camera sway."),
                    system("suppression", "Suppression", "Near misses blur and darken the screen."),
                    system("countermeasure", "Countermeasures", "Flares, chaff and smoke against missiles."))),
            new Group("explosives", "Demolition and Mines", "Charges, detonators and mines.", List.of(
                    system("demolition", "Charges and detonators", "Mining charges, breaching charges and the detonator."),
                    system("mine", "Mines", "Land and naval mines."))),
            new Group("gear", "Armor and Carry", "What you wear and what you carry.", List.of(
                    system("armor", "Armor", "Damage threshold and resistance per protection type, worn-piece wear, exosuit parts."),
                    system("backpack", "Backpacks", "A worn backpack with its own slots in the inventory."),
                    system("carry", "Open carry", "Carry a large item in both hands."),
                    system("skin", "Skins", "A choosable look for gear that offers more than one."),
                    system("smoking", "Smoking", "Cigarettes."))),
            new Group("bodies", "Bodies and Physics", "Things that fall over, and what they leave behind.", List.of(
                    system("physics", "Rigid-body physics", "The engine for bodies, decks and moving structures."),
                    system("ragdoll", "Ragdolls", "Players and soldiers go limp when they die."),
                    system("corpse", "Corpses", "A dead player leaves a body with their gear; leash it, drag it, burn it."))),
            new Group("recon", "Sensors and Surveillance", "Eyes in the sky, on the wall and in your pocket.", List.of(
                    system("drone", "Drones", "Piloted and autonomous drones and the Drones app.", "wflib:drones"),
                    system("photo", "Photography", "Camera, printed photos and the Photos app.", "wflib:photos"),
                    system("orbital", "Satellites", "Orbits, ground stations, launch pads and the Satellites app.", "wflib:satellites"),
                    app("cameras_app", "Cameras app", "Watch and steer security cameras from the PDA.", "wflib:cameras"),
                    app("radar_app", "Radar app", "The radar scope.", "wflib:radar"),
                    app("telemetry_app", "Telemetry app", "Readings from sensor blocks.", "wflib:telemetry"),
                    app("radio_app", "Radio app", "Tune and talk on the PDA.", "wflib:radio"))),
            new Group("hazards", "Hazards and Anomalies", "The Zone.", List.of(
                    system("hazard", "Hazards", "Radioactive and toxic items, the Geiger counter, protection checks."),
                    system("radiation", "Radiation", "The chunk radiation field: sources, fallout, decay."),
                    system("xray", "Screen effects", "Hazard and anomaly screen and camera effects."),
                    system("artifact", "Artefacts", "Artefacts, the detectors that find them, thrown bolts."),
                    off(system("anomaly", "Anomalies", "Anomalies, their sites and cave fields. Placed anomalies are lost while this is off.")),
                    system("colony", "Glyphid colonies", "A simulated colony, the castes that walk out of it, and its nests."))),
            new Group("construction", "Industry and Construction", "Building, hauling and the work that keeps a base going.", List.of(
                    system("work", "Work orders", "The job system drones and workers share."),
                    system("industry", "Industry", "What a block is worth as a target, and where the bases are."),
                    system("build", "Blueprints", "Read blueprints, construct, salvage, the drone builder."),
                    system("door", "Doors", "The reinforced door roster with locks and keys."),
                    system("probe", "Look-at probe", "The panel that tells you what you are looking at."),
                    system("rail", "Rail surveying", "Track alignment, tunnelling and laying."))),
            new Group("info", "PDA and Information", "The PDA itself, and what it can show.", List.of(
                    system("wiki", "Wiki", "Item articles in the PDA.", "wflib:wiki"),
                    system("worldstats", "World readout", "The overlay with time, biome, chunk, light and weather; mission lines."),
                    app("map_app", "Map app", "The map in the PDA.", "wflib:map"),
                    app("terminal_app", "Terminal app", "The command terminal.", "wflib:terminal"),
                    app("alerts_app", "Alerts app", "The alert feed.", "wflib:alerts"),
                    app("music_app", "Music app", "The music player.", "wflib:music"))),
            new Group("tooling", "Tooling", "For pack makers and developers.", List.of(
                    system("packs", "Pack hosts", "Content-pack discovery and the client/server handshake."),
                    system("labs", "Labs", "Headless data-mapping runs of the damage stack."))));

    /** Every feature, in order. */
    public static List<Feature> all() {
        List<Feature> out = new ArrayList<>();
        for (Group g : GROUPS) out.addAll(g.features());
        return out;
    }

    public static Feature byId(String id) {
        for (Group g : GROUPS) for (Feature f : g.features()) if (f.id().equals(id)) return f;
        throw new IllegalArgumentException("no feature '" + id + "'");
    }

    public static Group groupOf(String featureId) {
        for (Group g : GROUPS) for (Feature f : g.features()) if (f.id().equals(featureId)) return g;
        throw new IllegalArgumentException("no feature '" + featureId + "'");
    }

    /** The feature that switches {@code system}, or null when none does (a mandatory system). */
    public static Feature ofSystem(String system) {
        for (Group g : GROUPS) for (Feature f : g.features()) if (system.equals(f.system())) return f;
        return null;
    }

    /** Feature id to PDA app ids. */
    public static Map<String, List<String>> appsByFeature() {
        return all().stream().filter(f -> !f.apps().isEmpty()).collect(Collectors.toMap(Feature::id, Feature::apps));
    }
}
