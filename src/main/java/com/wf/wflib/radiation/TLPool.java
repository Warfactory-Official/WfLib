package com.wf.wflib.radiation;

import java.util.Objects;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import java.util.function.Supplier;

/** Thread-local stack over a bounded shared tier. Divergence: shared tier = CLQ, not NTM's MPMC array queue. */
public final class TLPool<T> {
    private final ThreadLocal<LocalCache<T>> local;
    private final ConcurrentLinkedQueue<T> shared = new ConcurrentLinkedQueue<>();
    private final AtomicInteger sharedSize = new AtomicInteger();
    private final int sharedCap;

    private final Supplier<? extends T> factory;
    private final Consumer<? super T> reset;
    private final int localCap;

    public TLPool(Supplier<? extends T> factory, Consumer<? super T> reset, int localCap, int sharedCap) {
        this.factory = Objects.requireNonNull(factory);
        this.reset = Objects.requireNonNull(reset);
        if (localCap < 0) throw new IllegalArgumentException("localCap must be >= 0");
        if (sharedCap < 2) throw new IllegalArgumentException("sharedCap must be >= 2");
        this.localCap = localCap;
        this.sharedCap = sharedCap;
        this.local = ThreadLocal.withInitial(() -> new LocalCache<>(this.localCap));
    }

    public T borrow() {
        if (localCap == 0 || Thread.currentThread().isVirtual()) {
            T sharedValue = pollShared();
            return sharedValue != null ? sharedValue : newInstance();
        }
        LocalCache<T> q = local.get();
        T t = q.poll();
        if (t != null) return t;
        t = pollShared();
        return t != null ? t : newInstance();
    }

    public void recycle(T t) {
        if (t == null) throw new NullPointerException();
        reset.accept(t);
        if (localCap != 0 && !Thread.currentThread().isVirtual() && local.get().add(t)) return;
        if (sharedSize.incrementAndGet() > sharedCap) {
            sharedSize.decrementAndGet();
            return;
        }
        shared.offer(t);
    }

    private T pollShared() {
        T t = shared.poll();
        if (t != null) sharedSize.decrementAndGet();
        return t;
    }

    private T newInstance() {
        T value = factory.get();
        if (value == null) throw new NullPointerException();
        return value;
    }

    private static final class LocalCache<T> {
        private final Object[] elements;
        private int size;

        private LocalCache(int capacity) {
            elements = new Object[capacity];
        }

        boolean add(T value) {
            if (size == elements.length) return false;
            elements[size++] = value;
            return true;
        }

        @SuppressWarnings("unchecked")
        T poll() {
            if (size == 0) return null;
            T value = (T) elements[--size];
            elements[size] = null;
            return value;
        }
    }
}
