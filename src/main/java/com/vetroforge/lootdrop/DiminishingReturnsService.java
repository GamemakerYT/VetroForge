// src/main/java/com/vetroforge/lootdrop/DiminishingReturnsService.java
package com.vetroforge.lootdrop;

import java.util.*;

public class DiminishingReturnsService {
    // merkt sich die letzten N Öffnungen pro Spieler
    private final Map<UUID, Deque<Long>> opens = new HashMap<>();
    private final int windowSec = 60 * 30; // 30 min
    private final int maxRemember = 8;

    /** 0.0 (keine Strafe) … 1.0+ (stark) */
    public double penaltyFor(UUID id){
        long now = System.currentTimeMillis();
        Deque<Long> q = opens.getOrDefault(id, new ArrayDeque<>());
        q.removeIf(t -> (now - t) / 1000 > windowSec);
        int k = q.size(); // wie viele in letzter Zeit
        // sanfte Kurve
        return Math.min(1.5, 0.12 * k * k);
    }

    public void noteOpen(UUID id){
        long now = System.currentTimeMillis();
        Deque<Long> q = opens.computeIfAbsent(id, __ -> new ArrayDeque<>());
        q.addLast(now);
        while (q.size() > maxRemember) q.removeFirst();
    }
}