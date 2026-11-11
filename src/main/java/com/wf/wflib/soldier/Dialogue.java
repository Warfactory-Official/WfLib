package com.wf.wflib.soldier;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.wf.wflib.soldier.api.Cost;
import com.wf.wflib.soldier.api.Recruiting;
import com.wf.wflib.soldier.api.SoldierOrder;
import com.wf.wflib.soldier.api.SoldierRole;
import com.wf.wflib.soldier.api.SoldierServices;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;

/**
 * Talking to a soldier (use it with an empty hand). The server runs the conversation and the client only shows it: each node is a line
 * from the soldier and the options to answer with, and an option is checked here, against what was sent, before anything happens.
 *
 * <p>Who answers depends on who they are to you. A soldier of yours can be given quick orders, asked how it is, traded with (items go
 * both ways) or let go. A soldier nobody owns can be asked to join (the same price as the old sneak-use gesture). A downed enemy can
 * be talked into switching sides for money or valuables: it may refuse, and then will not listen again for a while.
 */
final class Dialogue {
    /** How far the player may stray from the soldier before the conversation ends, and the dialogue's check interval (ticks). */
    static final double REACH = 8;
    private static final int EVERY = 10;
    /** A refused persuasion is not tried again for this many game ticks. */
    static final long REFUSED_TICKS = 2400;
    static final int INVENTORY = 41;

    enum Mood { PARTY, NEUTRAL, DOWNED }

    /** {@code kind}: 0 a plain question, 1 a deed (highlighted), 2 leave. */
    record Option(String id, String tag, String text, int kind, boolean enabled, String hint) {
        static Option say(String id, String text) { return new Option(id, "", text, 0, true, ""); }

        static Option deed(String id, String tag, String text) { return new Option(id, tag, text, 1, true, ""); }

        Option priced(boolean affordable, String price) { return new Option(id, tag, text, kind, affordable, price); }

        static Option leave() { return new Option("leave", "Leave", "That's all.", 2, true, ""); }
    }

    private static final class Convo {
        final UUID soldier;
        final Mood mood;
        List<Option> options = List.of();
        boolean trading;

        Convo(UUID soldier, Mood mood) {
            this.soldier = soldier;
            this.mood = mood;
        }
    }

    private static final Map<UUID, Convo> OPEN = new HashMap<>();
    private static final Map<UUID, Long> REFUSED = new HashMap<>();

    private Dialogue() {}

    static void forget() {
        OPEN.clear();
        REFUSED.clear();
    }

    /** Who this soldier is to the player; null when there is nothing to say (an enemy on its feet, or someone else's soldier). */
    static Mood mood(ServerPlayer player, SoldierAi.Bot b) {
        if (b.companion != null) return player.getUUID().equals(b.ownerId) ? Mood.PARTY : null;
        if (!b.huntsPlayers) return Mood.NEUTRAL;
        return b.health.downed() ? Mood.DOWNED : null;
    }

    /** Opens the conversation on the player's client; false when it cannot be (nothing to say, or no dialogue channel there). */
    static boolean open(ServerPlayer player, ServerPlayer soldier, SoldierAi.Bot b) {
        Mood mood = mood(player, b);
        if (mood == null || !player.connection.hasChannel(DialoguePackets.Node.TYPE)) return false;
        Convo c = new Convo(soldier.getUUID(), mood);
        OPEN.put(player.getUUID(), c);
        SoldierVoices.say(soldier, b, mood == Mood.PARTY ? "talk" : "greet");
        show(player, soldier, b, c, "root", "");
        return true;
    }

    /** A line and its options, sent to the player; a {@code line} of "" is the node's own opening. */
    private static void show(ServerPlayer player, ServerPlayer soldier, SoldierAi.Bot b, Convo c, String node, String line) {
        c.trading = false;
        c.options = options(player, soldier, b, c.mood, node);
        var j = new JsonObject();
        j.addProperty("entity", soldier.getId());
        j.addProperty("name", soldier.hasCustomName() ? soldier.getCustomName().getString() : b.name);
        j.addProperty("sub", sub(b, c.mood));
        j.addProperty("line", line.isEmpty() ? opening(soldier, c.mood, node) : line);
        var arr = new JsonArray();
        for (Option o : c.options) {
            var x = new JsonObject();
            x.addProperty("tag", o.tag);
            x.addProperty("text", o.text);
            x.addProperty("kind", o.kind);
            x.addProperty("on", o.enabled);
            x.addProperty("hint", o.hint);
            arr.add(x);
        }
        j.add("options", arr);
        PacketDistributor.sendToPlayer(player, new DialoguePackets.Node(j.toString()));
    }

