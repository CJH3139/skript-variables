package com.skriptvariables.profiler;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class TriggerStatsTest {

    @Test
    void accumulatesCountTotalAndSelf() {
        TriggerStats stats = new TriggerStats(1, "scripts/kits.sk", "on right click", 42, TriggerKind.EVENT);
        stats.record(100L, 80L);
        stats.record(250L, 250L);

        assertEquals(2, stats.count());
        assertEquals(350L, stats.totalNs());
        assertEquals(330L, stats.selfNs());
    }

    @Test
    void tracksTheSlowestInclusiveRun() {
        TriggerStats stats = new TriggerStats(1, "scripts/kits.sk", "on right click", 42, TriggerKind.EVENT);
        stats.record(100L, 100L);
        stats.record(900L, 10L);
        stats.record(400L, 400L);

        assertEquals(900L, stats.maxNs());
    }

    @Test
    void startsAtZeroAndKeepsIdentity() {
        TriggerStats stats = new TriggerStats(7, "scripts/a.sk", "function f()", 1, TriggerKind.FUNCTION);

        assertEquals(0, stats.count());
        assertEquals(0L, stats.totalNs());
        assertEquals(0L, stats.selfNs());
        assertEquals(0L, stats.maxNs());
        assertEquals(7, stats.id());
        assertEquals("scripts/a.sk", stats.script());
        assertEquals("function f()", stats.event());
        assertEquals(1, stats.line());
        assertEquals(TriggerKind.FUNCTION, stats.kind());
    }

    @Test
    void kindJsonNamesAreLowercase() {
        assertEquals("event", TriggerKind.EVENT.json());
        assertEquals("command", TriggerKind.COMMAND.json());
        assertEquals("function", TriggerKind.FUNCTION.json());
        assertEquals("periodical", TriggerKind.PERIODICAL.json());
    }
}
