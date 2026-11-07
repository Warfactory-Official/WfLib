package com.wf.wflib.soldier;

import com.wf.wflib.soldier.api.SoldierMedical;
import com.wf.wflib.soldier.api.SoldierSpec;
import com.wf.wflib.soldier.api.SoldierServices;
import com.wf.wflib.soldier.api.SoldierWeapon;
import com.wf.wflib.soldier.api.SoldierPlayer;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.norwood.ahf.stance.AhfStance;
import com.norwood.ahf.stance.Posture;
import com.norwood.ahf.stance.StanceRequest;
import com.norwood.ahf.stance.StanceState;
import com.wf.wflib.grenade.api.Grenades;
import com.wf.wflib.grenade.api.SmokeScreens;
import com.wf.wflib.grenade.api.ThrownCharge;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.event.PlayLevelSoundEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

import java.lang.reflect.Method;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Deque;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Predicate;

/**
 * Squad AI for scripted players (server thread): Carpet fakes in a filming world, and {@link SoldierPlayer}s from the
 * spawn eggs anywhere. Each soldier sees enemies by line of sight
 * only (with a reaction delay), remembers where it last saw them, takes cover against those threats (kneeling hidden,
 * standing or side-stepping to shoot), takes danger-weighted routes, bounds forward while teammates cover, gets
 * suppressed by near misses and hits (stays down, aims worse), suppresses last-seen positions, reloads behind cover,
 * fires real shots through {@link ServerFire#scriptedFire}, and respawns (Carpet disconnects dead fakes) re-armed.
 * Orders set a mode and an anchor: POST (fixed firing post), HOLD (cover near the anchor) or ADVANCE (cover to cover
 * toward the anchor, then hold). An explicit {@code path} is walked first, as scripted movement. Carpet fakes move through
 * Carpet's action pack because Carpet rewrites a fake's movement input every tick; soldiers set their own input.
 */
public final class SoldierAi {
    enum Fire {OFF, BURST, AUTO}

    enum Mode {POST, HOLD, ADVANCE}

    static final class Threat {
        Vec3 eye;
        long seen;
        int visible;
        int reaction;
        /** Awareness of this person (1 = noticed): builds while they are in view, fades while they are not. */
        float value = 1f;
        boolean downReported;

        /** A contact known only by report or sound: half-aware, so seeing the person confirms it fast. */
        static Threat told() {
            Threat t = new Threat();
            t.value = 0.8f;
            return t;
        }
    }

    static final class Bot {
        final String name;
        String team = "";
        String role = "rifle";
        String voice = "", lastCallout = "";
        int callouts;
        final VoiceCadence voiceCadence = new VoiceCadence();
        /** Spawn-egg soldier: ticks anywhere, is never respawned, and (if {@link #huntsPlayers}) shoots real players. */
        boolean soldier, huntsPlayers, hunting;
        /** Wants a reload that is worth the time; out of ammunition for good; was falling back to reload or heal. */
        boolean needReload, dry, retreating, engaged;
        Vec3 targetAnchor;
        int targetStill, nudgeTicks;
        Vec3 nudge = Vec3.ZERO;
        String stallInfo = "";
        int smokes, pushes;
        ResourceLocation fragItem, smokeItem;
        /** A route search running on a worker, and what it is for. */
        java.util.concurrent.Future<List<Vec3>> pendingRoute;
        BlockPos pendingGoal;
        String planFor = "";
        long planAt = -1000;
        SoldierMedical.Plan planCached;
        long lastSmoke = -1000, pushSearched = -1000;
        Vec3 rest, restFor;
        float restYaw;
        MinecraftServer server;
        UUID ownerId;
        String squad = "", squadTask = "travel", patient = "", rescuer = "";
        boolean mayAdvance = true, yielding;
        boolean oriented;
        boolean mayTreat;
        boolean medicalRetreat;
        Vec3 careShelter;
        float watchYaw;
        long lastBound = -1000, progressAt, lastShot = -1000, careRetry;
        int reports, heardShots, stalls, yieldTicks, treatments, revives, reviveAttempts, careCount;
        String carePatient = "", careAction = "";
        SoldierMedical.Plan carePlan;
        SoldierMedical.Condition health = SoldierMedical.Condition.HEALTHY;
        BlockPos blockedCell;
        long blockedUntil;
        Mode mode = Mode.HOLD;
        Vec3 anchor;
        int holdRadius = 6;
        final Deque<Vec3> path = new ArrayDeque<>();
        boolean sprint = true, crouch = true;
        float lean;
        Fire fire = Fire.BURST;
        int burst = 5, pause = 16;
        float spreadDeg = 2f, range = 80f;
        Vec3 spawn;
        float spawnYaw;
        int respawnTicks = 100;
        ResourceLocation gun;
        int mags = 4;
        // perception and state
        final Map<String, Threat> threats = new HashMap<>();
        /** People glimpsed but not yet noticed. */
        final Map<String, Threat> glimpses = new HashMap<>();
        final Deque<Vec3> route = new ArrayDeque<>();
        BlockPos routeGoal;
        BotTactics.Cover cover;
        BotTactics.Cover peekingCover;
        final BotPeek peek;
        final BotGrenades danger = new BotGrenades();
        boolean evading;
        long escapePlanned = -1000;
        int escapes;
        long coverChecked = -1000, arrived = -1000, searched = -1000, routeChecked = -1000;
        float suppression;
        int firing, resting, dead, down, giveUpAt, still, jitterAge, onTarget, shots, reloads, deaths, thrown;
        List<Vec3> posts = List.of();
        List<ResourceLocation> grenades = List.of();
        Vec3 lastSeenAt;
        // grenade: the item to throw, where, and the throw's progress (ticks); frags left for autonomous use
        ResourceLocation throwItem;
        Vec3 throwAt;
        int throwPhase = -1, frags;
        long lastFrag = -1000;
        boolean arm, stanceDirty = true, lastAds, moving, stepUp;
        Posture lastPosture = Posture.STAND;
        float lastLean;
        float jitterYaw, jitterPitch, climb;
        Vec3 lastPos = Vec3.ZERO;
        Vec3 walkDirection = Vec3.ZERO;
        String target = "", verdict = "", state = "";

        Bot(String name) {
            this.name = name;
            peek = new BotPeek(name.hashCode());
        }
    }

    private static final Map<String, Bot> BOTS = new LinkedHashMap<>();
    /** Last frag / smoke thrown by each team (game tick), so one team never showers the street. */
    private static final Map<String, Long> TEAM_FRAG = new HashMap<>(), TEAM_SMOKE = new HashMap<>();
    private static final RandomSource RNG = RandomSource.create(7L);
    private static final float MAX_TURN = 10f, FIRE_CONE = 6f;
    private static final int GIVE_UP_TICKS = 90, FORGET_TICKS = 300, SUPPRESS_TICKS = 160;

    private SoldierAi() {}

    /** Where the Carpet-scripted filming bots may run; the harness supplies it (default: nowhere). */
    private static volatile Predicate<MinecraftServer> filming = server -> false;

    public static void filmingGate(Predicate<MinecraftServer> gate) {
        filming = gate;
    }

    /**
     * Registers a freshly spawned soldier's orders: armed on its first tick, standing guard at {@code at} (an enemy
     * hunts from its post, a friendly stays by its owner).
     */
    public static void spawned(MinecraftServer server, String name, SoldierSpec spec, UUID owner, Vec3 at, float yaw) {
        Bot b = new Bot(name);
        b.soldier = true;
        b.server = server;
        b.team = spec.team();
        b.huntsPlayers = spec.huntsPlayers();
        b.ownerId = owner;
        b.gun = spec.gun();
        b.mags = spec.spares();
        b.arm = true;
        b.grenades = spec.grenades();
        b.spawn = at;
        b.spawnYaw = yaw;
        b.anchor = at;
        b.mode = Mode.HOLD;
        b.holdRadius = spec.huntsPlayers() ? 8 : (int) SoldierBrain.ESCORT_RADIUS;
        b.watchYaw = yaw;
        BOTS.put(name.toLowerCase(Locale.ROOT), b);
    }

    /** The server is going away: its soldiers' orders go with it. */
    public static void forget(MinecraftServer server) {
        BOTS.values().removeIf(b -> b.soldier && b.server == server);
    }

    static Bot botOf(String name) {
        return BOTS.get(name.toLowerCase(Locale.ROOT));
    }

    public static int soldiers() {
        return (int) BOTS.values().stream().filter(b -> b.soldier).count();
    }

    /** A player this AI drives: a spawn-egg soldier or a Carpet fake (never a real player). */
    static boolean scripted(ServerPlayer p) {
        return p instanceof SoldierPlayer || p.getClass().getName().equals("carpet.patches.EntityPlayerMPFake");
    }

    /** Soldiers run on the server that spawned them; filming bots only where the harness allows. */
    private static boolean runsOn(Bot b, MinecraftServer server, boolean filmingServer) {
        return b.soldier ? b.server == server : filmingServer;
    }

    private static Bot bot(String name) {
        return BOTS.computeIfAbsent(name.toLowerCase(Locale.ROOT), k -> new Bot(name));
    }

    /** Merge {@code a} into the named bot's order; only the given keys change. */
    public static JsonObject order(JsonObject a) {
        Bot b = bot(a.get("name").getAsString());
        if (a.has("team")) b.team = a.get("team").getAsString();
        if (a.has("role")) b.role = a.get("role").getAsString();
        if (a.has("voice")) {
            String voice = a.get("voice").getAsString();
            if (!SoldierVoices.valid(voice)) throw new IllegalArgumentException("Unknown voice pack or speaker: " + voice);
            b.voice = voice;
        }
        if (a.has("squad")) b.squad = a.get("squad").getAsString();
        if (a.has("mode")) {
            b.mode = Mode.valueOf(a.get("mode").getAsString().toUpperCase(Locale.ROOT));
            b.cover = null;
            b.route.clear();
        }
        if (a.has("anchor")) {
            b.anchor = point(a.getAsJsonArray("anchor"));
            b.cover = null;
            b.route.clear();
        }
        if (a.has("radius")) b.holdRadius = a.get("radius").getAsInt();
        if (a.has("path")) {
            b.path.clear();
            b.path.addAll(points(a.get("path")));
        }
        if (a.has("sprint")) b.sprint = a.get("sprint").getAsBoolean();
        if (a.has("crouch")) {
            b.crouch = a.get("crouch").getAsBoolean();
            b.stanceDirty = true;
        }
        if (a.has("lean")) {
            b.lean = Mth.clamp(a.get("lean").getAsFloat(), -1, 1);
            b.stanceDirty = true;
        }
        if (a.has("fire")) b.fire = Fire.valueOf(a.get("fire").getAsString().toUpperCase(Locale.ROOT));
        if (a.has("burst")) b.burst = Math.max(1, a.get("burst").getAsInt());
        if (a.has("pause")) b.pause = Math.max(0, a.get("pause").getAsInt());
        if (a.has("spread")) b.spreadDeg = Math.max(0, a.get("spread").getAsFloat());
        if (a.has("range")) b.range = a.get("range").getAsFloat();
        if (a.has("spawn")) {
            JsonArray s = a.getAsJsonArray("spawn");
            b.spawn = point(s);
            b.spawnYaw = s.size() > 3 ? s.get(3).getAsFloat() : 0;
        }
        if (a.has("respawn_ticks")) b.respawnTicks = a.get("respawn_ticks").getAsInt();
        if (a.has("gun")) b.gun = ResourceLocation.parse(a.get("gun").getAsString());
        if (a.has("mags")) b.mags = a.get("mags").getAsInt();
        if (a.has("arm") && a.get("arm").getAsBoolean()) b.arm = true;
        if (a.has("posts")) b.posts = points(a.get("posts"));
        if (a.has("grenades")) {
            List<ResourceLocation> g = new ArrayList<>();
            a.getAsJsonArray("grenades").forEach(e -> g.add(ResourceLocation.parse(e.getAsString())));
            b.grenades = g;
            issue(b, g);
        }
        if (a.has("throw")) {
            b.throwItem = ResourceLocation.parse(a.get("throw").getAsString());
            b.throwAt = point(a.getAsJsonArray("at"));
            b.throwPhase = 0;
        }
        return describe(b);
    }

