package com.skriptvariables.profiler;

import ch.njol.skript.Skript;
import ch.njol.skript.lang.Trigger;
import com.skriptvariables.SkriptVariables;
import com.skriptvariables.profiler.hooks.CommandHook;
import com.skriptvariables.profiler.hooks.EventHook;
import com.skriptvariables.profiler.hooks.FunctionHook;
import com.skriptvariables.profiler.hooks.PeriodicalHook;
import com.skriptvariables.profiler.hooks.TriggerHook;

import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.logging.Logger;

public final class Profiler {

    private static final int SPIKE_CAPACITY = 50;

    private static volatile boolean recording;
    private static long startedAt;
    private static long startedNanos;
    private static SpikeBuffer spikes;
    private static CallStack callStack;
    private static Map<Trigger, TriggerStats> statsByTrigger;
    private static List<TriggerHook> engaged = new ArrayList<>();
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

        spikes = new SpikeBuffer(SPIKE_CAPACITY);
        callStack = new CallStack();
        statsByTrigger = new IdentityHashMap<>();
        engaged = new ArrayList<>();
        int[] nextId = {1};

        TriggerHook events = new EventHook();
        try {
            events.wrap(mapperFor(events, nextId));
        } catch (ProfilerUnavailableException e) {
            clear();
            throw e;
        }
        engaged.add(events);

        for (TriggerHook hook : List.of(new CommandHook(), new FunctionHook(), new PeriodicalHook())) {
            try {
                hook.wrap(mapperFor(hook, nextId));
                engaged.add(hook);
            } catch (ProfilerUnavailableException e) {
                logger().warning("Profiler could not hook " + hook.kind().json() + "s: " + e.getMessage()
                    + ". Recording continues without them.");
                try {
                    hook.unwrap();
                } catch (ProfilerUnavailableException ignored) {
                }
            }
        }

        startedAt = System.currentTimeMillis();
        startedNanos = System.nanoTime();
        recording = true;
    }

    private static Function<Trigger, Trigger> mapperFor(TriggerHook hook, int[] nextId) {
        return original -> {
            if (original instanceof ProfilingTrigger already) return already;
            TriggerStats stats = statsByTrigger.computeIfAbsent(original, t -> new TriggerStats(
                nextId[0]++,
                t.getScript() == null ? "unknown" : t.getScript().nameAndPath(),
                hook.label(t),
                t.getLineNumber(),
                hook.kind()
            ));
            return new ProfilingTrigger(original, stats, spikes, callStack);
        };
    }

    public static synchronized String stop() throws ProfilerUnavailableException {
        if (!recording) throw new ProfilerUnavailableException("Not recording");

        long durationMs = elapsedMs();
        recording = false;

        ProfilerUnavailableException failure = unwrapAll();

        List<TriggerStats> collected = new ArrayList<>(statsByTrigger.values());
        List<Spike> collectedSpikes = spikes.drainSorted();

        lastProfile = ProfileJson.build(
            startedAt,
            durationMs,
            Skript.getVersion().toString(),
            collected,
            collectedSpikes
        );

        clear();
        if (failure != null) throw failure;
        return lastProfile;
    }

    public static synchronized void abortIfRecording() {
        if (!recording) return;
        try {
            stop();
        } catch (ProfilerUnavailableException e) {
            recording = false;
            clear();
        }
        logAbort();
    }

    private static ProfilerUnavailableException unwrapAll() {
        ProfilerUnavailableException first = null;
        List<TriggerHook> reversed = new ArrayList<>(engaged);
        Collections.reverse(reversed);
        for (TriggerHook hook : reversed) {
            try {
                hook.unwrap();
            } catch (ProfilerUnavailableException e) {
                if (first == null) first = e;
                logger().warning("Profiler could not unhook " + hook.kind().json() + "s: " + e.getMessage());
            }
        }
        return first;
    }

    private static void clear() {
        statsByTrigger = null;
        spikes = null;
        callStack = null;
        engaged = new ArrayList<>();
    }

    private static Logger logger() {
        SkriptVariables plugin = SkriptVariables.getInstance();
        return plugin != null ? plugin.getLogger() : Logger.getLogger("skript-variables");
    }

    private static void logAbort() {
        try {
            logger().info("Profiling stopped early because scripts were reloaded or unloaded. "
                + "Run /skv profile upload to send the partial profile.");
        } catch (RuntimeException ignored) {
        }
    }
}
