package com.skriptvariables.skript;

import ch.njol.skript.util.Timespan;
import org.skriptlang.skript.registration.DefaultSyntaxInfos;
import org.skriptlang.skript.registration.SyntaxInfo;
import org.skriptlang.skript.registration.SyntaxRegistry;

public final class SkvSyntax {

    private SkvSyntax() {}

    public static void register(SyntaxRegistry registry) {
        EvtVariablesApply.register();

        registry.register(SyntaxRegistry.EFFECT, SyntaxInfo.builder(EffStartProfile.class)
            .addPattern("start [a|the] skript profile [for %-timespan%]")
            .build());
        registry.register(SyntaxRegistry.EFFECT, SyntaxInfo.builder(EffStopProfile.class)
            .addPattern("stop [the] skript profile [and send [the] link to %-commandsender%]")
            .build());
        registry.register(SyntaxRegistry.EFFECT, SyntaxInfo.builder(EffOpenEditor.class)
            .addPattern("open [the] variable editor for %commandsenders%")
            .build());

        registry.register(SyntaxRegistry.CONDITION, SyntaxInfo.builder(CondProfilerRecording.class)
            .addPatterns(
                "[the] skript profiler is recording",
                "[the] skript profiler (isn't|is not) recording")
            .build());

        registry.register(SyntaxRegistry.EXPRESSION,
            DefaultSyntaxInfos.Expression.builder(ExprProfilerElapsedTime.class, Timespan.class)
                .priority(SyntaxInfo.SIMPLE)
                .addPattern("[the] skript profiler elapsed time")
                .build());
        registry.register(SyntaxRegistry.EXPRESSION,
            DefaultSyntaxInfos.Expression.builder(ExprProfilerExecutionCount.class, Long.class)
                .priority(SyntaxInfo.SIMPLE)
                .addPattern("[the] skript profiler execution count")
                .build());
    }
}
