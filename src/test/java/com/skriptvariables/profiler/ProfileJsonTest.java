package com.skriptvariables.profiler;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;

class ProfileJsonTest {

    @Test
    void includesHeaderAndTriggerFields() {
        TriggerStats stats = new TriggerStats(3, "scripts/kits.sk", "on right click", 42, TriggerKind.EVENT);
        stats.record(1000L, 800L);

        String json = ProfileJson.build(1760000000000L, 60000L, "2.14.0", List.of(stats), List.of());

        assertTrue(json.contains("\"version\":2"), json);
        assertTrue(json.contains("\"durationMs\":60000"), json);
        assertTrue(json.contains("\"skriptVersion\":\"2.14.0\""), json);
        assertTrue(json.contains("\"script\":\"scripts/kits.sk\""), json);
        assertTrue(json.contains("\"event\":\"on right click\""), json);
        assertTrue(json.contains("\"line\":42"), json);
        assertTrue(json.contains("\"kind\":\"event\""), json);
        assertTrue(json.contains("\"count\":1"), json);
        assertTrue(json.contains("\"totalNs\":1000"), json);
        assertTrue(json.contains("\"selfNs\":800"), json);
    }

    @Test
    void writesEachKindName() {
        TriggerStats cmd = new TriggerStats(1, "a.sk", "command /kit", 1, TriggerKind.COMMAND);
        cmd.record(1L, 1L);
        TriggerStats fn = new TriggerStats(2, "a.sk", "function f()", 2, TriggerKind.FUNCTION);
        fn.record(1L, 1L);
        TriggerStats per = new TriggerStats(3, "a.sk", "every 1 second", 3, TriggerKind.PERIODICAL);
        per.record(1L, 1L);

        String json = ProfileJson.build(0L, 1L, "2.14.0", List.of(cmd, fn, per), List.of());

        assertTrue(json.contains("\"kind\":\"command\""), json);
        assertTrue(json.contains("\"kind\":\"function\""), json);
        assertTrue(json.contains("\"kind\":\"periodical\""), json);
    }

    @Test
    void escapesQuotesAndBackslashesInScriptNames() {
        TriggerStats stats = new TriggerStats(1, "scripts\\win\"path.sk", "on join", 1, TriggerKind.EVENT);
        stats.record(5L, 5L);

        String json = ProfileJson.build(0L, 1L, "2.14.0", List.of(stats), List.of());

        assertTrue(json.contains("scripts\\\\win\\\"path.sk"), json);
    }

    @Test
    void serializesSpikes() {
        String json = ProfileJson.build(0L, 1L, "2.14.0", List.of(), List.of(new Spike(3, 55L, 8200000L)));

        assertTrue(json.contains("\"triggerId\":3"), json);
        assertTrue(json.contains("\"ns\":8200000"), json);
        assertTrue(json.contains("\"at\":55"), json);
    }
}
