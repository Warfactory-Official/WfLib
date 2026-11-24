package com.wf.wflib.radiation;

import java.util.concurrent.ConcurrentLinkedQueue;

/** Region-worker writes; FIFO, drained on the world thread with the sweep joined. */
final class RadiationWriteQueue<W> {

    interface Sink<W> {
        void add(W world, long pos, double amount);

        void emit(W world, long pos, double emission, double saturation);

        void set(W world, long pos, double amount);

        void mark(W world, long sectionKey);

        void dirty(W world, long chunkKey, int slot);
    }

    private static final byte ADD = 0, EMIT = 1, SET = 2, MARK = 3, DIRTY = 4;

    private record Op<W>(byte kind, W world, long key, double a, double b) {
    }

    private final ConcurrentLinkedQueue<Op<W>> ops = new ConcurrentLinkedQueue<>();

    void add(W world, long pos, double amount) {
        ops.add(new Op<>(ADD, world, pos, amount, 0.0D));
    }

    void emit(W world, long pos, double emission, double saturation) {
        ops.add(new Op<>(EMIT, world, pos, emission, saturation));
    }

    void set(W world, long pos, double amount) {
        ops.add(new Op<>(SET, world, pos, amount, 0.0D));
    }

    void mark(W world, long sectionKey) {
        ops.add(new Op<>(MARK, world, sectionKey, 0.0D, 0.0D));
    }

    void dirty(W world, long chunkKey, int slot) {
        ops.add(new Op<>(DIRTY, world, chunkKey, slot, 0.0D));
    }

    boolean isEmpty() {
        return ops.isEmpty();
    }

    void clear() {
        ops.clear();
    }

    /** At most {@code limit} ops; a throwing op does not stop the rest, first failure rethrown after. */
    int drain(Sink<W> sink, int limit) {
        int n = 0;
        RuntimeException failure = null;
        Op<W> op;
        while (n < limit && (op = ops.poll()) != null) {
            n++;
            try {
                switch (op.kind) {
                    case ADD -> sink.add(op.world, op.key, op.a);
                    case EMIT -> sink.emit(op.world, op.key, op.a, op.b);
                    case SET -> sink.set(op.world, op.key, op.a);
                    case MARK -> sink.mark(op.world, op.key);
                    default -> sink.dirty(op.world, op.key, (int) op.a);
                }
            } catch (RuntimeException ex) {
                if (failure == null) failure = ex;
                else failure.addSuppressed(ex);
            }
        }
        if (failure != null) throw failure;
        return n;
    }
}
