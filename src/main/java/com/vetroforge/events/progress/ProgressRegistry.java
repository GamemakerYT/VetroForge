// src/main/java/com/vetroforge/events/progress/ProgressRegistry.java
package com.vetroforge.events.progress;

import java.util.HashMap;
import java.util.Map;

public final class ProgressRegistry {
    private final Map<String, EventProgressSource> map = new HashMap<>();

    public ProgressRegistry() {
        // Fortschritt wird für die meisten Server-Aktionen direkt über EventManager.addProgressIfActive
        // gemeldet. Eine Dummy-Quelle würde den manuellen Fortschritt im Tick wieder überschreiben.
    }

    public void register(String key, EventProgressSource src) {
        map.put(key.toLowerCase(), src);
    }

    public EventProgressSource get(String key) {
        return map.get(key == null ? null : key.toLowerCase());
    }
}
