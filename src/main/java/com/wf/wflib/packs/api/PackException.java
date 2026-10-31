package com.wf.wflib.packs.api;

import java.util.List;

/** Load failure; one line per problem, each {@code <pack>: <file>: <field>: <message>}. */
public final class PackException extends RuntimeException {
    private static final int SHOWN = 50;

    private final List<String> problems;

    public PackException(String label, List<String> problems) {
        super(message(label, problems));
        this.problems = List.copyOf(problems);
    }

    public List<String> problems() {
        return problems;
    }

    private static String message(String label, List<String> problems) {
        StringBuilder b = new StringBuilder(label).append(": ").append(problems.size()).append(" error(s)");
        problems.stream().limit(SHOWN).forEach(p -> b.append("\n  ").append(p));
        if (problems.size() > SHOWN) {
            b.append("\n  ... ").append(problems.size() - SHOWN).append(" more");
        }
        return b.toString();
    }
}
