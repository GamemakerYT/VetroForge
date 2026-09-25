// src/main/java/com/vetroforge/events/progress/impl/CactusSoldProgress.java
package com.vetroforge.events.progress.impl;

import com.vetroforge.events.progress.EventProgressSource;

import java.util.concurrent.atomic.AtomicLong;

/** Live-Zähler für verkaufte Cactus aus dem SellShop. */
public class CactusSoldProgress implements EventProgressSource {
    private static final AtomicLong SOLD = new AtomicLong();

    public static void record(long amount) {
        if (amount > 0) SOLD.addAndGet(amount);
    }

    public static long current() {
        return SOLD.get();
    }

    @Override public long readCurrent() {
        return current();
    }
}