    public static JsonArray status() {
        JsonArray out = new JsonArray();
        BOTS.values().forEach(b -> out.add(describe(b)));
        return out;
    }

    public static JsonArray squad(JsonObject a) {
        String team = a.get("team").getAsString();
        String group = a.has("squad") ? a.get("squad").getAsString() : null;
        Vec3 objective = point(a.getAsJsonArray("anchor"));
        if (!Double.isFinite(objective.lengthSqr())) throw new IllegalArgumentException("Finite anchor required");
        Mode mode = a.has("mode") ? Mode.valueOf(a.get("mode").getAsString().toUpperCase(Locale.ROOT)) : Mode.ADVANCE;
        var members = BOTS.values().stream().filter(b -> !b.soldier && b.team.equals(team) && (group == null || b.squad.equals(group))).toList();
        if (members.isEmpty()) throw new IllegalArgumentException("No matching scripted squad");
        MinecraftServer server = net.neoforged.neoforge.server.ServerLifecycleHooks.getCurrentServer();
        Vec3 center = Vec3.ZERO;
        int count = 0;
        for (Bot b : members) {
            ServerPlayer p = server.getPlayerList().getPlayerByName(b.name);
            if (p != null) { center = center.add(p.position()); count++; }
        }
        Vec3 forward = flat(objective.subtract(count == 0 ? objective.subtract(0, 0, 1) : center.scale(1.0 / count)));
        if (forward.lengthSqr() == 0) forward = new Vec3(0, 0, 1);
        JsonArray out = new JsonArray();
        for (int i = 0; i < members.size(); i++) {
            Bot b = members.get(i);
            b.anchor = BotSquad.formation(objective, forward, b.role, i);
            b.mode = mode;
            b.cover = null;
            b.route.clear();
            b.path.clear();
            out.add(describe(b));
        }
        return out;
    }

    /** Drops the filming bots' orders; spawn-egg soldiers keep theirs. */
    public static String clear() {
        MinecraftServer server = net.neoforged.neoforge.server.ServerLifecycleHooks.getCurrentServer();
        int n = 0;
        for (var it = BOTS.values().iterator(); it.hasNext(); ) {
            Bot b = it.next();
            if (b.soldier) continue;
            ServerPlayer p = server == null ? null : server.getPlayerList().getPlayerByName(b.name);
            if (p != null && scripted(p)) {
                move(p, 0, 0, false, false);
                p.setJumping(false);
                BotMedical.cancel(p);
                p.getInventory().selected = 0;
                AhfStance.request(p, StanceRequest.STAND);
            }
            it.remove();
            n++;
        }
        if (BOTS.isEmpty()) BotSquad.clear();
        return "cleared " + n + " bots";
    }

    private static JsonObject describe(Bot b) {
        JsonObject o = new JsonObject();
        o.addProperty("name", b.name);
        o.addProperty("voice", SoldierVoices.profile(b));
        o.addProperty("last_callout", b.lastCallout);
        o.addProperty("callouts", b.callouts);
        o.addProperty("team", b.team);
        o.addProperty("squad", b.squad);
        o.addProperty("squad_task", b.squadTask);
        o.addProperty("patient", b.patient);
        o.addProperty("rescuer", b.rescuer);
        o.addProperty("reports", b.reports);
        o.addProperty("heard_shots", b.heardShots);
        o.addProperty("stalls", b.stalls);
        o.addProperty("yielding", b.yielding);
        o.addProperty("treatments", b.treatments);
        o.addProperty("revives", b.revives);
        o.addProperty("revive_attempts", b.reviveAttempts);
        o.addProperty("medical_action", b.careAction);
        o.addProperty("medical_patient", b.carePatient);
        o.addProperty("medical_downed", b.health.downed());
        o.addProperty("medical_hands", b.health.hands());
        o.addProperty("medical_legs", b.health.legs());
        o.addProperty("blood_fraction", b.health.blood());
        o.addProperty("bleeding", b.health.bleeding());
        o.addProperty("mode", b.mode.name().toLowerCase(Locale.ROOT));
        o.addProperty("state", b.state);
        o.addProperty("target", b.target);
        o.addProperty("threats", b.threats.size());
        o.addProperty("suppression", Math.round(b.suppression * 100) / 100f);
        o.addProperty("verdict", b.verdict);
        o.addProperty("shots", b.shots);
        o.addProperty("reloads", b.reloads);
        o.addProperty("deaths", b.deaths);
        o.addProperty("throws", b.thrown);
        o.addProperty("smokes_left", b.smokes);
        o.addProperty("frags_left", b.frags);
        o.addProperty("pushes", b.pushes);
        o.addProperty("retreating", b.retreating);
        o.addProperty("need_reload", b.needReload);
        o.addProperty("dry", b.dry);
        o.addProperty("last_stall", b.stallInfo);
        o.addProperty("offline_ticks", b.dead);
        o.addProperty("downed_ticks", b.down);
        o.addProperty("peek_phase", b.peek.phase().name().toLowerCase(Locale.ROOT));
        o.addProperty("peek_cycles", b.peek.cycles());
        o.addProperty("known_grenades", b.danger.count());
        o.addProperty("grenade_escapes", b.escapes);
        JsonArray danger = new JsonArray();
        o.add("grenade_danger", danger);
        o.addProperty("lean", b.lastLean);
        o.addProperty("posture", b.lastPosture.name().toLowerCase(Locale.ROOT));
        if (b.cover != null) {
            o.addProperty("cover", b.cover.cell().toShortString());
            o.addProperty("peek_lean", b.cover.peek().lean());
        }
        MinecraftServer server = net.neoforged.neoforge.server.ServerLifecycleHooks.getCurrentServer();
        ServerPlayer p = server == null ? null : server.getPlayerList().getPlayerByName(b.name);
        if (p != null) {
            JsonObject supplies = new JsonObject();
            SoldierServices.medical().supplies(p).forEach(supplies::addProperty);
            o.add("medical_items", supplies);
            for (BotGrenades.Notice n : b.danger.reacting(p.serverLevel().getGameTime())) {
                JsonObject notice = new JsonObject();
                JsonArray position = new JsonArray();
                position.add(n.at().x); position.add(n.at().y); position.add(n.at().z);
                notice.add("position", position);
                notice.addProperty("sense", n.sense());
                danger.add(notice);
            }
            Vec3 eye = p.getEyePosition();
            JsonArray at = new JsonArray(), view = new JsonArray();
            at.add(p.getX()); at.add(p.getY()); at.add(p.getZ());
            view.add(eye.x); view.add(eye.y); view.add(eye.z);
            o.add("position", at);
            o.add("eye", view);
            StanceState stance = AhfStance.get(p);
            if (stance != null) {
                o.addProperty("lean_amount", stance.lean(1));
                o.addProperty("effective_posture", stance.effective().name().toLowerCase(Locale.ROOT));
            }
        }
        return o;
    }

    static void onServerTick(ServerTickEvent.Post e) {
        if (BOTS.isEmpty()) return;
        MinecraftServer server = e.getServer();
        boolean filmingServer = filming.test(server);
        long tickStart = System.nanoTime();
        int driven = 0;
        long squadStart = SoldierProfiler.begin();
        BotSquad.tick(server, BOTS, server.overworld().getGameTime());
        SoldierProfiler.end("squad", squadStart);
        for (var it = BOTS.values().iterator(); it.hasNext(); ) {
            Bot b = it.next();
            if (!runsOn(b, server, filmingServer)) continue;
            ServerPlayer p = server.getPlayerList().getPlayerByName(b.name);
            if (p == null || !p.isAlive()) {
                if (p == null && b.soldier) it.remove();      // killed or removed: a soldier does not come back
                else if (p == null) respawn(server, b);
                continue;
            }
            if (!scripted(p)) continue;                        // never a real player
            if (b.soldier) SoldierBrain.tick(server, p, b);
            if (b.dead > 0) {
                b.dead = 0;
                b.arm = b.gun != null;
                b.stanceDirty = true;
                b.cover = null;
                b.route.clear();
                b.threats.clear();
                b.suppression = 0;
                b.danger.clear();
                b.evading = false;
                b.careAction = "";
                b.carePlan = null;
                b.carePatient = "";
                b.oriented = false;
                b.peek.reset(p.serverLevel().getGameTime());
            }
            if (b.arm) {
                b.arm = false;
                arm(p, b.gun, b.mags, b.grenades);
                issue(b, b.grenades);
                BotMedical.kit(p, b.role.equals("medic"));
            }
            long healthStart = SoldierProfiler.begin();
            b.health = BotMedical.condition(p);
            SoldierProfiler.end("condition", healthStart);
            if (b.health.downed()) {
                move(p, 0, 0, false, false);
                p.setJumping(false);
                pose(p, b, Posture.PRONE, 0, false);
                b.moving = false;
                b.state = "downed";
                BotMedical.cancel(p);
                // Leave time for approach, bleeding control, blood replacement and the real CPR channel.
                if (b.down == 0) b.giveUpAt = 1200 + RNG.nextInt(200);
                if (++b.down >= b.giveUpAt && b.rescuer.isEmpty() && b.down % 100 == 0) giveUp(p);
                continue;
            }
            b.down = 0;
            b.lastSeenAt = p.position();
            long driveStart = System.nanoTime();
            drive(server, p, b);
            SoldierProfiler.driveNanos += System.nanoTime() - driveStart;
            driven++;
        }
        SoldierProfiler.endTick(System.nanoTime() - tickStart, driven);
    }

