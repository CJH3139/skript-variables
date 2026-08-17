package com.skriptvariables.profiler;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.PriorityQueue;

/**
 * Keeps the slowest N executions seen. Backed by a min-heap so the cheapest
 * retained spike is always the one evicted.
 */
public final class SpikeBuffer {

    private final int capacity;
    private final PriorityQueue<Spike> heap;

    public SpikeBuffer(int capacity) {
        this.capacity = capacity;
        this.heap = new PriorityQueue<>(Math.max(1, capacity), Comparator.comparingLong(Spike::ns));
    }

    public synchronized void offer(int triggerId, long at, long ns) {
        if (heap.size() < capacity) {
            heap.add(new Spike(triggerId, at, ns));
            return;
        }
        Spike weakest = heap.peek();
        if (weakest != null && ns > weakest.ns()) {
            heap.poll();
            heap.add(new Spike(triggerId, at, ns));
        }
    }

    public synchronized List<Spike> drainSorted() {
        List<Spike> out = new ArrayList<>(heap);
        out.sort(Comparator.comparingLong(Spike::ns).reversed());
        return out;
    }
}
