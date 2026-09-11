package com.skriptvariables.profiler;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class CallStackTest {

    @Test
    void topLevelSelfEqualsInclusive() {
        CallStack stack = new CallStack();
        stack.enter();
        CallStack.Timing t = stack.exit(500L);

        assertEquals(500L, t.inclusiveNs());
        assertEquals(500L, t.selfNs());
    }

    @Test
    void parentSelfExcludesChildTime() {
        CallStack stack = new CallStack();
        stack.enter();
        stack.enter();
        CallStack.Timing child = stack.exit(300L);
        CallStack.Timing parent = stack.exit(1000L);

        assertEquals(300L, child.selfNs());
        assertEquals(1000L, parent.inclusiveNs());
        assertEquals(700L, parent.selfNs());
    }

    @Test
    void grandchildTimeOnlySubtractsFromItsDirectParent() {
        CallStack stack = new CallStack();
        stack.enter();
        stack.enter();
        stack.enter();
        stack.exit(100L);
        CallStack.Timing middle = stack.exit(400L);
        CallStack.Timing top = stack.exit(1000L);

        assertEquals(300L, middle.selfNs());
        assertEquals(600L, top.selfNs());
    }

    @Test
    void siblingsBothSubtractFromParent() {
        CallStack stack = new CallStack();
        stack.enter();
        stack.enter();
        stack.exit(100L);
        stack.enter();
        stack.exit(200L);
        CallStack.Timing top = stack.exit(1000L);

        assertEquals(700L, top.selfNs());
    }

    @Test
    void selfNeverGoesNegative() {
        CallStack stack = new CallStack();
        stack.enter();
        stack.enter();
        stack.exit(900L);
        CallStack.Timing top = stack.exit(800L);

        assertEquals(0L, top.selfNs());
    }

    @Test
    void exitWithoutEnterReturnsInclusiveAsSelf() {
        CallStack stack = new CallStack();
        CallStack.Timing t = stack.exit(50L);

        assertEquals(50L, t.selfNs());
    }

    @Test
    void framesAreIsolatedPerThread() throws Exception {
        CallStack stack = new CallStack();
        stack.enter();
        long[] otherSelf = new long[1];
        Thread other = new Thread(() -> {
            stack.enter();
            otherSelf[0] = stack.exit(40L).selfNs();
        });
        other.start();
        other.join();
        CallStack.Timing main = stack.exit(100L);

        assertEquals(40L, otherSelf[0]);
        assertEquals(100L, main.selfNs());
    }
}
