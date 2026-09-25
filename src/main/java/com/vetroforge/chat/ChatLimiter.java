// src/main/java/com/vetroforge/chat/ChatLimiter.java
package com.vetroforge.chat;

import org.bukkit.entity.Player;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class ChatLimiter {
    private volatile int slowSeconds = 0;
    private final Map<UUID, Long> nextAllowed = new ConcurrentHashMap<>();

    public int getSlowSeconds() { return slowSeconds; }
    public void setSlowSeconds(int secs) { this.slowSeconds = Math.max(0, secs); }

    public boolean canSend(Player p) {
        int slow = slowSeconds;
        if (slow <= 0) return true;
        long now = System.currentTimeMillis();
        long allowedAt = nextAllowed.getOrDefault(p.getUniqueId(), 0L);
        return now >= allowedAt;
    }

    public long getRemainingMs(Player p) {
        long now = System.currentTimeMillis();
        long allowedAt = nextAllowed.getOrDefault(p.getUniqueId(), 0L);
        return Math.max(0L, allowedAt - now);
    }

    public void recordSend(Player p) {
        int slow = slowSeconds;
        if (slow <= 0) { nextAllowed.remove(p.getUniqueId()); return; }
        long next = System.currentTimeMillis() + slow * 1000L;
        nextAllowed.put(p.getUniqueId(), next);
    }

    public void clear(UUID id) { nextAllowed.remove(id); }
}