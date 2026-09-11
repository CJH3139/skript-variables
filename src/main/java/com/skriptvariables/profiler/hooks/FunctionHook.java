package com.skriptvariables.profiler.hooks;

import ch.njol.skript.lang.Trigger;
import ch.njol.skript.lang.function.Function;
import ch.njol.skript.lang.function.Functions;
import ch.njol.skript.lang.function.ScriptFunction;
import com.skriptvariables.profiler.ProfilerUnavailableException;
import com.skriptvariables.profiler.TriggerKind;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;

public final class FunctionHook implements TriggerHook {

    @Override
    public TriggerKind kind() {
        return TriggerKind.FUNCTION;
    }

    @Override
    public String label(Trigger trigger) {
        String name = trigger.getName();
        if (name == null) return "function";
        if (name.startsWith("function ")) return name.endsWith(")") ? name : name + "()";
        return "function " + name + (name.endsWith(")") ? "" : "()");
    }

    @Override
    public void wrap(java.util.function.Function<Trigger, Trigger> mapper) throws ProfilerUnavailableException {
        apply(mapper);
    }

    @Override
    public void unwrap() throws ProfilerUnavailableException {
        apply(TriggerFields::unwrapOne);
    }

    private static void apply(java.util.function.Function<Trigger, Trigger> mapper) throws ProfilerUnavailableException {
        List<Function<?>> snapshot;
        try {
            snapshot = new ArrayList<>(Functions.getFunctions());
        } catch (RuntimeException e) {
            throw new ProfilerUnavailableException("Cannot list functions: " + e.getMessage());
        }
        Field field = TriggerFields.find(ScriptFunction.class, "trigger");
        for (Function<?> fn : snapshot) {
            if (fn instanceof ScriptFunction) TriggerFields.swap(fn, field, mapper);
        }
    }
}