    private static String sub(SoldierAi.Bot b, Mood mood) {
        String role = SoldierRole.tactics(b.role).title;
        return switch (mood) {
            case PARTY -> role + " - " + Companion.TEAMS[b.fireTeam];
            case NEUTRAL -> role;
            case DOWNED -> role + " - wounded";
        };
    }

    private static String pick(ServerPlayer soldier, String salt, String... lines) {
        return lines[new Random(soldier.getUUID().getLeastSignificantBits() ^ salt.hashCode()).nextInt(lines.length)];
    }

    private static String opening(ServerPlayer soldier, Mood mood, String node) {
        return switch (node) {
            case "dismiss" -> "You mean it? I walk away and you never see me again?";
            case "persuade" -> "...";
            default -> switch (mood) {
                case PARTY -> pick(soldier, "party", "Yeah? I'm listening.", "Orders?", "Talk to me.");
                case NEUTRAL -> pick(soldier, "neutral", "Easy with that thing. What do you want?", "Another one on two legs. Go on, talk.", "Keep it pointed at the floor and we'll get along.");
                case DOWNED -> pick(soldier, "downed", "(coughing) ...Don't. I'm done. Just... don't.", "Make it quick. Or talk, if that is what you came for.", "Get it over with. Or say what you want.");
            };
        };
    }

    private static List<Option> options(ServerPlayer player, ServerPlayer soldier, SoldierAi.Bot b, Mood mood, String node) {
        List<Option> out = new ArrayList<>();
        if (node.equals("dismiss")) {
            out.add(Option.deed("dismiss_yes", "Dismiss", "Yes. You're free to go."));
            out.add(Option.say("back", "Never mind."));
            return out;
        }
        switch (mood) {
            case PARTY -> {
                out.add(Option.deed("follow", "Order", "Stay on me."));
                out.add(Option.deed("hold", "Order", "Hold this position."));
                out.add(Option.deed("trade", "Trade", "Let's go through our gear."));
                out.add(Option.say("lamp", b.lamp ? "Take off your headlamp." : "Put your headlamp on."));
                out.add(Option.say("report", "How are you holding up?"));
                out.add(Option.say("about", "Remind me who you are."));
                out.add(Option.say("dismiss", "I can't use you any more."));
            }
            case NEUTRAL -> {
                String source = b.garrison.isEmpty() ? "rescue" : "garrison";
                if (Recruiting.enabled(source)) {
                    Cost price = recruitPrice(b);
                    out.add(Option.deed("recruit", "Recruit", "Fight with me.").priced(price.affordable(player), price == Cost.FREE ? "" : price.describe()));
                }
                out.add(Option.say("doing", "What are you doing out here?"));
                out.add(Option.say("about", "Who are you?"));
            }
            case DOWNED -> {
                if (!refused(player, soldier)) {
                    Cost items = RecruitConfig.persuadeItems();
                    if (items != Cost.FREE) out.add(Option.deed("persuade_items", "Persuade", "Take these. Walk with me instead.").priced(items.affordable(player), items.describe()));
                    Cost money = persuadeMoney(b);
                    if (money != Cost.FREE) out.add(Option.deed("persuade_money", "Persuade", "Switch sides. I'll pay you well.").priced(money.affordable(player), money.describe()));
                }
                out.add(Option.say("spare", "Stay down. It's over."));
            }
        }
        out.add(Option.leave());
        return out;
    }

    /** What recruiting this unowned soldier costs: nothing for a rescue, the garrison price for a garrison's. */
    static Cost recruitPrice(SoldierAi.Bot b) {
        if (b.garrison.isEmpty()) return Cost.FREE;
        var pricing = Recruiting.pricing();
        return Cost.all(List.of(RecruitConfig.garrisonCost(), pricing == null ? Cost.FREE : Cost.currency(pricing.garrison(SoldierRole.tactics(b.role)))));
    }

    /** The money for a persuasion: the config's amount, else twice the economy's garrison price; free (no offer) without either. */
    static Cost persuadeMoney(SoldierAi.Bot b) {
        int fixed = RecruitConfig.persuadeCurrency();
        if (fixed > 0) return Cost.currency(fixed);
        var pricing = Recruiting.pricing();
        return pricing == null ? Cost.FREE : Cost.currency(2 * pricing.garrison(SoldierRole.tactics(b.role)));
    }

    private static boolean refused(ServerPlayer player, ServerPlayer soldier) {
        Long until = REFUSED.get(soldier.getUUID());
        return until != null && soldier.level().getGameTime() < until;
    }

