package com.skriptvariables.util;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public final class VariableRestorer {

    public record RestoreResult(boolean restored, List<String> conflicts, int written, List<String> failed) {}

    private VariableRestorer() {
    }

    public static RestoreResult restore(List<DeletedVariableBuffer.Entry> entries, VariableStore store,
                                        DeletedVariableBuffer buffer, boolean force) {
        if (entries.isEmpty()) return new RestoreResult(false, List.of(), 0, List.of());

        if (!force) {
            List<String> conflicts = new ArrayList<>();
            for (DeletedVariableBuffer.Entry entry : entries) {
                for (String name : entry.values().keySet()) {
                    if (store.get(name) != null) conflicts.add(name);
                }
            }
            if (!conflicts.isEmpty()) return new RestoreResult(false, conflicts, 0, List.of());
        }

        int written = 0;
        List<String> failed = new ArrayList<>();
        for (DeletedVariableBuffer.Entry entry : entries) {
            boolean complete = true;
            for (Map.Entry<String, Object> value : entry.values().entrySet()) {
                try {
                    store.set(value.getKey(), value.getValue());
                    written++;
                } catch (Exception e) {
                    failed.add(value.getKey());
                    complete = false;
                }
            }
            if (complete) buffer.remove(entry.id());
        }
        return new RestoreResult(true, List.of(), written, failed);
    }
}