    private static void respawn(MinecraftServer server, Bot b) {
        if (b.dead++ == 0) {
            b.deaths++;
            if (b.lastSeenAt != null) {    // Carpet removes the dead fake at once: mark where the body went
                server.overworld().sendParticles(net.minecraft.core.particles.ParticleTypes.POOF, b.lastSeenAt.x,
                        b.lastSeenAt.y + 0.4, b.lastSeenAt.z, 12, 0.3, 0.3, 0.3, 0.02);
            }
        }
        b.moving = false;
        if (b.dead == b.respawnTicks && !b.posts.isEmpty()) {
            // a posted soldier who died falls back to the next post instead of walking into the same sights
            Vec3 post = b.posts.get(b.deaths % b.posts.size());
            b.spawn = post;
            b.anchor = post;
        }
        if (b.spawn == null || b.dead != b.respawnTicks) return;
        server.getCommands().performPrefixedCommand(server.createCommandSourceStack().withSuppressedOutput(),
                String.format(Locale.ROOT, "player %s spawn at %.2f %.2f %.2f facing %.1f 0 in minecraft:overworld in survival",
                        b.name, b.spawn.x, b.spawn.y, b.spawn.z, b.spawnYaw));
    }

    // --- perception -------------------------------------------------------------------------------------------

    public static void onGunshot(ServerPlayer source, Vec3 origin) {
        if (BOTS.isEmpty()) return;
        boolean filmingServer = filming.test(source.server);
        Bot shooter = BOTS.get(source.getGameProfile().getName().toLowerCase(Locale.ROOT));
        long now = source.level().getGameTime();
        for (Bot b : BOTS.values()) {
            if (!runsOn(b, source.server, filmingServer) || !hostile(b, shooter)) continue;
            ServerPlayer p = source.server.getPlayerList().getPlayerByName(b.name);
            if (p == null || p == source || p.level() != source.level()) continue;
            boolean clear = BotTactics.clearRay(p.serverLevel(), p.getEyePosition(), origin, p);
            if (!BotSenses.hears(p.getEyePosition(), origin, clear)) continue;
            Threat heard = b.threats.computeIfAbsent(source.getGameProfile().getName().toLowerCase(Locale.ROOT), k -> Threat.told());
            if (heard.visible > 0) continue;
            // A sound gives an approximate firing location, not a tracked enemy behind a wall.
            double error = clear ? 1.0 : 3.0;
            double angle = Math.toRadians(Math.floorMod(b.name.hashCode(), 360));
            heard.eye = source.getEyePosition().add(Math.cos(angle) * error, 0, Math.sin(angle) * error);
            heard.seen = now;
            heard.reaction = 6;
            b.heardShots++;
        }
    }

    /** Whether {@code observer} counts {@code other} as an enemy; a null {@code other} is a real player. */
    private static boolean hostile(Bot observer, Bot other) {
        return other == null ? observer.huntsPlayers : !other.team.equals(observer.team);
    }

    private static void perceive(MinecraftServer server, ServerPlayer p, Bot b, long now) {
        if (p.hurtTime == 9) {
            b.suppression = Math.min(1, b.suppression + 0.45f);
            SoldierVoices.say(p, b, "wounded");
        }
        b.suppression = Math.max(0, b.suppression - 0.012f);
        for (Bot other : BOTS.values()) {
            if (other.team.equals(b.team)) continue;
            observe(p, b, server.getPlayerList().getPlayerByName(other.name), other.name.toLowerCase(Locale.ROOT), now, false);
        }
        if (b.huntsPlayers) {
            for (ServerPlayer t : server.getPlayerList().getPlayers()) {
                String key = t.getGameProfile().getName().toLowerCase(Locale.ROOT);
                if (scripted(t) || BOTS.containsKey(key)) continue;
                observe(p, b, t.isSpectator() || t.isCreative() ? null : t, key, now, true);
            }
        }
        b.threats.values().removeIf(th -> now - th.seen > FORGET_TICKS);
    }

    /** Updates {@code b}'s memory of one enemy. {@code spare} = a real player: a downed one is no longer a target. */
    private static void observe(ServerPlayer p, Bot b, ServerPlayer t, String enemy, long now, boolean spare) {
        Threat th = b.threats.get(enemy);
        Threat glimpse = b.glimpses.get(enemy);
        boolean seeable = t != null && t.isAlive() && t.level() == p.level() && t.distanceTo(p) <= b.range
                && !(spare && BotMedical.condition(t).downed());
        Vec3 seen = seeable && BotSenses.inView(p.getEyePosition(), p.getLookAngle(), t.getEyePosition()) ? seen(p, t) : null;
        if (seen == null) {
            if (th != null) {
                th.visible = 0;
                th.value = Math.max(0f, th.value - BotSenses.FADE);
            }
            if (glimpse != null && (glimpse.value -= 0.03f) <= 0) b.glimpses.remove(enemy);
            return;
        }
        // Awareness builds: a person who is not yet noticed is not yet a target and their position is not known.
        Threat track = th != null ? th : glimpse != null ? glimpse : new Threat();
        if (th == null && glimpse == null) {
            track.value = 0f;
            b.glimpses.put(enemy, track);
        }
        double speed = t.getDeltaMovement().horizontalDistance();
        track.value = Math.min(1.5f, track.value + BotSenses.detectionGain(t.distanceTo(p), b.range, speed,
                t.isCrouching(), b.suppression));
        if (track.value < 1f) {
            if (th != null) th.visible = 0;
            return;
        }
        if (th == null) {
            b.glimpses.remove(enemy);
            b.threats.put(enemy, track);
            th = track;
            SoldierVoices.say(p, b, "contact");
        }
        th.reaction = 2;
        th.visible++;
        th.eye = t.getEyePosition();
        th.seen = now;
        if (!th.downReported && now % 10 == 0 && !SoldierVoices.ids().isEmpty() && BotMedical.condition(t).downed()) {
            th.downReported = true;
            SoldierVoices.say(p, b, "enemy_down");
        }
        BotSquad.report(p.server, BOTS, b, enemy, th, now);
    }

    private static List<Vec3> threatEyes(Bot b, Vec3 from) {
        return b.threats.values().stream().sorted(Comparator.comparingDouble((Threat t) -> -t.seen)
                .thenComparingDouble(t -> t.eye.distanceToSqr(from))).limit(4).map(t -> t.eye).toList();
    }

    /** Engage: the visible, acquired enemy (nearest), else null. */
    private static ServerPlayer target(MinecraftServer server, ServerPlayer p, Bot b) {
        ServerPlayer best = null;
        double bestD = Double.MAX_VALUE;
        for (Map.Entry<String, Threat> e : b.threats.entrySet()) {
            Threat th = e.getValue();
            if (th.visible == 0 || th.visible < th.reaction) continue;
            ServerPlayer t = server.getPlayerList().getPlayerByName(e.getKey());
            if (t == null || !t.isAlive()) continue;
            double d = t.distanceToSqr(p) * (e.getKey().equalsIgnoreCase(b.target) ? 0.6 : 1);  // stick with a target
            if (d < bestD) {
                best = t;
                bestD = d;
            }
        }
        return best;
    }

    // --- decision ---------------------------------------------------------------------------------------------

    /** Rounds in the gun, the fullest spare magazine (0 when there is none) and a magazine's capacity; -1 when unarmed. */
    private static int[] rounds(ServerPlayer p) {
        SoldierWeapon.Ammo ammo = SoldierServices.weapon().ammo(p);
        return ammo == null ? new int[]{-1, 0, 0} : new int[]{ammo.left(), ammo.spare(), ammo.capacity()};
    }

    private static void ammo(ServerPlayer p, Bot b) {
        int[] r = rounds(p);
        if (r[0] < 0) {
            b.needReload = b.dry = false;
            return;
        }
        b.needReload = BotPolicy.ammoLow(r[0], r[2]) && BotPolicy.worthReloading(r[0], r[2], r[1]);
        b.dry = BotPolicy.dry(r[0], r[1]);
    }

