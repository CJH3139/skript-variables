package com.skriptvariables.profiler;

import ch.njol.skript.Skript;
import ch.njol.skript.lang.Trigger;
import com.google.common.collect.Multimap;
import org.bukkit.event.Event;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

public final class Profiler {

    private static final int SPIKE_CAPACITY = 50;

    private static volatile boolean recording;
    private static long startedAt;
    private static long startedNanos;
    private static SpikeBuffer spikes;
    private static Map<Trigger, TriggerStats> statsByTrigger;
    private static String lastProfile;

    private Profiler() {}

    public static boolean isRecording() {
        return recording;
    }

    public static String lastProfile() {
        return lastProfile;
    }

    public static long elapsedMs() {
        if (!recording) return 0L;
        return (System.nanoTime() - startedNanos) / 1_000_000L;
    }

    public static synchronized long executions() {
        if (statsByTrigger == null) return 0L;
        long total = 0L;
        for (TriggerStats s : statsByTrigger.values()) total += s.count();
        return total;
    }

    public static synchronized void start() throws ProfilerUnavailableException {
        if (recording) throw new ProfilerUnavailableException("Already recording");

        Multimap<Class<? extends Event>, Trigger> map = TriggerRegistry.access();

        spikes = new SpikeBuffer(SPIKE_CAPACITY);
        statsByTrigger = new IdentityHashMap<>();
        int[] nextId = {1};

        TriggerRegistry.rewrite(map, original -> {
            if (original instanceof ProfilingTrigger already) return already;
            TriggerStats stats = statsByTrigger.computeIfAbsent(original, t -> new TriggerStats(
                nextId[0]++,
                t.getScript() == null ? "unknown" : t.getScript().nameAndPath(),
                t.getName(),
                t.getLineNumber()
            ));
            return new ProfilingTrigger(original, stats, spikes);
        });

        startedAt = System.currentTimeMillis();
        startedNanos = System.nanoTime();
        recording = true;
    }

    /** Restores the original triggers and returns the JSON payload. */
    public static synchronized String stop() throws ProfilerUnavailableException {
        if (!recording) throw new ProfilerUnavailableException("Not recording");

        long durationMs = elapsedMs();
        recording = false;

        Multimap<Class<? extends Event>, Trigger> map = TriggerRegistry.access();
        TriggerRegistry.rewrite(map, current ->
            current instanceof ProfilingTrigger wrapped ? wrapped.original() : current);

        List<TriggerStats> collected = new ArrayList<>(statsByTrigger.values());
        List<Spike> collectedSpikes = spikes.drainSorted();

        lastProfile = ProfileJson.build(
            startedAt,
            durationMs,
            Skript.getVersion().toString(),
            collected,
            collectedSpikes
        );

        statsByTrigger = null;
        spikes = null;
        return lastProfile;
    }

    /** Called when a reload or shutdown makes the swapped triggers unsafe. */
    public static synchronized void abortIfRecording() {
        if (!recording) return;
        try {
            stop();
            logAbort();
        } catch (ProfilerUnavailableException ignored) {
            try {
                Multimap<Class<? extends Event>, Trigger> map = TriggerRegistry.access();
                TriggerRegistry.rewrite(map, current ->
                    current instanceof ProfilingTrigger wrapped ? wrapped.original() : current);
            } catch (ProfilerUnavailableException stillFailing) {
                // Best effort: registry could not be reached to unwrap. Fall through
                // and clear local state anyway so the recorder doesn't stay stuck.
            }
            recording = false;
            statsByTrigger = null;
            spikes = null;
            logAbort();
        }
    }

    private static void logAbort() {
        try {
            java.util.logging.Logger logger = com.skriptvariables.SkriptVariables.getInstance() != null
                ? com.skriptvariables.SkriptVariables.getInstance().getLogger()
                : java.util.logging.Logger.getLogger("skript-variables");
            logger.info("Profiling stopped early because scripts were reloaded or unloaded. "
                + "Run /skv profile upload to send the partial profile.");
        } catch (RuntimeException ignored) {
            // Logging must never break the abort path.
        }
    }
}
