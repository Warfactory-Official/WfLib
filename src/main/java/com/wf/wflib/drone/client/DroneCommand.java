package com.wf.wflib.drone.client;

import com.wf.wflib.drone.api.DroneTask;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Typed Drones-app order: {@code <verb> [at|to] [target] [r<radius>]}, {@code alt <n|+n|-n>}, {@code stop|rtb|land}.
 * Target = {@code x z} / {@code x y z} (each may be {@code ~} or {@code ~n}, relative to you), {@code here},
 * {@code look}, a marker label or {@code #n}; none = the picked TARGET. Orders chain with {@code then} or
 * {@code ,} into one program. Verbs: {@link com.wf.wflib.drone.api.PdaDrones#dispatch}.
 */
final class DroneCommand {
    static final List<String> HELP = List.of(
            "moveto|loiter|strike|deliver|minelay|collect|hold [at] <place> [r<radius>]",
            "place: x z | x y z | ~dx ~dz | here | look | marker name | #n (none = TARGET)",
            "chain: moveto 100 200 then moveto 300 200 then hold 300 400",
            "queue: add <order> | send | undo | cancel | queue",
            "stop | rtb | land | alt <n|+n|-n> | target <place> | select <#n|name> | connect | clear");
    private static final Pattern CHAIN = Pattern.compile("\\s*(?:,|;|\\bthen\\b)\\s*", Pattern.CASE_INSENSITIVE);
    private static final Pattern COORD = Pattern.compile("~|~?-?\\d+(\\.\\d+)?");
    private static final Pattern ALT = Pattern.compile("[+-]?\\d+(\\.\\d+)?");
    private static final Pattern RADIUS = Pattern.compile("r\\d+(\\.\\d+)?");
    /** Kinds a single typed order may name; exfil is {@code rtb}. */
    private static final List<String> ORDERS = Arrays.stream(DroneTask.Kind.values())
            .filter(k -> k != DroneTask.Kind.EXFIL).map(DroneTask.Kind::id).toList();

    /** Where names resolve. */
    interface Places {
        Vec3 self();

        /** Block under the crosshair; null = nothing in reach. */
        @Nullable
        Vec3 look();

        /** Side marker by label or {@code #n}; null = none. */
        @Nullable
        Vec3 marker(String name);

        /** The TARGET row's current pick. */
        Vec3 picked();
    }

    /** {@code verb} = a dispatch verb or {@code target} (client side only). */
    record Order(String verb, Vec3 at, double arg) {
    }

    private DroneCommand() {
    }

    /** {@code then}/{@code ,}-separated orders, in order. @throws IllegalArgumentException with the reply text */
    static List<Order> parseAll(String line, Places places) {
        List<Order> out = new ArrayList<>();
        for (String part : CHAIN.split(line)) {
            if (!part.isBlank()) {
                out.add(parse(part, places));
            }
        }
        if (out.isEmpty()) {
            throw new IllegalArgumentException("empty order");
        }
        return out;
    }

    /** One program step; stop / land / alt only act now. @throws IllegalArgumentException when it can't queue */
    static DroneTask task(Order order) {
        return switch (order.verb()) {
            case "hold" -> new DroneTask.Loiter(order.at(), 0, 0);
            case "loiter" -> new DroneTask.Loiter(order.at(), order.arg() > 0 ? order.arg()
                    : DroneTask.Loiter.DEFAULT_RADIUS, 0);
            case "rtb" -> new DroneTask.Exfil();
            default -> {
                if (!ORDERS.contains(order.verb())) {
                    throw new IllegalArgumentException(order.verb() + " can't be queued or chained");
                }
                yield DroneTask.Kind.byId(order.verb()).at(order.at());
            }
        };
    }

    /** @throws IllegalArgumentException with the reply text */
    static Order parse(String line, Places places) {
        List<String> w = new ArrayList<>(List.of(line.trim().toLowerCase(Locale.ROOT).split("\\s+")));
        String first = w.removeFirst();
        String verb = switch (first) {
            case "goto", "go", "move" -> "moveto";
            case "orbit" -> "loiter";
            case "home", "return", "exfil" -> "rtb";
            case "hover" -> "hold";
            default -> first;
        };
        while (!w.isEmpty() && (w.getFirst().equals("at") || w.getFirst().equals("to") || w.getFirst().equals("over"))) {
            w.removeFirst();
        }
        switch (verb) {
            case "stop", "rtb", "land" -> {
                if (!w.isEmpty()) {
                    throw new IllegalArgumentException(verb + " takes no target");
                }
                return new Order(verb, places.self(), 0);
            }
            case "alt" -> {
                if (w.size() != 1 || !ALT.matcher(w.getFirst()).matches()) {
                    throw new IllegalArgumentException("alt <blocks> | alt +n | alt -n");
                }
                String n = w.getFirst();
                boolean relative = n.startsWith("+") || n.startsWith("-");
                return new Order(relative ? "climb" : "alt", places.self(), Double.parseDouble(n));
            }
            case "hold" -> {
                if (w.isEmpty()) {
                    return new Order("stop", places.self(), 0);
                }
            }
            case "target" -> {
                if (w.isEmpty()) {
                    throw new IllegalArgumentException("target <place>");
                }
            }
            default -> {
                if (!ORDERS.contains(verb)) {
                    throw new IllegalArgumentException("unknown '" + verb + "' (help)");
                }
            }
        }
        double radius = 0;
        if (!w.isEmpty() && RADIUS.matcher(w.getLast()).matches()) {
            radius = Double.parseDouble(w.removeLast().substring(1));
        }
        return new Order(verb, w.isEmpty() ? places.picked() : place(w, places), radius);
    }

    private static Vec3 place(List<String> w, Places places) {
        Vec3 self = places.self();
        if (w.size() == 1 && (w.getFirst().equals("here") || w.getFirst().equals("me"))) {
            return self;
        }
        if (w.size() == 1 && w.getFirst().equals("look")) {
            Vec3 look = places.look();
            if (look == null) {
                throw new IllegalArgumentException("nothing under the crosshair");
            }
            return look;
        }
        if ((w.size() == 2 || w.size() == 3) && w.stream().allMatch(t -> COORD.matcher(t).matches())) {
            double x = coord(w.getFirst(), self.x);
            double y = w.size() == 3 ? coord(w.get(1), self.y) : self.y;
            double z = coord(w.getLast(), self.z);
            return new Vec3(x, y, z);
        }
        String name = String.join(" ", w);
        Vec3 marker = places.marker(name);
        if (marker == null) {
            throw new IllegalArgumentException("no marker '" + name + "'");
        }
        return marker;
    }

    /** Whole numbers = block, centred like a marker. */
    private static double coord(String token, double base) {
        if (token.startsWith("~")) {
            return base + (token.length() == 1 ? 0 : Double.parseDouble(token.substring(1)));
        }
        return token.contains(".") ? Double.parseDouble(token) : Integer.parseInt(token) + 0.5;
    }
}