    private static void drive(MinecraftServer server, ServerPlayer p, Bot b) {
        long now = p.serverLevel().getGameTime();
        if (!b.oriented) {
            b.watchYaw = p.getYRot();
            b.oriented = true;
        }
        long t0 = SoldierProfiler.begin();
        perceive(server, p, b, now);
        SoldierProfiler.end("perceive", t0);
        t0 = SoldierProfiler.begin();
        ammo(p, b);
        SoldierProfiler.end("ammo", t0);
        t0 = SoldierProfiler.begin();
        boolean evading = evadeGrenades(p, b, now);
        SoldierProfiler.end("grenade_evade", t0);
        if (evading) { SoldierVoices.say(p, b, "grenade"); return; }
        t0 = SoldierProfiler.begin();
        boolean caring = medical(server, p, b, now);
        SoldierProfiler.end("medical", t0);
        if (caring) return;
        if (b.hunting) SoldierVoices.say(p, b, "search");
        else if (b.threats.isEmpty()) SoldierVoices.say(p, b, "idle");
        else if (b.suppression > 0.75f) SoldierVoices.say(p, b, "retreat");
        considerGrenades(server, p, b, now);
        if (b.throwPhase >= 0) {
            throwGrenade(p, b);
            return;
        }
        ServerPlayer target = target(server, p, b);
        String was = b.target;
        b.target = target == null ? "" : target.getGameProfile().getName();
        if (!was.isEmpty() && target == null) SoldierVoices.say(p, b, "lost_contact");
        b.onTarget = b.target.equals(was) && target != null ? b.onTarget + 1 : 0;

        // where to stand: scripted path, else the mode's choice
        t0 = SoldierProfiler.begin();
        Vec3 goal = b.medicalRetreat && !b.threats.isEmpty() || b.path.isEmpty() ? goal(server, p, b, now) : b.path.peek();
        SoldierProfiler.end("goal", t0);
        while (!b.path.isEmpty() && at(p, b.path.peek(), 0.45)) b.path.poll();
        if (!java.util.Objects.equals(b.peekingCover, b.cover)) {
            b.peekingCover = b.cover;
            b.peek.reset(now);
        }
        boolean pinned = b.suppression > 0.75f || b.dry;
        boolean busy = SoldierServices.weapon().busy(p) || SoldierServices.weapon().blocked(p);
        boolean nearCover = b.cover != null && BotTactics.horizontal(p.position(), Vec3.atBottomCenterOf(b.cover.cell())) < 2.6;
        boolean hasThreat = target != null || !b.threats.isEmpty();
        boolean atHome = goal == null || b.cover != null && at(p, Vec3.atBottomCenterOf(b.cover.cell()), 0.6);
        b.peek.tick(now, atHome && (hasThreat || b.crouch && !b.soldier) && b.path.isEmpty() && b.fire != Fire.OFF,
                !b.engaged && (pinned || busy || b.needReload) || b.fire == Fire.OFF || !b.path.isEmpty()
                        || goal != null && !nearCover && !b.engaged);
        // A firing position remains the goal until the whole peek ends, not merely until the next burst pause.
        boolean workingCover = nearCover && b.path.isEmpty();
        if (workingCover) goal = b.peek.exposed() ? b.cover.peek().feet() : Vec3.atBottomCenterOf(b.cover.cell());
        // Already looking at the enemy from here: there is nothing to gain from stepping out to the lean position.
        if (workingCover && b.engaged && target != null && seen(p, target) != null) goal = null;
        // A decimeter short of the planned edge can leave a fully leaned eye behind the wall.
        t0 = SoldierProfiler.begin();
        Vec3 step = steer(p, b, goal, workingCover ? 0.04 : 0.45);
        SoldierProfiler.end("steer", t0);
        boolean arrivedNow = b.moving && step == null;
        b.moving = step != null;
        if (arrivedNow) {
            b.arrived = now;
            b.watchYaw = b.rest != null && b.threats.isEmpty() && at(p, b.rest, 1.0) ? b.restYaw : p.getYRot();
        }
        boolean sprinting = b.moving && !workingCover && b.sprint && !pinned && !b.health.sprint()
                && (target == null || b.mode == Mode.ADVANCE && !inCover(p, b) && p.distanceTo(target) > 35);

        // aim: target chest/head, else suppress a fresh last-seen position, else face the route
        Vec3 aim = null;
        Vec3 seenAim = target != null && !sprinting ? seen(p, target) : null;
        if (seenAim != null) aim = seenAim;
        trackStillness(b, target);
        if (aim == null && !sprinting && (!b.moving || workingCover)) aim = suppressionPoint(b, now);
        if (aim == null && workingCover && hasThreat) aim = threatEyes(b, p.getEyePosition()).get(0).subtract(0, 0.5, 0);
        if (aim == null && !b.moving && !b.threats.isEmpty())
            aim = threatEyes(b, p.getEyePosition()).getFirst().subtract(0, 0.5, 0);
        aimAt(p, b, aim, step, target);

        // An enemy in plain sight is shot at now, from wherever this soldier stands: the enemy can see it too, so
        // hiding first would only give them the shot. It stays up while it has the target, and falls back otherwise.
        boolean direct = seenAim != null && !b.dry && (!b.needReload || p.distanceTo(target) < 12);
        b.engaged = direct;
        if (direct) b.peek.engage(now);
        boolean peek = b.peek.exposed() && !sprinting;
        boolean low = !peek && (!b.moving || workingCover) && (b.crouch || workingCover);
        Vec3 dir = step;
        boolean crawl = b.moving && !workingCover && (b.suppression > 0.45f || b.health.legs());
        walk(p, b, dir, sprinting, low || crawl, !workingCover);

        Posture posture = crawl ? Posture.PRONE : peek && workingCover ? b.cover.peek().posture()
                : low ? Posture.KNEEL : Posture.STAND;
        float lean = peek ? workingCover ? b.cover.peek().lean() : b.lean : 0;
        boolean ads = aim != null && peek;
        pose(p, b, posture, lean, ads);
        boolean clear = aim == null || !friendlyInLine(server, p, b, aim);
        if (!clear) b.verdict = "friendly";
        Vec3 aimDelta = aim == null ? Vec3.ZERO : aim.subtract(p.getEyePosition());
        boolean onAim = aim != null && Math.abs(Mth.wrapDegrees(yawTo(p, aim) + b.jitterYaw - p.getYRot())) < FIRE_CONE
                && Math.abs(-Math.toDegrees(Math.atan2(aimDelta.y, aimDelta.horizontalDistance()))
                + b.jitterPitch - b.climb - p.getXRot()) < FIRE_CONE;
        StanceState stance = AhfStance.get(p);
        boolean settled = stance != null && stance.effective() == posture && stance.blend(1) >= 0.99f
                && Math.abs(stance.lean(1) - lean) < 0.08f;
        boolean line = aim != null && BotTactics.sees(p.serverLevel(), p.getEyePosition(), aim, p);
        boolean canShoot = direct
                ? clear && line && onAim && (settled || b.onTarget > 6 || p.distanceTo(target) < 15)
                : clear && !b.moving && b.peek.settled() && settled && line && onAim;
        t0 = SoldierProfiler.begin();
        trigger(server, p, b, aim, canShoot, inCover(p, b) || low);
        SoldierProfiler.end("trigger", t0);
        b.state = (b.moving ? (sprinting ? "rush" : crawl ? "crawl" : "move") : low ? "cover" : peek ? "peek" : "stand")
                + (pinned ? "/pinned" : "") + (SoldierServices.weapon().blocked(p) ? "/wounded" : "");
    }

    /** How long the target has stayed about where it is: a soldier's aim settles on someone who keeps still. */
    private static void trackStillness(Bot b, ServerPlayer target) {
        if (target == null) {
            b.targetAnchor = null;
            b.targetStill = 0;
        } else if (b.targetAnchor != null && target.position().distanceToSqr(b.targetAnchor) < 2.25) {
            b.targetStill++;
        } else {
            b.targetAnchor = target.position();
            b.targetStill = 0;
        }
    }

    /** Searches (cover, routes) one tick may start across all soldiers; the rest wait a tick, so a firefight cannot spike the thread. */
    private static final int SEARCHES_PER_TICK = 3;

    private static boolean searchReady(Bot b, long now) {
        return now - b.searched >= 20 && SoldierProfiler.covers + SoldierProfiler.routes < SEARCHES_PER_TICK;
    }

    private static Threat freshest(Bot b) {
        Threat best = null;
        for (Threat t : b.threats.values()) if (t.eye != null && (best == null || t.seen > best.seen)) best = t;
        return best;
    }

    /** Where this soldier wants to be now (null = stay). */
    private static Vec3 goal(MinecraftServer server, ServerPlayer p, Bot b, long now) {
        if (b.anchor == null) return null;
        if (b.squadTask.equals("rescue-cover") && b.cover == null && b.threats.isEmpty()) return null;
        List<Vec3> eyes = threatEyes(b, p.getEyePosition());
        BlockPos feet = feet(p);
        // Healing, reloading or out of ammunition with the enemy about: back off from the corner first, then do it.
        boolean retreat = b.medicalRetreat || (b.needReload || b.dry) && !eyes.isEmpty();
        if (retreat && !eyes.isEmpty()) {
            if (!b.retreating) {
                b.retreating = true;
                b.cover = null;
                b.careShelter = null;
                b.route.clear();
            }
            Vec3 primary = eyes.get(0);
            if (b.cover == null && searchReady(b, now)) {
                b.searched = now;
                Vec3 away = BotTactics.horizontal(p.position(), primary) < 1e-3 ? Vec3.ZERO
                        : flat(p.position().subtract(primary));
                boolean near = BotTactics.horizontal(p.position(), primary) < 16;
                BlockPos centre = near ? BlockPos.containing(p.position().add(away.scale(5))) : feet;
                b.cover = BotTactics.findCover(p.serverLevel(), p, feet, new BlockPos(centre.getX(), feet.getY(), centre.getZ()),
                        8, eyes, null, 0, taken(b), false);
                if (b.cover != null && BotTactics.horizontal(Vec3.atBottomCenterOf(b.cover.cell()), primary)
                        < BotTactics.horizontal(p.position(), primary) - 1) b.cover = null;   // never toward them
            }
            if (b.cover != null) return at(p, Vec3.atBottomCenterOf(b.cover.cell()), 0.6)
                    ? null : Vec3.atBottomCenterOf(b.cover.cell());
            if (b.careShelter == null) b.careShelter = p.position().add(flat(p.position().subtract(eyes.getFirst())).scale(6));
            return at(p, b.careShelter, 0.6) ? null : b.careShelter;
        }
        if (b.retreating) {                                  // done: the corner is contested again, take a fresh look
            b.retreating = false;
            b.cover = null;
            b.careShelter = null;
            b.route.clear();
        }
        if (b.mode == Mode.POST) {
            if (!at(p, b.anchor, 0.8)) return b.anchor;
            if (!eyes.isEmpty() && b.peek.hidden() && now - b.coverChecked >= 30) {
                BotTactics.Peek peek = BotTactics.exposed(p.serverLevel(), feet, eyes, BotTactics.KNEEL_HIGH, p)
                        ? null : BotTactics.peekFrom(p.serverLevel(), p, feet, eyes.get(0));
                b.cover = peek == null ? null : new BotTactics.Cover(feet, peek, 0);
                b.coverChecked = now;
            }
            return null;
        }
        if (b.cover != null && !b.peek.hidden()) return null;
        if (eyes.isEmpty()) {
            // quiet: ADVANCE walks on; HOLD waits by a wall near its anchor, not in the middle of the street
            if (b.mode == Mode.HOLD) {
                Vec3 rest = restSpot(p, b);
                if (rest != null) return at(p, rest, 0.7) ? null : rest;
            }
            return at(p, b.anchor, b.mode == Mode.ADVANCE ? 2 : b.holdRadius) ? null : b.anchor;
        }
        Threat fresh = freshest(b);
        long age = fresh == null ? 0 : now - fresh.seen;
        boolean nearObjective = at(p, b.anchor, 4);
        boolean coverValid = b.cover != null && now - b.coverChecked < 30;
        if (b.cover != null && now - b.coverChecked >= 30) {
            // re-check: flanked (a threat now sees the kneeling body) => look again
            b.coverChecked = now;
            if (BotTactics.exposed(p.serverLevel(), b.cover.cell(), eyes, BotTactics.KNEEL_HIGH, p)) b.cover = null;
            coverValid = b.cover != null;
        }
        boolean mayBound = mayBound(server, b, now);
        if (b.mode == Mode.ADVANCE && !nearObjective && coverValid && !b.moving && searchReady(b, now) && mayBound) {
            b.searched = now;
            BotTactics.Cover next = BotTactics.findCover(p.serverLevel(), p, feet, feet, 12, eyes, b.anchor, 3,
                    taken(b), true);
            if (next != null) {
                b.cover = next;
                b.coverChecked = now;
            } else if (b.suppression < 0.5f && age > 100) {
                return toward(p.position(), b.anchor, 7);                  // no cover ahead and nobody in sight: move up
            }
        }
        // Press the contact: it has not shown itself for a while, so take the next cover toward where it was.
        if (fresh != null && fresh.visible == 0 && coverValid && !b.moving && now - b.pushSearched >= 25 && mayBound
                && BotPolicy.shouldPush(now, fresh.seen, BotTactics.horizontal(p.position(), fresh.eye), b.suppression,
                        !b.needReload && !b.dry, true, BotTactics.horizontal(p.position(), b.anchor), b.holdRadius)) {
            b.pushSearched = now;
            BotTactics.Cover next = BotTactics.findCover(p.serverLevel(), p, feet, feet, 12, eyes, fresh.eye, 3, taken(b), true);
            if (next != null && BotTactics.horizontal(Vec3.atBottomCenterOf(next.cell()), b.anchor)
                    <= b.holdRadius + BotPolicy.PUSH_LEASH) {
                b.cover = next;
                b.coverChecked = now;
                b.pushes++;
            }
        }
        if (!coverValid && b.cover == null && searchReady(b, now)) {   // searches cost thousands of rays
            b.searched = now;
            BlockPos center = b.mode == Mode.HOLD || nearObjective ? BlockPos.containing(b.anchor) : feet;
            int r = b.mode == Mode.HOLD || nearObjective ? b.holdRadius : 8;
            b.cover = BotTactics.findCover(p.serverLevel(), p, feet, new BlockPos(center.getX(), feet.getY(), center.getZ()),
                    r, eyes, null, 0, taken(b), true);
            if (b.cover == null) b.cover = BotTactics.findCover(p.serverLevel(), p, feet, feet, 6, eyes, null, 0, taken(b), false);
            b.coverChecked = now;
        }
        if (b.cover != null) {
            Vec3 c = Vec3.atBottomCenterOf(b.cover.cell());
            return BotTactics.horizontal(p.position(), c) > 0.5 ? c : null;
        }
        // No cover at all: never stand about in the open, take the least exposed spot near here and wait there.
        if (b.mode == Mode.ADVANCE && b.mayAdvance && !nearObjective && age > 100) return toward(p.position(), b.anchor, 7);
        if (searchReady(b, now)) {
            b.searched = now;
            BlockPos spot = BotTactics.leastExposed(p.serverLevel(), p, feet, 5, eyes);
            b.rest = spot == null ? null : Vec3.atBottomCenterOf(spot);
        }
        return b.rest != null && !at(p, b.rest, 0.5) ? b.rest : null;
    }

