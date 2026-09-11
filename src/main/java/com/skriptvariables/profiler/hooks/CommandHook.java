package com.skriptvariables.profiler.hooks;

import ch.njol.skript.command.Commands;
import ch.njol.skript.command.ScriptCommand;
import ch.njol.skript.lang.Trigger;
import com.skriptvariables.profiler.ProfilerUnavailableException;
import com.skriptvariables.profiler.TriggerKind;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

public final class CommandHook implements TriggerHook {

    @Override
    public TriggerKind kind() {
        return TriggerKind.COMMAND;
    }

    @Override
    public String label(Trigger trigger) {
        String name = trigger.getName();
        if (name == null) return "command";
        String lower = name.toLowerCase();
        if (lower.startsWith("command /")) return name;
        if (lower.startsWith("command ")) return "command /" + name.substring("command ".length());
        return "command /" + name;
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
        Object raw = TriggerFields.readStatic(Commands.class, "commands");
        if (!(raw instanceof Map<?, ?> map)) {
            throw new ProfilerUnavailableException("Commands.commands is not a Map");
        }
        Field field = TriggerFields.find(ScriptCommand.class, "trigger");
        List<Object> snapshot = new ArrayList<>(map.values());
        for (Object cmd : snapshot) {
            if (cmd instanceof ScriptCommand) TriggerFields.swap(cmd, field, mapper);
        }
    }
}
