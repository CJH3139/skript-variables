package com.skriptvariables.events;

import org.bukkit.command.CommandSender;
import org.bukkit.event.Cancellable;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.NotNull;

public final class VariablesApplyEvent extends Event implements Cancellable {
    private static final HandlerList HANDLERS = new HandlerList();
    private final CommandSender sender;
    private final String[] variableNames;
    private boolean cancelled = false;

    public VariablesApplyEvent(CommandSender sender, String[] variableNames) {
        this.sender = sender;
        this.variableNames = variableNames.clone();
    }

    public CommandSender getSender() {
        return sender;
    }

    public String[] getVariableNames() {
        return variableNames.clone();
    }

    @Override
    public boolean isCancelled() {
        return cancelled;
    }

    @Override
    public void setCancelled(boolean cancelled) {
        this.cancelled = cancelled;
    }

    @Override
    public @NotNull HandlerList getHandlers() {
        return HANDLERS;
    }

    public static HandlerList getHandlerList() {
        return HANDLERS;
    }
}