    /** A cell beside a wall (best: in a corner) within 4 blocks of the anchor; null in open ground. Cached per anchor. */
    private static Vec3 restSpot(ServerPlayer p, Bot b) {
        if (b.restFor == null || b.restFor.distanceToSqr(b.anchor) > 1) {
            b.restFor = b.anchor;
            BotTactics.Rest spot = BotTactics.restSpot(p.serverLevel(), p, BlockPos.containing(b.anchor), 4);
            b.rest = spot == null ? null : Vec3.atBottomCenterOf(spot.cell());
            b.restYaw = spot == null ? 0 : spot.yaw();
        }
        return b.rest;
    }

    /** Bounding overwatch: at most half the team (rounded up, at least one) is on the move. */
    private static boolean mayBound(MinecraftServer server, Bot b, long now) {
        return b.mayAdvance && now - b.arrived >= 25 && b.suppression <= 0.5f;
    }

    private static List<BlockPos> taken(Bot b) {
        List<BlockPos> out = new ArrayList<>();
        for (Bot o : BOTS.values()) if (o != b && o.team.equals(b.team) && o.cover != null) out.add(o.cover.cell());
        return out;
    }

    private static boolean inCover(ServerPlayer p, Bot b) {
        return b.cover != null && BotTactics.horizontal(p.position(), Vec3.atBottomCenterOf(b.cover.cell())) < 0.8;
    }

    /** Fresh last-seen enemy position to put rounds on (machine guns always, riflemen sometimes). */
    private static Vec3 suppressionPoint(Bot b, long now) {
        if (b.fire == Fire.OFF) return null;
        boolean mg = b.role.equals("mg");
        if (!mg && (now / 30 + b.name.hashCode()) % 2 != 0) return null;
        Threat best = null;
        for (Threat t : b.threats.values()) {
            if (now - t.seen < SUPPRESS_TICKS && (best == null || t.seen > best.seen)) best = t;
        }
        return best == null ? null : best.eye.subtract(0, 0.5, 0);
    }

    // --- first aid and rescue ---------------------------------------------------------------------------------

    /** A treatment plan walks the inventory and every wound: recomputed twice a second per soldier, not every tick. */
    private static SoldierMedical.Plan cachedPlan(Bot b, ServerPlayer actor, ServerPlayer patient) {
        long now = actor.level().getGameTime();
        String key = patient.getGameProfile().getName();
        if (b.planFor.equals(key) && now - b.planAt < 10 && b.carePlan == null) return b.planCached;
        b.planFor = key;
        b.planAt = now + Math.floorMod(b.name.hashCode(), 5);
        b.planCached = BotMedical.plan(actor, patient);
        return b.planCached;
    }

    private static boolean medical(MinecraftServer server, ServerPlayer p, Bot b, long now) {
        if (!BotMedical.present()) return false;
        b.medicalRetreat = b.health.hands();
        ServerPlayer previous = b.carePatient.isEmpty() ? null : server.getPlayerList().getPlayerByName(b.carePatient);
        if (!b.careAction.isEmpty() && !BotMedical.active(p)) {
            if (b.careAction.equals("revive")) {
                if (previous != null && !BotMedical.condition(previous).downed()) {
                    b.revives++;
                    SoldierVoices.say(p, b, "revived");
                    b.reviveAttempts = 0;
                } else if (b.reviveAttempts >= 4) b.careRetry = now + 200;
            } else if (b.carePlan != null && BotMedical.count(p, b.carePlan.item()) < b.careCount) b.treatments++;
            b.careAction = "";
            b.carePlan = null;
            p.getInventory().selected = 0;
            b.stanceDirty = true;
            b.careRetry = Math.max(b.careRetry, now + 10);
        }
        if (BotMedical.active(p)) {
            if (p.hurtTime > 0 || b.health.hands() || b.suppression > 0.85f) {
                BotMedical.cancel(p);
                b.careAction = "";
                b.carePlan = null;
                p.getInventory().selected = 0;
                b.stanceDirty = true;
                b.careRetry = now + 30;
                return false;
            }
            move(p, 0, 0, false, true);
            p.setJumping(false);
            b.moving = false;
            if (previous != null && previous != p) aimAt(p, b, previous.position().add(0, 0.5, 0), null, null);
            pose(p, b, Posture.KNEEL, 0, false);
            b.state = b.careAction.equals("revive") ? "reviving" : previous == p ? "self-treat" : "treating-ally";
            return true;
        }
        if (b.health.legs() && b.patient.isEmpty() && cachedPlan(b, p, p) == null) {
            move(p, 0, 0, false, true);
            p.setJumping(false);
            b.moving = false;
            pose(p, b, Posture.PRONE, 0, false);
            b.state = "wounded-awaiting-medic";
            SoldierVoices.say(p, b, "help");
            return true;
        }
        ServerPlayer patient = b.patient.isEmpty() ? p : server.getPlayerList().getPlayerByName(b.patient);
        if (patient == null || !patient.isAlive() || patient.level() != p.level()) return false;
        SoldierMedical.Plan plan = b.health.hands() ? null : cachedPlan(b, p, patient);
        boolean revive = patient != p && BotMedical.condition(patient).downed() && plan == null;
        if (plan == null && !revive) return false;
        boolean self = patient == p;
        if (self && !b.mayTreat && !b.health.critical()) return false;
        if (self && !b.health.critical() && !b.threats.isEmpty() && !inCover(p, b)
                && (b.careShelter == null || !at(p, b.careShelter, 0.6))) {
            b.medicalRetreat = true;
            return false;
        }
        if (self && (b.health.hands() || now < b.careRetry)) return false;
        if (!self && p.distanceToSqr(patient) > 2.4 * 2.4) {
            Vec3 step = steer(p, b, patient.position(), 1.6);
            if (step == null) {
                b.careRetry = now + 100;
                b.patient = "";
                return false;
            }
            aimAt(p, b, null, step, null);
            pose(p, b, Posture.STAND, 0, false);
            walk(p, b, step, !b.health.sprint(), false, true);
            b.state = b.moving ? "rescue-approach" : "rescue-route-blocked";
            return true;
        }
        if (!self && !BotTactics.clearRay(p.serverLevel(), p.getEyePosition(), patient.position().add(0, 0.4, 0), p)) {
            Vec3 step = steer(p, b, patient.position(), 0.5);
            if (step == null) {
                b.careRetry = now + 100;
                b.patient = "";
                return false;
            }
            aimAt(p, b, null, step, null);
            walk(p, b, step, false, false, true);
            b.state = "rescue-approach";
            return true;
        }
        move(p, 0, 0, false, true);
        p.setJumping(false);
        b.moving = false;
        pose(p, b, Posture.KNEEL, 0, false);
        if (now < b.careRetry || b.health.hands()) {
            b.state = "medical-wait";
            return true;
        }
        SoldierServices.weapon().cancel(p);
        if (!b.carePatient.equals(patient.getGameProfile().getName())) b.reviveAttempts = 0;
        b.carePatient = patient.getGameProfile().getName();
        if (revive) {
            p.getInventory().selected = 0;
            if (BotMedical.revive(p, patient)) {
                b.reviveAttempts++;
                b.careAction = "revive";
                b.state = "reviving";
                SoldierVoices.say(p, b, "revive");
            } else b.careRetry = now + 20;
        } else {
            BotMedical.equip(p, plan);
            b.careCount = BotMedical.count(p, plan.item());
            if (BotMedical.begin(p, patient, plan)) {
                b.carePlan = plan;
                b.careAction = plan.item().toString();
                b.careShelter = null;
                b.state = self ? "self-treat" : "treating-ally";
                SoldierVoices.say(p, b, "treat");
            } else {
                p.getInventory().selected = 0;
                b.careRetry = now + 20;
            }
        }
        return true;
    }

    // --- grenades ---------------------------------------------------------------------------------------------

    /** Counts what the grenades issued are, and remembers which item is the frag and which the smoke. */
    private static void issue(Bot b, List<ResourceLocation> grenades) {
        b.frags = b.smokes = 0;
        b.fragItem = b.smokeItem = null;
        for (ResourceLocation id : grenades) {
            switch (SoldierServices.throwables().kind(id)) {
                case FRAG -> {
                    b.frags++;
                    b.fragItem = id;
                }
                case SMOKE -> {
                    b.smokes++;
                    b.smokeItem = id;
                }
                default -> {
                }
            }
        }
    }

    private static boolean smoke(ThrownCharge charge) {
        return SoldierServices.throwables().smoke(charge);
    }

    private static boolean friendlySmoke(ThrownCharge charge, Bot observer) {
        if (!smoke(charge) || !(charge.getOwner() instanceof ServerPlayer owner)) return false;
        Bot thrower = BOTS.get(owner.getGameProfile().getName().toLowerCase(Locale.ROOT));
        return thrower != null && thrower.team.equals(observer.team);
    }

