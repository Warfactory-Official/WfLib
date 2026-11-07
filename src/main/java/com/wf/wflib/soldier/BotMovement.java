package com.wf.wflib.soldier;

final class BotMovement {
    private BotMovement() {}

    static boolean yields(String name, int priority, String other, int otherPriority) {
        return priority > otherPriority || priority == otherPriority && name.compareToIgnoreCase(other) > 0;
    }
}
