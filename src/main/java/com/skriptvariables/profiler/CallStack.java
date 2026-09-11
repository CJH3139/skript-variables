package com.skriptvariables.profiler;

import java.util.ArrayDeque;

public final class CallStack {

    public record Timing(long inclusiveNs, long selfNs) {}

    private static final class Frame {
        long childNs;
    }

    private final ThreadLocal<ArrayDeque<Frame>> frames = ThreadLocal.withInitial(ArrayDeque::new);

    public void enter() {
        frames.get().push(new Frame());
    }

    public Timing exit(long inclusiveNs) {
        ArrayDeque<Frame> stack = frames.get();
        Frame frame = stack.poll();
        if (frame == null) return new Timing(inclusiveNs, inclusiveNs);
        long self = Math.max(0L, inclusiveNs - frame.childNs);
        Frame parent = stack.peek();
        if (parent != null) parent.childNs += inclusiveNs;
        return new Timing(inclusiveNs, self);
    }
}
