package com.skriptvariables.profiler.hooks;

import ch.njol.skript.lang.Trigger;
import com.skriptvariables.profiler.ProfilerUnavailableException;
import com.skriptvariables.profiler.TriggerKind;

import java.util.function.Function;

public interface TriggerHook {

    TriggerKind kind();

    String label(Trigger trigger);

    void wrap(Function<Trigger, Trigger> mapper) throws ProfilerUnavailableException;

    void unwrap() throws ProfilerUnavailableException;
}
