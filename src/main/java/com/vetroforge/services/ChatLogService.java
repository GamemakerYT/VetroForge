package com.vetroforge.services;

import java.time.Instant;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class ChatLogService {
    private static final int MAX_LINES_PER_PLAYER = 80;
    private final Map<String, Deque<Entry>> byPlayer = new ConcurrentHashMap<>();

    public void record(String playerName, String message) {
        if (playerName == null || playerName.isBlank() || message == null || message.isBlank()) return;
        String key = key(playerName);
        Deque<Entry> deque = byPlayer.computeIfAbsent(key, ignored -> new ArrayDeque<>());
        synchronized (deque) {
            deque.addLast(new Entry(System.currentTimeMillis(), message));
            while (deque.size() > MAX_LINES_PER_PLAYER) deque.removeFirst();
        }
    }

    public String getRecentLines(String playerName, int maxLines) {
        if (playerName == null || playerName.isBlank() || maxLines <= 0) return "";
        Deque<Entry> deque = byPlayer.get(key(playerName));
        if (deque == null) return "";

        List<Entry> snapshot;
        synchronized (deque) {
            snapshot = new ArrayList<>(deque);
        }
        int start = Math.max(0, snapshot.size() - maxLines);
        StringBuilder out = new StringBuilder();
        for (int i = start; i < snapshot.size(); i++) {
            Entry entry = snapshot.get(i);
            if (out.length() > 0) out.append('\n');
            out.append(Instant.ofEpochMilli(entry.timestamp)).append(" | ").append(entry.message);
        }
        return out.toString();
    }

    private static String key(String playerName) {
        return playerName.toLowerCase(Locale.ROOT);
    }

    private record Entry(long timestamp, String message) {}
}
