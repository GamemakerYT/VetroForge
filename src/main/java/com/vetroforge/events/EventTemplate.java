// src/main/java/com/vetroforge/events/EventTemplate.java
package com.vetroforge.events;

import java.time.Duration;
import java.util.List;

public final class EventTemplate {
    public final String id;
    public final String name;
    public final String description;
    public final String category;
    public final String icon;
    public final long goal;
    public final Duration duration; // optional (null → kein Auto-Ende)
    public final String progressSource; // z.B. "cactus_sold"
    public final List<String> rewards; // einfache String-Liste (Befehle, Keys etc.)

    public EventTemplate(String id, String name, long goal, Duration duration,
                         String progressSource, List<String> rewards) {
        this(id, name, "", "Community", "PAPER", goal, duration, progressSource, rewards);
    }

    public EventTemplate(String id, String name, String description, String category, String icon,
                         long goal, Duration duration, String progressSource, List<String> rewards) {
        this.id = id;
        this.name = name;
        this.description = description == null ? "" : description;
        this.category = category == null || category.isBlank() ? "Community" : category;
        this.icon = icon == null || icon.isBlank() ? "PAPER" : icon;
        this.goal = goal;
        this.duration = duration;
        this.progressSource = progressSource;
        this.rewards = rewards;
    }
}
