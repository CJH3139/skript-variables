package com.skriptvariables.profiler;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class TriggerStatsTest {

    @Test
    void accumulatesCountAndTotal() {
        TriggerStats stats = new TriggerStats(1, "scripts/kits.sk", "on right click", 42);
        stats.record(100L);
        stats.record(250L);

        assertEquals(2, stats.count());
        assertEquals(350L, stats.totalNs());
    }

    @Test
    void tracksTheSlowestSingleRun() {
        TriggerStats stats = new TriggerStats(1, "scripts/kits.sk", "on right click", 42);
        stats.record(100L);
        stats.record(900L);
        stats.record(400L);

        assertEquals(900L, stats.maxNs());
    }

    @Test
    void startsAtZero() {
        TriggerStats stats = new TriggerStats(7, "scripts/a.sk", "on join", 1);

        assertEquals(0, stats.count());
        assertEquals(0L, stats.totalNs());
        assertEquals(0L, stats.maxNs());
        assertEquals(7, stats.id());
        assertEquals("scripts/a.sk", stats.script());
        assertEquals("on join", stats.event());
        assertEquals(1, stats.line());
    }
}
