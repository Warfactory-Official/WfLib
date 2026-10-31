package com.wf.wflib.pda.terminal;

import com.wf.wflib.pda.api.Pda;
import com.wf.wflib.pda.api.TerminalNoun;
import com.wf.wflib.pda.api.Uplink;
import com.wf.wflib.recon.api.GridNode;
import com.wf.wflib.recon.api.NamedNode;
import com.wf.wflib.recon.api.ReconGrid;
import com.wf.wflib.recon.api.ReconNet;
import com.wf.wflib.recon.api.ReconOwners;
import net.minecraft.server.level.ServerPlayer;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/** PDA Terminal app, server end: one line -> reply lines. Nouns from {@link TerminalNoun#NOUNS}. */
public final class PdaTerminal {
    /** One token: refs and console addressing split on whitespace. */
    static final Pattern NAME = Pattern.compile("[A-Za-z0-9_-]{1,16}");
    /** < {@link TerminalReplyPacket} list cap. */
    private static final int MAX_LINES = 200;

    private PdaTerminal() {
    }

    public static List<String> run(ServerPlayer player, String line) {
        if (!Pda.carries(player)) {
            return List.of("err: no PDA carried");
        }
        String[] w = line.trim().split("\\s+");
        String verb = w[0].toLowerCase(Locale.ROOT);
        switch (verb) {
            case "help", "" -> {
                return List.of(help());
            }
            case "fastfetch" -> {
                return w.length == 2 && w[1].equalsIgnoreCase("server") ? ServerFetch.lines(player)
                        : List.of("fastfetch shows your machine; 'fastfetch server' shows the host");
            }
            case "rename" -> {
                return List.of(w.length == 4 ? rename(player, w[1].toLowerCase(Locale.ROOT), w[2], w[3])
                        : "err: " + help());
            }
            default -> {
                for (TerminalNoun noun : TerminalNoun.NOUNS.values()) {
                    if (noun.plural().equals(verb)) {
                        List<? extends TerminalNoun.Entry> entries = noun.entries(player);
                        return entries == null ? List.of("err: no uplink") : list(player, entries, noun.none());
                    }
                }
                return List.of("err: unknown '" + w[0] + "' (help)");
            }
        }
    }

    private static String help() {
        List<TerminalNoun> nouns = TerminalNoun.NOUNS.values();
        return nouns.stream().map(TerminalNoun::plural).collect(Collectors.joining(" | "))
                + " | rename <" + nouns.stream().map(TerminalNoun::singular).collect(Collectors.joining("|"))
                + "> <#n|name> <new|-> | fastfetch [server] | cmatrix | clear";
    }

    private static String rename(ServerPlayer player, String kind, String ref, String name) {
        boolean reset = name.equals("-");
        if (!reset && !NAME.matcher(name).matches()) {
            return "err: name = 1-16 of A-Z 0-9 _ -";
        }
        for (TerminalNoun noun : TerminalNoun.NOUNS.values()) {
            if (noun.singular().equals(kind)) {
                List<? extends TerminalNoun.Entry> entries = noun.entries(player);
                if (entries == null) {
                    return "err: no uplink";
                }
                TerminalNoun.Entry entry = pick(entries, ref);
                return entry == null ? "err: no " + kind + " '" + ref + "'"
                        : entry.rename(player, reset ? null : name);
            }
        }
        return "err: rename " + TerminalNoun.NOUNS.values().stream().map(TerminalNoun::singular)
                .collect(Collectors.joining("|"));
    }

    private static List<String> list(ServerPlayer player, List<? extends TerminalNoun.Entry> items, String none) {
        if (items.isEmpty()) {
            return List.of(none);
        }
        int shown = Math.min(items.size(), MAX_LINES);
        List<String> out = new ArrayList<>(shown + 1);
        for (int i = 0; i < shown; i++) {
            out.add("#" + (i + 1) + " " + items.get(i).line(player));
        }
        if (shown < items.size()) {
            out.add("... " + (items.size() - shown) + " more");
        }
        return out;
    }

    /** {@code #n} / {@code n} = 1-based listing index; else name, case-insensitive. */
    @Nullable
    private static TerminalNoun.Entry pick(List<? extends TerminalNoun.Entry> items, String ref) {
        String digits = ref.startsWith("#") ? ref.substring(1) : ref;
        if (!digits.isEmpty() && digits.length() <= 3 && digits.chars().allMatch(Character::isDigit)) {
            int index = Integer.parseInt(digits) - 1;
            return index >= 0 && index < items.size() ? items.get(index) : null;
        }
        for (TerminalNoun.Entry item : items) {
            if (item.name().equalsIgnoreCase(ref)) {
                return item;
            }
        }
        return null;
    }

    /** Own net, this level, by node id. */
    public static final TerminalNoun NODES = new TerminalNoun() {
        @Override
        public String plural() {
            return "nodes";
        }

        @Override
        public String singular() {
            return "node";
        }

        @Override
        public String none() {
            return "no nodes on this net";
        }

        @Override
        @Nullable
        public List<Entry> entries(ServerPlayer player) {
            long net = ReconOwners.netOf(player);
            if (!Uplink.allows(player, net)) {
                return null;
            }
            ReconGrid grid = ReconNet.grid(player.serverLevel(), net);
            List<GridNode> nodes = grid == null ? new ArrayList<>() : new ArrayList<>(grid.nodes());
            nodes.sort(Comparator.comparingLong(GridNode::id));
            List<Entry> out = new ArrayList<>(nodes.size());
            for (GridNode n : nodes) {
                out.add(new Entry() {
                    @Override
                    public String name() {
                        return n.label();
                    }

                    @Override
                    public String line(ServerPlayer viewer) {
                        return String.format(Locale.ROOT, "%s  %s  %d %d %d", n.label(),
                                n.online() ? "ONLINE " + n.hops() + " hop" : "OFFLINE",
                                n.pos().getX(), n.pos().getY(), n.pos().getZ());
                    }

                    @Override
                    public String rename(ServerPlayer p, @Nullable String name) {
                        if (!(p.serverLevel().getBlockEntity(n.pos()) instanceof NamedNode named)) {
                            return "err: no renamable node '" + n.label() + "'";
                        }
                        named.rename(name == null ? "" : name);
                        return "node at " + n.pos().toShortString() + (name == null ? " label reset" : " renamed " + name);
                    }
                });
            }
            return out;
        }
    };
}
