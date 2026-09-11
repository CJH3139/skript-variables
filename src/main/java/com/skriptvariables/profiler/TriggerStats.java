package com.skriptvariables.profiler;

public final class TriggerStats {

    private final int id;
    private final String script;
    private final String event;
    private final int line;
    private final TriggerKind kind;

    private long count;
    private long totalNs;
    private long selfNs;
    private long maxNs;

    public TriggerStats(int id, String script, String event, int line, TriggerKind kind) {
        this.id = id;
        this.script = script;
        this.event = event;
        this.line = line;
        this.kind = kind;
    }

    public synchronized void record(long inclusiveNs, long selfNs) {
        count++;
        totalNs += inclusiveNs;
        this.selfNs += selfNs;
        if (inclusiveNs > maxNs) maxNs = inclusiveNs;
    }

    public int id() { return id; }
    public String script() { return script; }
    public String event() { return event; }
    public int line() { return line; }
    public TriggerKind kind() { return kind; }

    public synchronized long count() { return count; }
    public synchronized long totalNs() { return totalNs; }
    public synchronized long selfNs() { return selfNs; }
    public synchronized long maxNs() { return maxNs; }
}