    /** Whether a persuasion works, given the dice ({@code roll} 0-1). */
    static boolean persuades(int percent, double roll) {
        return roll * 100 < percent;
    }

    static void pick(ServerPlayer player, int index) {
        Convo c = OPEN.get(player.getUUID());
        if (c == null) return;
        ServerPlayer soldier = player.getServer().getPlayerList().getPlayer(c.soldier);
        SoldierAi.Bot b = soldier == null ? null : SoldierAi.botOf(soldier.getGameProfile().getName());
        if (index < 0 || index >= c.options.size() || soldier == null || b == null || soldier.distanceTo(player) > REACH + 4) {
            close(player, true);
            return;
        }
        Option o = c.options.get(index);
        if (!o.enabled) {
            show(player, soldier, b, c, "root", o.hint.isEmpty() ? "You can't." : "That costs " + o.hint + ".");
            return;
        }
        try {
            switch (o.id) {
                case "leave" -> close(player, true);
                case "back" -> show(player, soldier, b, c, "root", "");
                case "follow" -> order(player, soldier, b, c, SoldierOrder.of(SoldierOrder.Kind.FOLLOW), "On you.");
                case "hold" -> order(player, soldier, b, c, SoldierOrder.at(SoldierOrder.Kind.HOLD, soldier.position()), "I'll hold here.");
                case "lamp" -> {
                    Squads.lamp(player, List.of(b), !b.lamp);
                    show(player, soldier, b, c, "root", b.lamp ? "Lamp's on." : "Lamp's off.");
                }
                case "trade" -> {
                    c.trading = true;
                    sendTrade(player, soldier);
                }
                case "report" -> show(player, soldier, b, c, "root", report(soldier, b));
                case "about" -> show(player, soldier, b, c, "root", about(soldier, b));
                case "doing" -> show(player, soldier, b, c, "root", doing(b));
                case "dismiss" -> show(player, soldier, b, c, "dismiss", "");
                case "dismiss_yes" -> {
                    Squads.dismiss(player, soldier.getGameProfile().getName());
                    SoldierVoices.say(soldier, b, "ack_hold");
                    player.displayClientMessage(Component.literal(b.name + " was let go"), true);
                    close(player, true);
                }
                case "recruit" -> {
                    Cost price = recruitPrice(b);
                    if (!price.affordable(player)) throw new IllegalArgumentException("Hiring " + b.name + " costs " + price.describe());
                    Companion joined = Squads.recruit(player, soldier);
                    price.charge(player);
                    b.garrison = "";                                                   // the territory raises a replacement
                    SoldierVoices.say(soldier, b, "greet");
                    player.displayClientMessage(Component.literal("Recruited " + b.name + " to " + Companion.TEAMS[joined.fireTeam]), true);
                    Convo party = new Convo(c.soldier, Mood.PARTY);
                    OPEN.put(player.getUUID(), party);
                    show(player, soldier, b, party, "root", "Lead the way.");
                }
                case "persuade_items", "persuade_money" -> persuade(player, soldier, b, c, o.id.equals("persuade_items") ? RecruitConfig.persuadeItems() : persuadeMoney(b));
                case "spare" -> {
                    player.displayClientMessage(Component.literal(b.name + " stays down"), true);
                    close(player, true);
                }
                default -> close(player, true);
            }
        } catch (IllegalArgumentException ex) {
            show(player, soldier, b, c, "root", ex.getMessage());
        }
    }

    private static void order(ServerPlayer player, ServerPlayer soldier, SoldierAi.Bot b, Convo c, SoldierOrder order, String answer) {
        Squads.order(player, List.of(b), order);
        SoldierVoices.say(soldier, b, order.kind().ack);
        show(player, soldier, b, c, "root", answer);
    }

    private static void persuade(ServerPlayer player, ServerPlayer soldier, SoldierAi.Bot b, Convo c, Cost price) {
        if (price == Cost.FREE || !price.affordable(player)) throw new IllegalArgumentException("You can't pay that");
        if (!persuades(RecruitConfig.persuadeChance(), player.getRandom().nextDouble())) {
            REFUSED.put(soldier.getUUID(), soldier.level().getGameTime() + REFUSED_TICKS);
            show(player, soldier, b, c, "root", "(spits blood) Keep your money. I'd sooner die with my own.");
            return;
        }
        if (!price.charge(player)) throw new IllegalArgumentException("You can't pay that");
        Companion joined = Squads.turn(player, soldier);
        SoldierVoices.say(soldier, b, "greet");
        player.displayClientMessage(Component.literal(b.name + " switched sides and joined " + Companion.TEAMS[joined.fireTeam]), true);
        Convo party = new Convo(c.soldier, Mood.PARTY);
        OPEN.put(player.getUUID(), party);
        show(player, soldier, b, party, "root", "All right. You paid, I follow. Patch me up first.");
    }

