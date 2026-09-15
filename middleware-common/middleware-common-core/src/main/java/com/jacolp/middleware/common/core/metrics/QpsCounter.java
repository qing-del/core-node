package com.jacolp.middleware.common.core.metrics;

import java.util.concurrent.atomic.AtomicInteger;

public class QpsCounter {

    private final AtomicInteger count = new AtomicInteger(0);

    public void increment() {
        count.incrementAndGet();
    }

    public int getAndReset() {
        return count.getAndSet(0);
    }
}