    /** Hearing is driven by an actual grenade bounce, not a continuously audible live entity. */
    static void onGrenadeSound(PlayLevelSoundEvent.AtPosition event) {
        if (BOTS.isEmpty() || event.isCanceled() || !(event.getLevel() instanceof ServerLevel level)
                || event.getSound() == null || event.getSound().value() != Grenades.BOUNCE.get()
                ) return;
        boolean filmingServer = filming.test(level.getServer());
        Vec3 at = event.getPosition();
        // The sound is emitted during collision, before the charge's position is updated.
        ThrownCharge charge = level.getEntitiesOfClass(ThrownCharge.class, new AABB(at, at).inflate(3),
                        c -> c.phase() == ThrownCharge.Phase.LIVE).stream()
                .min(Comparator.comparingDouble(c -> c.position().distanceToSqr(at))).orElse(null);
        if (charge == null) return;
        for (Bot b : BOTS.values()) {
            if (!runsOn(b, level.getServer(), filmingServer)) continue;
            ServerPlayer p = level.getServer().getPlayerList().getPlayerByName(b.name);
            if (p == null || p.level() != level || !p.isAlive()) continue;
            if (friendlySmoke(charge, b)) continue; // the thrower called out smoke to their own squad
            boolean clear = BotTactics.clearRay(level, p.getEyePosition(), at.add(0, 0.1, 0), p);
            if (BotGrenades.audible(p.getEyePosition(), at, clear, event.getNewVolume())) {
                b.danger.notice(charge.getUUID(), at, level.getGameTime(), "heard");
            }
        }
    }

    /** Emergency locomotion overrides orders, cover, suppression and firing; orders remain queued. */
    private static boolean evadeGrenades(ServerPlayer p, Bot b, long now) {
        ServerLevel level = p.serverLevel();
        for (ThrownCharge c : level.getEntitiesOfClass(ThrownCharge.class,
                p.getBoundingBox().inflate(BotGrenades.SIGHT_RANGE), c -> c.phase() == ThrownCharge.Phase.LIVE)) {
            if (c.getOwner() == p && c.tickCount < 8) continue; // our own outgoing throw is expected
            Vec3 at = c.position().add(0, 0.06, 0);
            if (BotGrenades.visible(p.getEyePosition(), p.getLookAngle(), at,
                    BotTactics.sees(level, p.getEyePosition(), at, p))) {
                if (smoke(c)) {
                    b.danger.forget(c.getUUID());
                    continue;
                }
                b.danger.notice(c.getUUID(), at, now, "seen");
            }
        }
        b.danger.expire(now, id -> level.getEntity(id) instanceof ThrownCharge c && c.phase() == ThrownCharge.Phase.LIVE);
        List<Vec3> hazards = b.danger.reacting(now).stream().map(BotGrenades.Notice::at).toList();
        boolean safe = BotGrenades.clearance(p.position(), hazards) >= BotGrenades.SAFE_DISTANCE;
        if (hazards.isEmpty() || !b.evading && safe) {
            if (b.evading) {
                b.evading = false;
                b.route.clear();
                b.routeGoal = null;
                b.stanceDirty = true;
                b.peek.reset(now);
            }
            return false;
        }
        if (!b.evading) {
            b.evading = true;
            b.escapes++;
            b.escapePlanned = -1000;
            b.cover = null;
            b.route.clear();
            b.routeGoal = null;
            b.peek.reset(now);
            b.firing = 0;
            b.climb = 0;
            b.target = "";
            b.onTarget = 0;
            SoldierServices.weapon().cancel(p);
            BotMedical.cancel(p);
            b.careAction = "";
            b.carePlan = null;
            if (b.throwPhase >= 0) {
                SoldierServices.throwables().repin(p);
                b.throwPhase = b.throwPhase <= 20 ? 0 : -1;
            }
            p.getInventory().selected = 0;
        }
        if (safe) b.route.clear();
        else if (now - b.escapePlanned >= 8) {
            b.escapePlanned = now;
            b.route.clear();
            b.route.addAll(BotTactics.escape(level, p, feet(p), hazards));
        }
        while (!b.route.isEmpty() && at(p, b.route.peek(), 0.45)) b.route.poll();
        Vec3 next = b.route.peek();
        Vec3 step = next == null ? null : flat(next.subtract(p.position()));
        b.moving = step != null;
        b.stepUp = next != null && next.y > p.getY() + 0.6
                && BotTactics.horizontal(p.position(), next) < 1.6 && p.onGround();
        aimAt(p, b, null, step, null);
        pose(p, b, Posture.STAND, 0, false);
        walk(p, b, step, !b.health.sprint(), false, true);
        b.state = safe ? "grenade-safe" : b.moving ? "grenade-escape" : "grenade-blocked";
        b.verdict = "grenade";
        return true;
    }

    /** A frag at an enemy who went behind cover a moment ago (not one in plain sight), clear of teammates. */
    private static void considerGrenades(MinecraftServer server, ServerPlayer p, Bot b, long now) {
        if (b.throwPhase >= 0 || !b.careAction.isEmpty() || b.evading) return;
        considerSmoke(server, p, b, now);
        if (b.throwPhase < 0) considerFrag(server, p, b, now);
    }

    private static void throwAt(Bot b, ResourceLocation item, Vec3 at) {
        b.throwItem = item;
        b.throwAt = at;
        b.throwPhase = 0;
    }

    /** A frag on a contact that went to ground out of sight: rare, never close to friends, never from every soldier. */
    private static void considerFrag(MinecraftServer server, ServerPlayer p, Bot b, long now) {
        if (b.frags <= 0 || !b.target.isEmpty() || b.suppression > 0.5f || b.needReload
                || !BotPolicy.cooledDown(now, b.lastFrag, TEAM_FRAG.getOrDefault(b.team, -100000L),
                        BotPolicy.FRAG_COOLDOWN, BotPolicy.TEAM_FRAG_COOLDOWN) || RNG.nextInt(100) != 0) return;
        for (Threat t : b.threats.values()) {
            long since = now - t.seen;
            double d = BotTactics.horizontal(p.position(), t.eye);
            if (t.visible > 0 || since < 40 || since > 140 || d < 10 || d > 24) continue;
            Vec3 at = t.eye.subtract(0, 1.5, 0);
            for (Bot o : BOTS.values()) {
                ServerPlayer m = o.team.equals(b.team) ? server.getPlayerList().getPlayerByName(o.name) : null;
                if (m != null && m.position().distanceToSqr(at) < 49) return;   // never near our own
            }
            throwAt(b, b.fragItem, at);
            b.frags--;
            b.lastFrag = now;
            TEAM_FRAG.put(b.team, now);
            return;
        }
    }

    /**
     * Smoke to cover movement: onto the line between a casualty being treated and the enemy, or a little ahead of a
     * soldier who has the squad's lease to cross open ground with an enemy in sight.
     */
    private static void considerSmoke(MinecraftServer server, ServerPlayer p, Bot b, long now) {
        if (b.smokes <= 0 || b.suppression > 0.7f || b.threats.isEmpty() || !BotPolicy.cooledDown(now, b.lastSmoke,
                TEAM_SMOKE.getOrDefault(b.team, -100000L), BotPolicy.SMOKE_COOLDOWN, BotPolicy.TEAM_SMOKE_COOLDOWN)) return;
        Threat fresh = freshest(b);
        if (fresh == null || now - fresh.seen > 100) return;
        Vec3 toThreat = flat(fresh.eye.subtract(p.position()));
        if (Double.isNaN(toThreat.x)) return;
        Vec3 at = null;
        for (Bot o : BOTS.values()) {                                      // a downed friend someone is already reaching
            if (o == b || !o.team.equals(b.team) || !o.health.downed() || o.rescuer.isEmpty()) continue;
            ServerPlayer c = server.getPlayerList().getPlayerByName(o.name);
            if (c == null || c.level() != p.level() || c.distanceToSqr(p) > 22 * 22) continue;
            Vec3 between = flat(fresh.eye.subtract(c.position()));
            Vec3 chest = c.position().add(0, 0.5, 0);
            // only when the enemy can actually see the casualty right now: no point screening what is already hidden
            if (!Double.isNaN(between.x) && BotTactics.clearRay(p.serverLevel(), fresh.eye, chest, c)
                    && !SmokeScreens.blocked(p.level(), fresh.eye, chest)) at = c.position().add(between.scale(3.5));
            break;
        }
        double d = BotTactics.horizontal(p.position(), fresh.eye);
        if (at == null && b.squadTask.equals("bound") && b.mode != Mode.POST && fresh.visible > 0 && d >= 12 && d <= 32
                && b.cover != null && RNG.nextInt(200) == 0 && b.target.isEmpty()
                && BotTactics.clearRay(p.serverLevel(), fresh.eye, p.getEyePosition(), p)
                && !SmokeScreens.blocked(p.level(), fresh.eye, p.getEyePosition())) {
            at = p.position().add(toThreat.scale(Math.min(8, d * 0.4)));    // a screen to cross behind
        }
        if (at == null) return;
        throwAt(b, b.smokeItem, at);
        b.smokes--;
        b.lastSmoke = now;
        TEAM_SMOKE.put(b.team, now);
    }

    /** Through the player grenade path: hotbar to the grenade, pin (arm cocked), aim the arc, throw, back to the gun. */
    private static void throwGrenade(ServerPlayer p, Bot b) {
        move(p, 0, 0, false, false);
        int phase = b.throwPhase++;
        if (phase == 0) {
            int slot = -1;
            for (int i = 0; i < 9; i++) {
                if (BuiltInRegistries.ITEM.getKey(p.getInventory().getItem(i).getItem()).equals(b.throwItem)) slot = i;
            }
            if (slot < 0) {
                b.throwPhase = -1;
                return;
            }
            p.getInventory().selected = slot;
            b.stanceDirty = true;
            AhfStance.request(p, StanceRequest.STAND.withPosture(Posture.STAND).withLean(0).withAds(false, 0, 0));
        }
        double speed = SoldierServices.throwables().heldSpeed(p);
        if (!Double.isNaN(speed)) {
            Vec3 eye = p.getEyePosition();
            Vec3 d = b.throwAt.subtract(eye);
            float yaw = yawOf(d);
            p.setYRot(yaw);
            p.setYHeadRot(yaw);
            p.setXRot(throwPitch(d.horizontalDistance(), d.y, speed));
            if (phase == 4) {
                SoldierServices.throwables().pull(p);
                SoldierVoices.say(p, b, "throw_grenade");
            }
            if (phase == 20) {
                SoldierServices.throwables().release(p);
                b.thrown++;
            }
        }
        if (phase >= 28) {
            p.getInventory().selected = 0;
            b.throwPhase = -1;
        }
    }

