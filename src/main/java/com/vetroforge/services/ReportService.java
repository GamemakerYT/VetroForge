package com.vetroforge.services;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class ReportService {
    private final Map<String, Deque<Long>> reportsByPlayer = new ConcurrentHashMap<>();

    public void recordReport(String playerName) {
        if (playerName == null || playerName.isBlank()) return;
        Deque<Long> deque = reportsByPlayer.computeIfAbsent(key(playerName), ignored -> new ArrayDeque<>());
        synchronized (deque) {
            deque.addLast(System.currentTimeMillis());
            pruneOlderThan(deque, System.currentTimeMillis() - 24L * 60L * 60L * 1000L);
        }
    }

    public int getRecentReportsCount(String playerName, int minutes) {
        if (playerName == null || playerName.isBlank() || minutes <= 0) return 0;
        Deque<Long> deque = reportsByPlayer.get(key(playerName));
        if (deque == null) return 0;
        long cutoff = System.currentTimeMillis() - minutes * 60_000L;
        synchronized (deque) {
            pruneOlderThan(deque, cutoff);
            return deque.size();
        }
    }

    private void pruneOlderThan(Deque<Long> deque, long cutoff) {
        while (!deque.isEmpty() && deque.peekFirst() < cutoff) deque.removeFirst();
    }

    private static String key(String playerName) {
        return playerName.toLowerCase(Locale.ROOT);
    }
}
