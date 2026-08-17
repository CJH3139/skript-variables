package com.skriptvariables.profiler;

/**
 * Mutable counters for one trigger. Synchronized because Skript triggers can
 * run off the main thread, and an uncontended lock costs far less than the
 * work being measured.
 */
public final class TriggerStats {

    private final int id;
    private final String script;
    private final String event;
    private final int line;

    private long count;
    private long totalNs;
    private long maxNs;

    public TriggerStats(int id, String script, String event, int line) {
        this.id = id;
        this.script = script;
        this.event = event;
        this.line = line;
    }

    public synchronized void record(long ns) {
        count++;
        totalNs += ns;
        if (ns > maxNs) maxNs = ns;
    }

    public int id() { return id; }
    public String script() { return script; }
    public String event() { return event; }
    public int line() { return line; }

    public synchronized long count() { return count; }
    public synchronized long totalNs() { return totalNs; }
    public synchronized long maxNs() { return maxNs; }
}
