package com.skriptvariables.util;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.LongSupplier;

public final class DeletedVariableBuffer {

    public record Entry(int id, String name, Map<String, Object> values, int batch,
                        long deletedAt, String deletedBy, String sessionId) {}

    private final int maxEntries;
    private final int maxValues;
    private final long maxAgeMillis;
    private final LongSupplier clock;
    private final LinkedHashMap<Integer, Entry> entries = new LinkedHashMap<>();
    private int nextId = 1;
    private int nextBatch = 1;
    private int totalValues;

    public DeletedVariableBuffer(int maxEntries, long maxAgeMillis, LongSupplier clock) {
        this(maxEntries, Integer.MAX_VALUE, maxAgeMillis, clock);
    }

    public DeletedVariableBuffer(int maxEntries, int maxValues, long maxAgeMillis, LongSupplier clock) {
        this.maxEntries = maxEntries;
        this.maxValues = maxValues;
        this.maxAgeMillis = maxAgeMillis;
        this.clock = clock;
    }

    public synchronized int newBatch() {
        return nextBatch++;
    }

    public synchronized Entry record(int batch, String name, Map<String, Object> values,
                                     String deletedBy, String sessionId) {
        prune();
        if (maxEntries < 1 || values.size() > maxValues) return null;
        Entry entry = new Entry(nextId++, name, Collections.unmodifiableMap(new LinkedHashMap<>(values)),
            batch, clock.getAsLong(), deletedBy, sessionId);
        entries.put(entry.id(), entry);
        totalValues += entry.values().size();
        Iterator<Entry> oldest = entries.values().iterator();
        while (entries.size() > maxEntries || totalValues > maxValues) {
            totalValues -= oldest.next().values().size();
            oldest.remove();
        }
        return entry;
    }

    public synchronized void remove(int id) {
        prune();
        Entry removed = entries.remove(id);
        if (removed != null) totalValues -= removed.values().size();
    }

    public synchronized Entry get(int id) {
        prune();
        return entries.get(id);
    }

    public synchronized List<Entry> newestFirst() {
        prune();
        List<Entry> out = new ArrayList<>(entries.values());
        Collections.reverse(out);
        return out;
    }

    public synchronized List<Entry> latestBatch() {
        prune();
        int latest = entries.values().stream().mapToInt(Entry::batch).max().orElse(-1);
        return entries.values().stream().filter(e -> e.batch() == latest).toList();
    }

    private void prune() {
        long now = clock.getAsLong();
        entries.values().removeIf(e -> {
            if (now - e.deletedAt() <= maxAgeMillis) return false;
            totalValues -= e.values().size();
            return true;
        });
    }
}
