package com.skriptvariables.profiler;

import java.util.Collection;
import java.util.List;

public final class ProfileJson {

    private ProfileJson() {}

    public static String build(long startedAt,
                               long durationMs,
                               String skriptVersion,
                               Collection<TriggerStats> stats,
                               List<Spike> spikes) {
        StringBuilder sb = new StringBuilder(512);
        sb.append('{');
        sb.append("\"version\":1,");
        sb.append("\"startedAt\":").append(startedAt).append(',');
        sb.append("\"durationMs\":").append(durationMs).append(',');
        sb.append("\"skriptVersion\":\"").append(esc(skriptVersion)).append("\",");

        sb.append("\"triggers\":[");
        boolean first = true;
        for (TriggerStats s : stats) {
            if (s.count() == 0) continue;
            if (!first) sb.append(',');
            first = false;
            sb.append('{')
              .append("\"id\":").append(s.id()).append(',')
              .append("\"script\":\"").append(esc(s.script())).append("\",")
              .append("\"event\":\"").append(esc(s.event())).append("\",")
              .append("\"line\":").append(s.line()).append(',')
              .append("\"count\":").append(s.count()).append(',')
              .append("\"totalNs\":").append(s.totalNs()).append(',')
              .append("\"maxNs\":").append(s.maxNs())
              .append('}');
        }
        sb.append("],");

        sb.append("\"spikes\":[");
        first = true;
        for (Spike sp : spikes) {
            if (!first) sb.append(',');
            first = false;
            sb.append('{')
              .append("\"triggerId\":").append(sp.triggerId()).append(',')
              .append("\"at\":").append(sp.at()).append(',')
              .append("\"ns\":").append(sp.ns())
              .append('}');
        }
        sb.append(']');

        sb.append('}');
        return sb.toString();
    }

    private static String esc(String s) {
        if (s == null) return "";
        StringBuilder out = new StringBuilder(s.length() + 8);
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '"' -> out.append("\\\"");
                case '\\' -> out.append("\\\\");
                case '\n' -> out.append("\\n");
                case '\r' -> out.append("\\r");
                case '\t' -> out.append("\\t");
                default -> {
                    if (c < 0x20) out.append(String.format("\\u%04x", (int) c));
                    else out.append(c);
                }
            }
        }
        return out.toString();
    }
}
