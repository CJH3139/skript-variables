package com.skriptvariables.profiler.hooks;

import ch.njol.skript.ScriptLoader;
import ch.njol.skript.events.EvtAtTime;
import ch.njol.skript.events.EvtPeriodical;
import ch.njol.skript.lang.SkriptEvent;
import ch.njol.skript.lang.Trigger;
import com.skriptvariables.profiler.ProfilerUnavailableException;
import com.skriptvariables.profiler.TriggerKind;
import org.skriptlang.skript.lang.script.Script;
import org.skriptlang.skript.lang.structure.Structure;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

public final class PeriodicalHook implements TriggerHook {

    @Override
    public TriggerKind kind() {
        return TriggerKind.PERIODICAL;
    }

    @Override
    public String label(Trigger trigger) {
        String name = trigger.getName();
        return name == null ? "periodical" : name;
    }

    @Override
    public void wrap(Function<Trigger, Trigger> mapper) throws ProfilerUnavailableException {
        apply(mapper);
    }

    @Override
    public void unwrap() throws ProfilerUnavailableException {
        apply(TriggerFields::unwrapOne);
    }

    private static void apply(Function<Trigger, Trigger> mapper) throws ProfilerUnavailableException {
        List<Structure> structures = new ArrayList<>();
        try {
            for (Script script : ScriptLoader.getLoadedScripts()) {
                structures.addAll(script.getStructures());
            }
        } catch (RuntimeException e) {
            throw new ProfilerUnavailableException("Cannot list loaded scripts: " + e.getMessage());
        }
        Field field = TriggerFields.find(SkriptEvent.class, "trigger");
        for (Structure s : structures) {
            if (s instanceof EvtPeriodical || s instanceof EvtAtTime) TriggerFields.swap(s, field, mapper);
        }
    }
}
