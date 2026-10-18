package com.wf.wflib.aef.nuke.util;

import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.ints.IntList;

import java.util.concurrent.atomic.AtomicReference;

public final class MpscIntArrayListCollector {

    private final AtomicReference<Node> head = new AtomicReference<>();
    private volatile int drainCapacityHint;

    private static void append(Node head, IntArrayList out) {
        for (Node node = head; node != null; node = node.next) {
            if (node.values == null) {
                out.add(node.v);
            } else {
                for (int i = node.values.length - 1; i >= 0; i--) out.add(node.values[i]);
            }
        }
    }

    public void push(int i) {
        pushNode(new Node(i, null));
    }

    public void pushBatch(IntList values) {
        int size = values.size();
        if (size == 0) return;
        if (size == 1) {
            push(values.getInt(0));
            return;
        }
        pushNode(new Node(0, values.toIntArray()));
    }

    private void pushNode(Node node) {
        while (true) {
            Node h = head.get();
            node.next = h;
            if (head.compareAndSet(h, node)) return;
        }
    }

    public IntArrayList drain() {
        Node h = head.getAndSet(null);
        if (h == null) return new IntArrayList(0);
        IntArrayList out = new IntArrayList(Math.max(1, drainCapacityHint));
        append(h, out);
        drainCapacityHint = out.size();
        return out;
    }

    private static final class Node {
        private final int v;
        private final int[] values;
        private Node next;

        private Node(int v, int[] values) {
            this.v = v;
            this.values = values;
        }
    }
}