    /** Minecraft pitch whose arc (WFLib ThrownCharge: gravity 0.024525, drag 0.995 per tick) lands at {@code dist},
     *  {@code dy}; a lob (20-75 degrees) when one reaches, else the flattest throw that does. */
    static float throwPitch(double dist, double dy, double speed) {
        float best = -30, flat = Float.NaN;
        double bestErr = Double.MAX_VALUE;
        for (int deg = -10; deg <= 75; deg++) {
            double r = Math.toRadians(deg), vx = Math.cos(r) * speed, vy = Math.sin(r) * speed, x = 0, y = 0;
            for (int t = 0; t < 200 && !(vy < 0 && y < dy); t++) {
                x += vx;
                y += vy;
                vx *= 0.995;
                vy = vy * 0.995 - 0.024525;
            }
            double err = Math.abs(x - dist);
            if (err < 0.8 && Float.isNaN(flat)) flat = -deg;
            if (deg >= 20 && err < bestErr) {
                bestErr = err;
                best = -deg;
            }
        }
        return bestErr < 0.8 || Float.isNaN(flat) ? best : flat;
    }

    // --- movement ---------------------------------------------------------------------------------------------

    /** Next horizontal direction toward {@code goal} along a danger-weighted route, or null when there. */
    private static Vec3 steer(ServerPlayer p, Bot b, Vec3 goal, double tolerance) {
        b.stepUp = false;
        if (goal == null || at(p, goal, tolerance)) {
            b.route.clear();
            b.routeGoal = null;
            return null;
        }
        if (BotTactics.horizontal(p.position(), goal) < 1 && Math.abs(p.getY() - goal.y) < 0.6) {
            double distance = BotTactics.horizontal(p.position(), goal);
            Vec3 delta = new Vec3(goal.x - p.getX(), 0, goal.z - p.getZ());
            if (p.level().noCollision(p, p.getBoundingBox().expandTowards(delta)))
                return flat(delta).scale(Math.min(1, distance * 2));
        }
        BlockPos g = BlockPos.containing(goal);
        collectRoute(b, g);
        if (g.equals(b.routeGoal) && b.route.isEmpty() && p.level().getGameTime() - b.routeChecked < 20) return null;
        if (!g.equals(b.routeGoal) || b.route.isEmpty() || p.tickCount % 60 == 0) {
            if (SoldierProfiler.covers + SoldierProfiler.routes >= SEARCHES_PER_TICK) return null;   // next tick
            if (b.pendingRoute != null) return b.route.isEmpty() ? null : routeStep(p, b);          // still searching
            List<BlockPos> occupied = new ArrayList<>();
            if (b.blockedCell != null && p.level().getGameTime() < b.blockedUntil) occupied.add(b.blockedCell);
            for (Bot ally : BOTS.values()) {
                if (ally == b || !ally.team.equals(b.team)) continue;
                ServerPlayer t = p.server.getPlayerList().getPlayerByName(ally.name);
                if (t != null && t.level() == p.level() && (!ally.moving
                        || BotMovement.yields(b.name, priority(b), ally.name, priority(ally)))) occupied.add(feet(t));
            }
            List<Vec3> eyes = threatEyes(b, p.getEyePosition()).stream().limit(2).toList();
            if (SoldierJobs.enabled()) {
                var job = SoldierJobs.route(p.serverLevel(), feet(p), g, eyes, occupied);
                if (job != null) {                                 // a worker has it: keep walking the old route meanwhile
                    b.pendingRoute = job;
                    b.pendingGoal = g;
                    b.routeChecked = p.level().getGameTime();
                    b.routeGoal = g;
                    return b.route.isEmpty() ? null : routeStep(p, b);
                }
            }
            b.routeChecked = p.level().getGameTime();
            b.routeGoal = g;
            b.route.clear();
            b.route.addAll(BotTactics.routeAvoiding(p.serverLevel(), p, feet(p), g, eyes, occupied));
            if (b.route.isEmpty()) return null;
        }
        return routeStep(p, b);
    }

    /** A finished worker search replaces the route, unless the soldier has since gone somewhere else. */
    private static void collectRoute(Bot b, BlockPos goal) {
        var job = b.pendingRoute;
        if (job == null) return;
        if (!goal.equals(b.pendingGoal)) {                          // wanted elsewhere now
            job.cancel(true);
            b.pendingRoute = null;
            b.routeGoal = null;
            return;
        }
        if (!job.isDone()) return;
        b.pendingRoute = null;
        try {
            List<Vec3> found = job.get();
            b.route.clear();
            b.route.addAll(found);
        } catch (java.util.concurrent.CancellationException | java.util.concurrent.ExecutionException | InterruptedException e) {
            b.route.clear();
        }
    }

    /** The next step along the route. */
    private static Vec3 routeStep(ServerPlayer p, Bot b) {
        while (b.route.size() > 1 && at(p, b.route.peek(), 0.5)) b.route.poll();
        Vec3 next = b.route.peek();
        // a step up on the route (stairs take it themselves, a full block needs a hop)
        b.stepUp = next.y > p.getY() + 0.6 && BotTactics.horizontal(p.position(), next) < 1.6 && p.onGround();
        return flat(next.subtract(p.position()));
    }

    /** The cell the feet stand in (on a stair's upper half, the cell above the stair). */
    private static BlockPos feet(ServerPlayer p) {
        return BlockPos.containing(p.getX(), p.getY() + 0.5, p.getZ());
    }

    /** Within {@code r} horizontally and on the same floor. */
    private static boolean at(ServerPlayer p, Vec3 v, double r) {
        return BotTactics.horizontal(p.position(), v) < r && Math.abs(p.getY() - v.y) < 1.2;
    }

    private static void unstick(ServerPlayer p, Bot b, boolean wantsMove) {
        b.still = wantsMove && p.position().distanceToSqr(b.lastPos) < 0.0004 ? b.still + 1 : 0;
        boolean stuck = b.still > 8;
        p.setJumping(wantsMove && b.stepUp); // jump only for a real planned step, never as a wall-stall animation
        if (stuck) {
            b.still = 0;
            b.stalls++;
            stallInfo(p, b);
            nudgeFree(p, b);
            b.blockedCell = BlockPos.containing(p.position().add(b.walkDirection.scale(1.1)));
            b.blockedUntil = p.level().getGameTime() + 60;
            b.route.clear();
            b.routeGoal = null;
        }
        if (p.position().distanceToSqr(b.lastPos) > 0.0004) b.progressAt = p.level().getGameTime();
        b.lastPos = p.position();
    }

    /** What the soldier walked into: position, heading and the blocks one step ahead at feet and head height. */
    private static void stallInfo(ServerPlayer p, Bot b) {
        Vec3 ahead = p.position().add(b.walkDirection.scale(0.8));
        BlockPos feet = BlockPos.containing(ahead);
        b.stallInfo = String.format(Locale.ROOT, "%.1f,%.1f,%.1f dir %.1f,%.1f feet=%s head=%s below=%s", p.getX(), p.getY(), p.getZ(),
                b.walkDirection.x, b.walkDirection.z, p.level().getBlockState(feet).getBlock().builtInRegistryHolder().key().location().getPath(),
                p.level().getBlockState(feet.above()).getBlock().builtInRegistryHolder().key().location().getPath(),
                p.level().getBlockState(feet.below()).getBlock().builtInRegistryHolder().key().location().getPath());
    }

    /** After a stall: slide sideways (the way with room) for half a second, then the route is searched again. */
    private static void nudgeFree(ServerPlayer p, Bot b) {
        Vec3 d = b.walkDirection;
        if (d.lengthSqr() < 1e-4) return;
        for (Vec3 side : new Vec3[]{new Vec3(-d.z, 0, d.x), new Vec3(d.z, 0, -d.x), d.scale(-1)}) {
            if (p.level().noCollision(p, p.getBoundingBox().move(side.normalize().scale(0.6)))) {
                b.nudge = side.normalize();
                b.nudgeTicks = 10;
                return;
            }
        }
    }

    private static int priority(Bot b) { return b.evading ? 0 : !b.patient.isEmpty() ? 1 : 2; }

    private static void walk(ServerPlayer p, Bot b, Vec3 step, boolean sprint, boolean crouch, boolean allowReroute) {
        b.yielding = false;
        if (b.nudgeTicks > 0 && step != null) {                 // sliding off whatever it was stuck on
            b.nudgeTicks--;
            step = b.nudge;
            sprint = false;
        }
        if (step != null && allowReroute) for (Bot ally : BOTS.values()) {
            if (ally == b || !ally.team.equals(b.team)) continue;
            ServerPlayer t = p.server.getPlayerList().getPlayerByName(ally.name);
            if (t == null || t.level() != p.level() || Math.abs(t.getY() - p.getY()) > 1.2
                    || t.position().subtract(p.position()).dot(step) <= 0
                    || BotTactics.horizontal(p.position().add(step.scale(0.8)), t.position()) >= 0.85) continue;
            if (!ally.moving || BotMovement.yields(b.name, priority(b), ally.name, priority(ally))) {
                b.yielding = true;
                b.yieldTicks++;
                b.blockedCell = feet(t);
                b.blockedUntil = p.level().getGameTime() + 40;
                b.route.clear();
                b.routeGoal = null;
                Vec3 avoid = null;
                if (b.yieldTicks > 8) {
                    for (Vec3 side : new Vec3[]{new Vec3(-step.z, 0, step.x), new Vec3(step.z, 0, -step.x), step.scale(-1)}) {
                        Vec3 at = p.position().add(side);
                        if (BotTactics.walkable(p.serverLevel(), BlockPos.containing(at))
                                && BotTactics.horizontal(at, t.position()) > 1.2
                                && p.level().noCollision(p, p.getBoundingBox().expandTowards(side))) {
                            avoid = side;
                            break;
                        }
                    }
                }
                step = avoid;
                sprint = false;
                break;
            }
        }
        if (!b.yielding) b.yieldTicks = 0;
        b.moving = step != null;
        b.walkDirection = step == null ? Vec3.ZERO : step;
        float forward = 0, strafe = 0;
        if (step != null) {
            double s = Math.sin(Math.toRadians(p.getYRot())), c = Math.cos(Math.toRadians(p.getYRot()));
            strafe = (float) (step.x * c + step.z * s);
            forward = (float) (step.z * c - step.x * s);
        }
        move(p, forward, strafe, sprint && b.moving, crouch);
        unstick(p, b, b.moving && allowReroute);
    }

    private static void pose(ServerPlayer p, Bot b, Posture posture, float lean, boolean ads) {
        if (!b.stanceDirty && ads == b.lastAds && posture == b.lastPosture && lean == b.lastLean) return;
        if (AhfStance.request(p, StanceRequest.STAND.withPosture(posture).withLean(lean).withAds(ads, 0, 0))) {
            b.stanceDirty = false;
            b.lastAds = ads;
            b.lastPosture = posture;
            b.lastLean = lean;
        } else b.stanceDirty = true;
    }

    // --- aiming and firing ------------------------------------------------------------------------------------

