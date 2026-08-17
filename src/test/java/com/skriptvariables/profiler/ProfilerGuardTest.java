package com.skriptvariables.profiler;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ProfilerGuardTest {

    @Test
    void matchesLeadingSlashForm() {
        assertTrue(ProfilerGuard.isReload("/sk reload"));
    }

    @Test
    void matchesWithoutLeadingSlash() {
        assertTrue(ProfilerGuard.isReload("sk reload"));
    }

    @Test
    void matchesWithLeadingWhitespaceBeforeSlash() {
        assertTrue(ProfilerGuard.isReload("  /sk reload"));
    }

    @Test
    void matchesDoubleSpaceBetweenWords() {
        assertTrue(ProfilerGuard.isReload("/sk  reload"));
    }

    @Test
    void matchesAnyCase() {
        assertTrue(ProfilerGuard.isReload("/SK RELOAD"));
    }

    @Test
    void matchesSkriptLongForm() {
        assertTrue(ProfilerGuard.isReload("/skript reload"));
    }

    @Test
    void matchesNamespacedForm() {
        assertTrue(ProfilerGuard.isReload("/skript:sk reload"));
    }

    @Test
    void matchesDisable() {
        assertTrue(ProfilerGuard.isReload("/sk disable myscript"));
    }

    @Test
    void matchesEnable() {
        assertTrue(ProfilerGuard.isReload("/sk enable myscript"));
    }

    @Test
    void matchesSkriptLongFormDisable() {
        assertTrue(ProfilerGuard.isReload("/skript disable foo"));
    }

    @Test
    void rejectsSkWithoutReload() {
        assertFalse(ProfilerGuard.isReload("/sk"));
    }

    @Test
    void rejectsSkriptWithoutReload() {
        assertFalse(ProfilerGuard.isReload("/skript"));
    }

    @Test
    void rejectsPluginManagerReload() {
        assertFalse(ProfilerGuard.isReload("/reload"));
    }

    @Test
    void rejectsUnrelatedCommand() {
        assertFalse(ProfilerGuard.isReload("/skv profile start"));
    }

    @Test
    void rejectsNull() {
        assertFalse(ProfilerGuard.isReload(null));
    }

    @Test
    void rejectsEmptyString() {
        assertFalse(ProfilerGuard.isReload(""));
    }
}
