package com.skriptvariables.skript;

import ch.njol.skript.SkriptConfig;
import ch.njol.skript.lang.Literal;
import ch.njol.skript.lang.SkriptEvent;
import ch.njol.skript.lang.SkriptParser.ParseResult;
import ch.njol.skript.registrations.EventValues;
import com.skriptvariables.events.VariablesApplyEvent;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.skriptlang.skript.bukkit.registration.BukkitSyntaxInfos;
import org.skriptlang.skript.registration.SyntaxRegistry;

public final class EvtVariablesApply extends SkriptEvent {

    public static void register(SyntaxRegistry registry) {
        registry.register(BukkitSyntaxInfos.Event.KEY,
            BukkitSyntaxInfos.Event.builder(EvtVariablesApply.class, "Variable Apply")
                .supplier(EvtVariablesApply::new)
                .addEvent(VariablesApplyEvent.class)
                .addPattern("variable[s] [editor] (push|commit|change)")
                .listeningBehavior(SkriptConfig.listenCancelledByDefault.value() ? ListeningBehavior.ANY : ListeningBehavior.UNCANCELLED)
                .build());
        EventValues.registerEventValue(VariablesApplyEvent.class, CommandSender.class, e -> e.getSender(), EventValues.TIME_NOW);
        EventValues.registerEventValue(VariablesApplyEvent.class, Player.class, e -> e.getSender() instanceof Player p ? p : null, EventValues.TIME_NOW);
        EventValues.registerEventValue(VariablesApplyEvent.class, String[].class, e -> e.getVariableNames(), EventValues.TIME_FUTURE);
    }

    @Override
    public boolean init(Literal<?>[] args, int matchedPattern, ParseResult parseResult) {
        return true;
    }

    @Override
    public boolean check(Event event) {
        return true;
    }

    @Override
    public @NotNull String toString(@Nullable Event event, boolean debug) {
        return "variable apply";
    }
}
