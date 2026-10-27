package com.skriptvariables.util;

import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DeletedVariableBufferTest {

    private final long[] now = {0};

    private DeletedVariableBuffer buffer(int maxEntries, long maxAgeMillis) {
        return new DeletedVariableBuffer(maxEntries, maxAgeMillis, () -> now[0]);
    }

    private static DeletedVariableBuffer.Entry record(DeletedVariableBuffer buffer, int batch, String name) {
        return buffer.record(batch, name, Map.of(name, 1L), "Steve", "s1");
    }

    private static List<String> names(List<DeletedVariableBuffer.Entry> entries) {
        return entries.stream().map(DeletedVariableBuffer.Entry::name).toList();
    }

    @Test
    void recordAssignsIncreasingIds() {
        DeletedVariableBuffer buffer = buffer(200, Long.MAX_VALUE);
        int batch = buffer.newBatch();

        DeletedVariableBuffer.Entry first = buffer.record(batch, "coins", Map.of("coins", 5L), "Steve", "s1");
        DeletedVariableBuffer.Entry second = record(buffer, batch, "kills");

        assertEquals(1, first.id());
        assertEquals(2, second.id());
        assertEquals("coins", buffer.get(1).name());
        assertEquals(Map.of("coins", 5L), buffer.get(1).values());
    }

    @Test
    void recordCopiesValues() {
        DeletedVariableBuffer buffer = buffer(200, Long.MAX_VALUE);
        Map<String, Object> values = new HashMap<>();
        values.put("coins", 5L);

        DeletedVariableBuffer.Entry entry = buffer.record(buffer.newBatch(), "coins", values, "Steve", "s1");
        values.put("coins", 9L);

        assertEquals(5L, entry.values().get("coins"));
    }

    @Test
    void evictsOldestPastMaxEntries() {
        DeletedVariableBuffer buffer = buffer(2, Long.MAX_VALUE);
        int batch = buffer.newBatch();
        record(buffer, batch, "a");
        record(buffer, batch, "b");
        record(buffer, batch, "c");

        assertNull(buffer.get(1));
        assertEquals(List.of("c", "b"), names(buffer.newestFirst()));
    }

    @Test
    void expiresByAge() {
        DeletedVariableBuffer buffer = buffer(200, 1000);
        record(buffer, buffer.newBatch(), "a");
        now[0] = 1001;

        assertNull(buffer.get(1));
        assertTrue(buffer.newestFirst().isEmpty());
    }

    @Test
    void removeThenGetReturnsNull() {
        DeletedVariableBuffer buffer = buffer(200, Long.MAX_VALUE);
        DeletedVariableBuffer.Entry entry = record(buffer, buffer.newBatch(), "a");

        buffer.remove(entry.id());

        assertNull(buffer.get(entry.id()));
    }

    @Test
    void latestBatchReturnsOnlyNewestBatch() {
        DeletedVariableBuffer buffer = buffer(200, Long.MAX_VALUE);
        record(buffer, buffer.newBatch(), "x");
        int second = buffer.newBatch();
        record(buffer, second, "y");
        record(buffer, second, "z");

        assertEquals(List.of("y", "z"), names(buffer.latestBatch()));
    }

    @Test
    void latestBatchAfterPartialEviction() {
        DeletedVariableBuffer buffer = buffer(2, Long.MAX_VALUE);
        record(buffer, buffer.newBatch(), "x");
        int second = buffer.newBatch();
        record(buffer, second, "y");
        record(buffer, second, "z");
        buffer.newBatch();

        assertEquals(List.of("y", "z"), names(buffer.latestBatch()));
    }

    @Test
    void evictsOldestPastMaxValues() {
        DeletedVariableBuffer buffer = new DeletedVariableBuffer(200, 3, Long.MAX_VALUE, () -> now[0]);
        int batch = buffer.newBatch();
        buffer.record(batch, "a::*", Map.of("a::1", 1L, "a::2", 2L), "Steve", "s1");
        buffer.record(batch, "b::*", Map.of("b::1", 1L, "b::2", 2L), "Steve", "s1");

        assertEquals(List.of("b::*"), names(buffer.newestFirst()));
    }

    @Test
    void entryLargerThanMaxValuesIsNotRecorded() {
        DeletedVariableBuffer buffer = new DeletedVariableBuffer(200, 3, Long.MAX_VALUE, () -> now[0]);
        record(buffer, buffer.newBatch(), "keep");

        DeletedVariableBuffer.Entry entry = buffer.record(buffer.newBatch(), "big::*",
            Map.of("big::1", 1L, "big::2", 2L, "big::3", 3L, "big::4", 4L), "Steve", "s1");

        assertNull(entry);
        assertEquals(List.of("keep"), names(buffer.newestFirst()));
    }

    @Test
    void zeroCapacityRecordsNothing() {
        DeletedVariableBuffer buffer = buffer(0, Long.MAX_VALUE);

        assertNull(record(buffer, buffer.newBatch(), "a"));
        assertTrue(buffer.newestFirst().isEmpty());
    }
}