    private static void aimAt(ServerPlayer p, Bot b, Vec3 aim, Vec3 step, ServerPlayer target) {
        float yaw = p.getYRot(), pitch = p.getXRot();
        if (aim != null) {
            if (--b.jitterAge <= 0) {
                // aim quality: settles on a held target, worse when suppressed or the target moves
                boolean moving = target != null && target.getDeltaMovement().horizontalDistanceSqr() > 0.003;
                float err = b.spreadDeg * (1 + 1.5f * b.suppression) * (moving ? 1.5f : 1) * (b.moving ? 1.5f : 1)
                        * Math.max(0.5f, 2.4f - 0.06f * b.onTarget) * (target == null ? 1.6f : 1)
                        * Math.max(0.45f, 1f - 0.008f * b.targetStill);
                b.jitterAge = 5 + RNG.nextInt(6);
                b.jitterYaw = (float) RNG.nextGaussian() * err;
                b.jitterPitch = (float) RNG.nextGaussian() * err * 0.6f;
            }
            Vec3 d = aim.subtract(p.getEyePosition());
            yaw = yawOf(d) + b.jitterYaw;
            pitch = (float) -Math.toDegrees(Math.atan2(d.y, d.horizontalDistance())) + b.jitterPitch - b.climb;
        } else if (step != null) {
            yaw = yawOf(step);
            pitch = 0;
        } else if (b.threats.isEmpty()) {
            yaw = BotSenses.scan(b.watchYaw, p.level().getGameTime(), b.name.hashCode());
            pitch = 0;
        }
        float newYaw = p.getYRot() + Mth.clamp(Mth.wrapDegrees(yaw - p.getYRot()), -MAX_TURN, MAX_TURN);
        p.setYRot(newYaw);
        p.setXRot(Mth.clamp(p.getXRot() + Mth.clamp(pitch - p.getXRot(), -MAX_TURN, MAX_TURN), -89, 89));
        p.setYHeadRot(newYaw);
    }

    private static void trigger(MinecraftServer server, ServerPlayer p, Bot b, Vec3 aim, boolean canShoot, boolean safe) {
        SoldierWeapon weapon = SoldierServices.weapon();
        if (weapon.ammo(p) == null || weapon.busy(p)) return;
        if (weapon.jammed(p)) {
            weapon.unjam(p);
            b.verdict = "unjam";
            return;
        }
        if (b.fire == Fire.OFF || !canShoot) {
            if (b.resting > 0) b.resting--;
            b.firing = 0;
            b.climb = 0;
            // reload behind cover (or when nothing is in the way of doing it)
            if (b.needReload && (safe || rounds(p)[0] == 0) && weapon.reload(p)) {
                b.reloads++;
                SoldierVoices.say(p, b, "reload");
            }
            return;
        }
        if (b.fire == Fire.BURST) {
            if (b.resting > 0) {
                b.resting--;
                return;
            }
        }
        SoldierWeapon.Result shot = weapon.fire(p);
        b.verdict = shot.verdict();
        if (shot.shot() == SoldierWeapon.Shot.FIRED) {
            if (b.firing == 0) SoldierVoices.say(p, b, b.target.isEmpty() ? "covering" : "attack");
            b.shots++;
            b.lastShot = p.level().getGameTime();
            b.climb += b.role.equals("mg") ? 0.15f : 0.3f;           // muzzle climb within a burst
            suppress(server, p, b, aim);
            // like the stalkers' queue sizes: long bursts and short breaks up close, short bursts and long breaks far off
            double range = aim == null ? 30 : aim.distanceTo(p.getEyePosition());
            int burst = Math.max(1, Math.round(b.burst * (range < 12 ? 1.5f : range < 30 ? 1f : 0.6f)));
            int pause = Math.round(b.pause * (range < 12 ? 0.4f : range < 30 ? 1f : 1.7f));
            if (b.fire == Fire.BURST && ++b.firing >= burst) {
                b.firing = 0;
                b.climb = 0;
                b.resting = pause / 2 + RNG.nextInt(pause + 1);
            }
        }
        if (shot.shot() == SoldierWeapon.Shot.EMPTY && b.needReload && weapon.reload(p)) {
            b.reloads++;
            SoldierVoices.say(p, b, "reload");
        }
    }

    /** Enemies within 3 blocks of the shot line feel it (near misses keep heads down). */
    private static void suppress(MinecraftServer server, ServerPlayer p, Bot b, Vec3 aim) {
        if (aim == null) return;
        Vec3 eye = p.getEyePosition(), dir = p.getLookAngle();
        var hit = p.serverLevel().clip(new ClipContext(eye, eye.add(dir.scale(b.range)),
                ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, p));
        double reach = hit.getType() == HitResult.Type.MISS ? b.range : eye.distanceTo(hit.getLocation()) + 1;
        for (Bot o : BOTS.values()) {
            if (o.team.equals(b.team)) continue;
            ServerPlayer t = server.getPlayerList().getPlayerByName(o.name);
            if (t == null || !t.isAlive()) continue;
            Vec3 c = t.getEyePosition().subtract(eye);
            double along = c.dot(dir);
            if (along > 0 && along <= reach && c.subtract(dir.scale(along)).lengthSqr() < 9) {
                o.suppression = Math.min(1, o.suppression + (b.role.equals("mg") ? 0.09f : 0.06f));
                Threat th = o.threats.computeIfAbsent(b.name.toLowerCase(Locale.ROOT), k -> Threat.told());
                if (th.eye == null || th.visible == 0) {
                    th.eye = eye;
                    th.seen = p.serverLevel().getGameTime();
                }
            }
        }
    }

    /** A living teammate within 1.2 blocks of the line from the eye to {@code aim}, in front: hold fire. */
    private static boolean friendlyInLine(MinecraftServer server, ServerPlayer p, Bot b, Vec3 aim) {
        Vec3 eye = p.getEyePosition();
        Vec3 line = aim.subtract(eye);
        double len = line.length();
        if (len < 1e-3) return false;
        Vec3 dir = line.scale(1 / len);
        for (Bot other : BOTS.values()) {
            if (other == b || !other.team.equals(b.team)) continue;
            if (crosses(p, server.getPlayerList().getPlayerByName(other.name), eye, dir, len)) return true;
        }
        if (b.huntsPlayers) return false;
        for (ServerPlayer t : server.getPlayerList().getPlayers()) {          // real players are on the friendlies' side
            if (!scripted(t) && crosses(p, t, eye, dir, len)) return true;
        }
        return false;
    }

    private static boolean crosses(ServerPlayer p, ServerPlayer t, Vec3 eye, Vec3 dir, double len) {
        if (t == null || t == p || !t.isAlive() || t.level() != p.level()) return false;
        Vec3 c = t.position().add(0, t.getBbHeight() * 0.5, 0).subtract(eye);
        double along = c.dot(dir);
        return along > 0 && along < len + 1 && c.subtract(dir.scale(along)).lengthSqr() < 1.44;
    }

    /** Chest, else head, visible from the actual eased/leaned eye; hidden bodies need a real lookout first. */
    private static Vec3 seen(ServerPlayer p, ServerPlayer t) {
        long now = p.level().getGameTime();
        if (now != seenTick) {
            SEEN.clear();
            seenTick = now;
        }
        long key = ((long) p.getId() << 32) | (t.getId() & 0xffffffffL);
        Vec3 hit = SEEN.get(key);
        if (hit != null) return hit == NOT_SEEN ? null : hit;
        hit = NOT_SEEN;
        Vec3 eye = p.getEyePosition();
        for (double f : new double[]{0.7, 0.9}) {
            Vec3 to = t.position().add(0, t.getBbHeight() * f, 0);
            if (BotTactics.sees(p.serverLevel(), eye, to, p)) {
                hit = to;
                break;
            }
        }
        SEEN.put(key, hit);
        return hit == NOT_SEEN ? null : hit;
    }

    /** Who saw whom this tick (the same pair is asked about several times a tick): cleared on the next tick. */
    private static final java.util.Map<Long, Vec3> SEEN = new java.util.HashMap<>();
    private static final Vec3 NOT_SEEN = new Vec3(Double.NaN, 0, 0);
    private static long seenTick = Long.MIN_VALUE;

    // --- equipment ----------------------------------------------------------------------------------------------

    /** Main hand = the gun with its first listed magazine full and a round chambered; {@code spares} more full mags. */
    public static String arm(ServerPlayer p, ResourceLocation gunId, int spares) {
        return arm(p, gunId, spares, List.of());
    }

    static String arm(ServerPlayer p, ResourceLocation gunId, int spares, List<ResourceLocation> grenades) {
        return SoldierServices.weapon().arm(p, gunId, spares, grenades);
    }

    private static Method forward, strafing, sprinting, sneaking;

    /** The medical mod's give-up (what a downed player's held key sends); nothing when there is none. */
    private static void giveUp(ServerPlayer p) {
        SoldierServices.medical().giveUp(p);
    }

    /** Carpet fakes: action pack input (reflection: no compile dependency on Carpet). */
    private static void move(ServerPlayer p, float fwd, float side, boolean sprint, boolean sneak) {
        if (p instanceof SoldierPlayer soldier) {
            soldier.drive(fwd, side, sprint, sneak);
            return;
        }
        try {
            Object pack = p.getClass().getMethod("getActionPack").invoke(p);
            if (forward == null) {
                Class<?> k = pack.getClass();
                forward = k.getMethod("setForward", float.class);
                strafing = k.getMethod("setStrafing", float.class);
                sprinting = k.getMethod("setSprinting", boolean.class);
                sneaking = k.getMethod("setSneaking", boolean.class);
            }
            forward.invoke(pack, fwd);
            strafing.invoke(pack, side);
            sneaking.invoke(pack, sneak && !sprint);
            sprinting.invoke(pack, sprint);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("Carpet action pack unavailable", e);
        }
    }

    // --- geometry -----------------------------------------------------------------------------------------------

    private static Vec3 toward(Vec3 from, Vec3 to, double max) {
        Vec3 d = new Vec3(to.x - from.x, 0, to.z - from.z);
        double len = d.length();
        return len <= max ? to : from.add(d.scale(max / len));
    }

    private static List<Vec3> points(JsonElement e) {
        List<Vec3> out = new ArrayList<>();
        for (JsonElement p : e.getAsJsonArray()) out.add(point(p.getAsJsonArray()));
        return out;
    }

    private static Vec3 point(JsonArray a) {
        return new Vec3(a.get(0).getAsDouble(), a.get(1).getAsDouble(), a.get(2).getAsDouble());
    }

    private static Vec3 flat(Vec3 v) {
        return new Vec3(v.x, 0, v.z).normalize();
    }

    private static float yawTo(ServerPlayer p, Vec3 aim) {
        return yawOf(aim.subtract(p.getEyePosition()));
    }

    static float yawOf(Vec3 d) {
        return (float) Math.toDegrees(Math.atan2(-d.x, d.z));
    }
}
