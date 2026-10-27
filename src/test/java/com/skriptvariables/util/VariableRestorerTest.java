package com.skriptvariables.util;

import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class VariableRestorerTest {

    private static final class MapStore implements VariableStore {
        final Map<String, Object> values = new HashMap<>();

        @Override
        public Object get(String name) {
            return values.get(name);
        }

        @Override
        public void set(String name, Object value) {
            if (value == null) values.remove(name);
            else values.put(name, value);
        }
    }

    private final DeletedVariableBuffer buffer = new DeletedVariableBuffer(200, Long.MAX_VALUE, () -> 0L);
    private final MapStore store = new MapStore();

    private DeletedVariableBuffer.Entry record(String name, Map<String, Object> values) {
        return buffer.record(1, name, values, "Steve", "s1");
    }

    @Test
    void restoreWritesValuesBackAndRemovesEntry() {
        DeletedVariableBuffer.Entry entry = record("coins", Map.of("coins", 5L));

        VariableRestorer.RestoreResult result = VariableRestorer.restore(List.of(entry), store, buffer, false);

        assertTrue(result.restored());
        assertEquals(5L, store.values.get("coins"));
        assertNull(buffer.get(entry.id()));
        assertEquals(1, result.written());
    }

    @Test
    void restoreRefusesOnConflict() {
        DeletedVariableBuffer.Entry entry = record("coins", Map.of("coins", 5L));
        store.values.put("coins", 9L);

        VariableRestorer.RestoreResult result = VariableRestorer.restore(List.of(entry), store, buffer, false);

        assertFalse(result.restored());
        assertEquals(List.of("coins"), result.conflicts());
        assertEquals(9L, store.values.get("coins"));
        assertNotNull(buffer.get(entry.id()));
    }

    @Test
    void forceOverwritesConflict() {
        DeletedVariableBuffer.Entry entry = record("coins", Map.of("coins", 5L));
        store.values.put("coins", 9L);

        VariableRestorer.RestoreResult result = VariableRestorer.restore(List.of(entry), store, buffer, true);

        assertTrue(result.restored());
        assertEquals(5L, store.values.get("coins"));
    }

    @Test
    void listRestoreIgnoresNewIndices() {
        DeletedVariableBuffer.Entry entry = record("homes::*", Map.of("homes::a", 1L));
        store.values.put("homes::z", 7L);

        VariableRestorer.RestoreResult result = VariableRestorer.restore(List.of(entry), store, buffer, false);

        assertTrue(result.restored());
        assertEquals(1L, store.values.get("homes::a"));
        assertEquals(7L, store.values.get("homes::z"));
    }

    @Test
    void multiEntryConflictBlocksAll() {
        DeletedVariableBuffer.Entry a = record("a", Map.of("a", 1L));
        DeletedVariableBuffer.Entry b = record("b", Map.of("b", 2L));
        store.values.put("b", 0L);

        VariableRestorer.RestoreResult result = VariableRestorer.restore(List.of(a, b), store, buffer, false);

        assertFalse(result.restored());
        assertEquals(List.of("b"), result.conflicts());
        assertFalse(store.values.containsKey("a"));
    }

    @Test
    void failedWriteKeepsEntryAndReportsName() {
        DeletedVariableBuffer.Entry a = record("a", Map.of("a", 1L));
        DeletedVariableBuffer.Entry b = record("b", Map.of("b", 2L));
        VariableStore failing = new VariableStore() {
            @Override
            public Object get(String name) {
                return store.get(name);
            }

            @Override
            public void set(String name, Object value) {
                if (name.equals("b")) throw new IllegalStateException("storage down");
                store.set(name, value);
            }
        };

        VariableRestorer.RestoreResult result = VariableRestorer.restore(List.of(a, b), failing, buffer, false);

        assertEquals(List.of("b"), result.failed());
        assertEquals(1, result.written());
        assertEquals(1L, store.values.get("a"));
        assertNull(buffer.get(a.id()));
        assertNotNull(buffer.get(b.id()));
    }

    @Test
    void emptyEntriesRestoresNothing() {
        VariableRestorer.RestoreResult result = VariableRestorer.restore(List.of(), store, buffer, false);

        assertFalse(result.restored());
        assertEquals(0, result.written());
    }
}
