package com.skriptvariables.profiler;

public enum TriggerKind {
    EVENT("event"),
    COMMAND("command"),
    FUNCTION("function"),
    PERIODICAL("periodical");

    private final String json;

    TriggerKind(String json) {
        this.json = json;
    }

    public String json() {
        return json;
    }
}