    private static String report(ServerPlayer soldier, SoldierAi.Bot b) {
        int health = Math.round(soldier.getHealth() / soldier.getMaxHealth() * 100);
        String ammo = "";
        if (SoldierServices.hasWeapon() && SoldierServices.weapon().ammo(soldier) != null) {
            var a = SoldierServices.weapon().ammo(soldier);
            ammo = " " + a.left() + " in the gun and " + a.spare() + " spare " + (a.spare() == 1 ? "magazine" : "magazines") + ".";
        }
        return (health >= 80 ? "Fine." : health >= 40 ? "I've had better days." : "Bad. I need a medic.") + " Health " + health + "%." + ammo
                + " I'm on '" + b.order.label() + "'.";
    }

    private static String about(ServerPlayer soldier, SoldierAi.Bot b) {
        String gun = b.gun == null ? "nothing" : BuiltInRegistries.ITEM.containsKey(b.gun) ? BuiltInRegistries.ITEM.get(b.gun).getDescription().getString() : b.gun.getPath();
        return "Call me " + (soldier.hasCustomName() ? soldier.getCustomName().getString() : b.name) + ". " + SoldierRole.tactics(b.role).title.toLowerCase(java.util.Locale.ROOT)
                + ", carrying " + gun + ". " + SoldierRole.tactics(b.role).description;
    }

    private static String doing(SoldierAi.Bot b) {
        if (!b.garrison.isEmpty()) return "Posted here. Somebody has to watch this ground, and it might as well be me.";
        return switch (b.order.kind()) {
            case PATROL -> "Walking the route. Same as yesterday.";
            case GUARD, HOLD -> "Holding this spot. Nobody told me to leave it.";
            default -> "Waiting for someone sensible to come along. You'll do.";
        };
    }

    // ---- trade: items between the player and a soldier of theirs

    private static void sendTrade(ServerPlayer player, ServerPlayer soldier) {
        List<ItemStack> items = new ArrayList<>(INVENTORY);
        for (int i = 0; i < INVENTORY; i++) items.add(soldier.getInventory().getItem(i).copy());
        PacketDistributor.sendToPlayer(player, new DialoguePackets.Trade(soldier.getId(), items));
    }

    /** Moves the stack in {@code slot} to the other side: what does not fit stays where it was. */
    static void move(ServerPlayer player, boolean fromSoldier, int slot) {
        Convo c = OPEN.get(player.getUUID());
        if (c == null || !c.trading || c.mood != Mood.PARTY) return;
        ServerPlayer soldier = player.getServer().getPlayerList().getPlayer(c.soldier);
        SoldierAi.Bot b = soldier == null ? null : SoldierAi.botOf(soldier.getGameProfile().getName());
        if (b == null || !player.getUUID().equals(b.ownerId) || soldier.distanceTo(player) > REACH + 4) {
            close(player, true);
            return;
        }
        var from = fromSoldier ? soldier.getInventory() : player.getInventory();
        var to = fromSoldier ? player.getInventory() : soldier.getInventory();
        if (slot < 0 || slot >= (fromSoldier ? INVENTORY : 36)) return;
        ItemStack stack = from.getItem(slot);
        if (stack.isEmpty()) return;
        ItemStack moving = stack.copy();
        to.add(moving);
        from.setItem(slot, moving.isEmpty() ? ItemStack.EMPTY : moving);
        soldier.getInventory().setChanged();
        sendTrade(player, soldier);
    }

    // ---- ending

    static void close(ServerPlayer player, boolean tell) {
        if (OPEN.remove(player.getUUID()) != null && tell) PacketDistributor.sendToPlayer(player, new DialoguePackets.Close());
    }

    /** Ends conversations whose soldier is gone, or whose player has walked away. */
    static void onServerTick(ServerTickEvent.Post e) {
        MinecraftServer server = e.getServer();
        if (OPEN.isEmpty() || server.getTickCount() % EVERY != 0) return;
        for (var entry : List.copyOf(OPEN.entrySet())) {
            ServerPlayer player = server.getPlayerList().getPlayer(entry.getKey());
            ServerPlayer soldier = server.getPlayerList().getPlayer(entry.getValue().soldier);
            if (player == null) OPEN.remove(entry.getKey());
            else if (!player.isAlive() || soldier == null || !soldier.isAlive() || soldier.level() != player.level() || soldier.distanceTo(player) > REACH + 4) close(player, true);
        }
    }
}
